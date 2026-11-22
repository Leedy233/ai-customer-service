package com.example.aicustomerservice.service;

import com.example.aicustomerservice.entity.ChatMessageEntity;
import com.example.aicustomerservice.entity.ConversationMeta;
import com.example.aicustomerservice.repository.ChatMessageRepository;
import com.example.aicustomerservice.repository.ConversationMetaRepository;
import com.example.aicustomerservice.repository.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 加法：会话摘要与真删除。
 * 摘要在结束会话（END 工单）时生成：首条用户问题 → 末条助手答复首句（确定性提取，不额外耗模型）。
 * 删除 = 墓碑 + 清消息 + 退排队；审计日志与工单保留，列表/历史靠墓碑屏蔽。
 */
@Service
@RequiredArgsConstructor
public class ConversationMetaService {

    private final ConversationMetaRepository metaRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RequestLogRepository requestLogRepository;
    private final QueueService queueService;
    private final org.springframework.ai.chat.memory.ChatMemory chatMemory;

    public Optional<ConversationMeta> find(String conversationId) {
        return metaRepository.findByConversationId(conversationId);
    }

    public boolean isDeleted(String conversationId) {
        return metaRepository.findByConversationId(conversationId)
                .map(m -> Boolean.TRUE.equals(m.getDeleted()))
                .orElse(false);
    }

    public String summaryFor(String conversationId) {
        return metaRepository.findByConversationId(conversationId)
                .map(ConversationMeta::getSummary)
                .orElse(null);
    }

    private ConversationMeta upsert(Long userId, String conversationId) {
        ConversationMeta meta = metaRepository.findByConversationId(conversationId).orElseGet(() -> {
            ConversationMeta m = new ConversationMeta();
            m.setConversationId(conversationId);
            m.setUserId(userId);
            return m;
        });
        meta.setUpdatedAt(LocalDateTime.now());
        return meta;
    }

    @Transactional
    public synchronized void summarizeOnEnd(Long userId, String conversationId) {
        String firstUser = chatMessageRepository
                .findFirstByConversationIdAndRoleOrderByIdAsc(conversationId, "user")
                .map(ChatMessageEntity::getContent).orElse("");
        String lastAssistant = chatMessageRepository
                .findFirstByConversationIdAndRoleOrderByIdDesc(conversationId, "assistant")
                .map(ChatMessageEntity::getContent).orElse("");
        String head = firstUser.length() > 40 ? firstUser.substring(0, 40) : firstUser;
        String tail = lastAssistant.split("[。！？\\n]")[0];
        if (tail.length() > 60) tail = tail.substring(0, 60);
        String summary = tail.isBlank() ? head : head + " → " + tail;
        if (summary.length() > 200) summary = summary.substring(0, 200);
        ConversationMeta meta = upsert(userId, conversationId);
        meta.setSummary(summary);
        metaRepository.save(meta);
    }

    /** 返回 false 表示无所有权 */
    @Transactional
    public synchronized boolean delete(Long userId, String conversationId) {
        boolean owner = requestLogRepository.findFirstByConversationIdOrderByIdAsc(conversationId)
                .map(r -> r.getUserId().equals(userId))
                .orElse(false);
        if (!owner) {
            return false;
        }
        ConversationMeta meta = upsert(userId, conversationId);
        meta.setDeleted(true);
        metaRepository.save(meta);
        chatMessageRepository.deleteByConversationId(conversationId);
        // 加法：同步清除模型记忆（JdbcChatMemory 表），避免删除后模型仍"记得"该会话
        chatMemory.clear(userId + "::" + conversationId);
        queueService.cancel(conversationId);
        return true;
    }
}