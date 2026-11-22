package com.example.aicustomerservice.service;

import com.example.aicustomerservice.entity.KnowledgeDoc;
import com.example.aicustomerservice.repository.KnowledgeDocRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 知识库服务（加法）：知识源由 classpath 迁移到 DB（首启种子内容不变），
 * 管理端 CRUD / 启停实时同步 Chroma 向量库（按 docId 删旧 chunk 再加新 chunk）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService implements CommandLineRunner {

    private final KnowledgeDocRepository repository;
    private final VectorStore vectorStore;

    @Value("classpath:knowledge/faq.md")
    private Resource faq;
    @Value("classpath:knowledge/shipping.md")
    private Resource shipping;
    @Value("classpath:knowledge/refund.md")
    private Resource refund;

    private final Map<Long, List<String>> chunkIds = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> chunkTexts = new ConcurrentHashMap<>();
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() == 0) {
            for (Resource resource : List.of(faq, shipping, refund)) {
                TextReader reader = new TextReader(resource);
                List<Document> docs = reader.get();
                String title = resource.getFilename() == null ? "knowledge" : resource.getFilename();
                KnowledgeDoc doc = new KnowledgeDoc();
                doc.setTitle(title);
                doc.setContent(docs.isEmpty() ? "" : docs.get(0).getText());
                doc.setSource("CLASSPATH");
                doc.setEnabled(true);
                doc.setCreatedAt(LocalDateTime.now());
                doc.setUpdatedAt(LocalDateTime.now());
                repository.save(doc);
            }
            log.info("【知识库】classpath 种子迁移入库 {} 篇", repository.count());
        }
        for (KnowledgeDoc doc : repository.findAllByOrderByIdAsc()) {
            sync(doc);
        }
        log.info("【知识库】向量同步完成 docs={} chunks={}", repository.count(), totalChunks());
    }

    /** 删除旧 chunk 后按当前内容/启停状态重建 */
    public synchronized void sync(KnowledgeDoc doc) {
        // 加法：换成持久化向量库（Chroma）后，进程重启会让内存里的 chunkIds 丢失，
        // 只靠下面的 id 列表清理不足，会每次启动重复写入同一篇知识。
        // 这里按元数据 docId 先清空该文档的所有向量，保证重复启动/缩容都不留脏数据。
        vectorStore.delete("docId == '" + doc.getId() + "'");
        List<String> old = chunkIds.remove(doc.getId());
        if (old != null && !old.isEmpty()) {
            vectorStore.delete(old);
        }
        chunkTexts.remove(doc.getId());
        if (!Boolean.TRUE.equals(doc.getEnabled()) || doc.getContent() == null || doc.getContent().isBlank()) {
            return;
        }
        Document source = Document.builder()
                .text(doc.getContent())
                .metadata("title", doc.getTitle())
                .metadata("docId", String.valueOf(doc.getId()))
                .build();
        List<Document> chunks = splitter.apply(List.of(source));
        List<String> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<Document> toAdd = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String id = UUID.randomUUID().toString();
            ids.add(id);
            texts.add(chunk.getText());
            // 加法：chunk 序号入 metadata，供真溯源 section 字段
            toAdd.add(Document.builder().id(id).text(chunk.getText())
                    .metadata(chunk.getMetadata())
                    .metadata("chunkIndex", String.valueOf(i + 1))
                    .build());
        }
        if (!toAdd.isEmpty()) {
            vectorStore.add(toAdd);
        }
        chunkIds.put(doc.getId(), ids);
        chunkTexts.put(doc.getId(), texts);
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeDoc doc : repository.findAllByOrderByIdAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", doc.getId());
            m.put("title", doc.getTitle());
            m.put("source", doc.getSource());
            m.put("enabled", doc.getEnabled());
            m.put("chunkCount", chunkIds.getOrDefault(doc.getId(), List.of()).size());
            m.put("contentLength", doc.getContent() == null ? 0 : doc.getContent().length());
            m.put("createdAt", doc.getCreatedAt().toString());
            m.put("updatedAt", doc.getUpdatedAt().toString());
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> detail(Long id) {
        KnowledgeDoc doc = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("知识文档不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getId());
        m.put("title", doc.getTitle());
        m.put("content", doc.getContent());
        m.put("source", doc.getSource());
        m.put("enabled", doc.getEnabled());
        m.put("chunks", chunkTexts.getOrDefault(doc.getId(), List.of()));
        m.put("updatedAt", doc.getUpdatedAt().toString());
        return m;
    }

    @Transactional
    public synchronized KnowledgeDoc create(String title, String content) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setTitle(title);
        doc.setContent(content);
        doc.setSource("ADMIN");
        doc.setEnabled(true);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        repository.save(doc);
        sync(doc);
        return doc;
    }

    @Transactional
    public synchronized KnowledgeDoc update(Long id, String title, String content) {
        KnowledgeDoc doc = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("知识文档不存在"));
        doc.setTitle(title);
        doc.setContent(content);
        doc.setUpdatedAt(LocalDateTime.now());
        repository.save(doc);
        sync(doc);
        return doc;
    }

    @Transactional
    public synchronized KnowledgeDoc toggle(Long id) {
        KnowledgeDoc doc = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("知识文档不存在"));
        doc.setEnabled(!Boolean.TRUE.equals(doc.getEnabled()));
        doc.setUpdatedAt(LocalDateTime.now());
        repository.save(doc);
        sync(doc);
        return doc;
    }

    @Transactional
    public synchronized void delete(Long id) {
        KnowledgeDoc doc = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("知识文档不存在"));
        // 加法：重启后内存 chunkIds 为空，只按 id 列表清理会在 Chroma 留孤儿向量；先按 docId 清空
        vectorStore.delete("docId == '" + id + "'");
        List<String> old = chunkIds.remove(id);
        if (old != null && !old.isEmpty()) {
            vectorStore.delete(old);
        }
        chunkTexts.remove(id);
        repository.delete(doc);
    }

    public int totalChunks() {
        return chunkIds.values().stream().mapToInt(List::size).sum();
    }

    public long enabledCount() {
        return repository.findAllByOrderByIdAsc().stream().filter(d -> Boolean.TRUE.equals(d.getEnabled())).count();
    }
}