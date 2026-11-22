package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.entity.RequestLog;
import com.example.aicustomerservice.repository.RequestLogRepository;
import com.example.aicustomerservice.security.CurrentUser;
import com.example.aicustomerservice.security.OutputGuard;
import com.example.aicustomerservice.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient chatClient;
    private final ChatMessageService chatMessageService;
    private final RequestLogRepository requestLogRepository;
    private final CurrentUser currentUser;
    private final org.springframework.ai.vectorstore.VectorStore vectorStore;
    private final com.example.aicustomerservice.service.ToolEventBus toolEventBus;

    @GetMapping
    public ApiResponse<String> chat(@RequestParam(defaultValue = "default") String conversationId,
                                    @RequestParam String message) {
        long start = System.nanoTime();
        Long userId = currentUser.requireUserId();
        // P0 修复：记忆键按用户命名空间隔离，禁止跨用户读取他人会话记忆
        String memoryKey = userId + "::" + conversationId;
        chatMessageService.save(conversationId, "user", message, null);
        ChatResponse response = null;
        try {
            response = chatClient.prompt()
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey))
                    .toolContext(Map.of("conversationId", conversationId, "userId", userId))
                    .user(message)
                    .call()
                    .chatResponse();
            String answer = OutputGuard.mask(response.getResult().getOutput().getText());
            chatMessageService.save(conversationId, "assistant", answer, null);
            saveRequestLog(conversationId, userId, response, elapsedMs(start), null);
            return ApiResponse.success(answer);
        } catch (Exception e) {
            saveRequestLog(conversationId, userId, response, elapsedMs(start), e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam(defaultValue = "default") String conversationId,
                               @RequestParam String message) {
        long start = System.nanoTime();
        Long userId = currentUser.requireUserId();
        String memoryKey = userId + "::" + conversationId;
        chatMessageService.save(conversationId, "user", message, null);
        StringBuilder full = new StringBuilder();
        AtomicReference<ChatResponse> lastWithUsage = new AtomicReference<>();
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey))
                .toolContext(Map.of("conversationId", conversationId, "userId", userId))
                .user(message)
                .stream()
                .chatResponse()
                .doOnNext(cr -> {
                    if (cr.getMetadata() != null && cr.getMetadata().getUsage() != null
                            && tokens(cr.getMetadata().getUsage().getCompletionTokens()) > 0) {
                        lastWithUsage.set(cr);
                    }
                })
                .map(cr -> {
                    String text = cr.getResult() != null && cr.getResult().getOutput() != null
                            ? cr.getResult().getOutput().getText() : null;
                    return text == null ? "" : text;
                })
                .doOnNext(full::append)
                .doOnComplete(() -> {
                    chatMessageService.save(conversationId, "assistant", OutputGuard.mask(full.toString()), null);
                    saveRequestLog(conversationId, userId, lastWithUsage.get(), elapsedMs(start), null);
                })
                .doOnError(e -> saveRequestLog(conversationId, userId, lastWithUsage.get(), elapsedMs(start),
                        e.getClass().getSimpleName() + ": " + e.getMessage()))
                // P1 修复：客户端中途断开（cancel）也落审计与已生成部分，不再丢记录
                .doOnCancel(() -> {
                    if (full.length() > 0) {
                        chatMessageService.save(conversationId, "assistant", OutputGuard.mask(full.toString()), null);
                    }
                    saveRequestLog(conversationId, userId, lastWithUsage.get(), elapsedMs(start),
                            "ClientAbort: 客户端中途断开");
                });
    }

    /**
     * 加法：SSE v2 协议（v1 原样保留）。帧类型：
     * sources（流首帧，真检索引用 [{doc,section,excerpt}]）/ delta（文本增量）/
     * tool（真入参+真耗时，工具落库同时推送）/ 结束由流 complete 表达。
     */
    @GetMapping(value = "/stream/v2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<org.springframework.http.codec.ServerSentEvent<String>> streamV2(
            @RequestParam(defaultValue = "default") String conversationId,
            @RequestParam String message) {
        long start = System.nanoTime();
        Long userId = currentUser.requireUserId();
        String memoryKey = userId + "::" + conversationId;
        chatMessageService.save(conversationId, "user", message, null);

        // 真溯源：与 advisor 同参数显式检索，取文档标题/chunk 序号/原文片段
        String sourcesJson;
        try {
            java.util.List<com.example.aicustomerservice.util.JsonUtil.SourceRef> sources = vectorStore.similaritySearch(
                            org.springframework.ai.vectorstore.SearchRequest.builder()
                                    .query(message).topK(3).similarityThreshold(0.3).build())
                    .stream().map(d -> {
                        String text = d.getText() == null ? "" : d.getText();
                        return new com.example.aicustomerservice.util.JsonUtil.SourceRef(
                                String.valueOf(d.getMetadata().getOrDefault("title", "knowledge")),
                                "片段 " + d.getMetadata().getOrDefault("chunkIndex", "1"),
                                text.length() > 160 ? text.substring(0, 160) : text);
                    }).toList();
            sourcesJson = com.example.aicustomerservice.util.JsonUtil.sources(sources);
        } catch (Exception e) {
            sourcesJson = "[]";
        }

        toolEventBus.register(conversationId);
        StringBuilder full = new StringBuilder();
        AtomicReference<ChatResponse> lastWithUsage = new AtomicReference<>();
        Flux<org.springframework.http.codec.ServerSentEvent<String>> text = chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey))
                .toolContext(Map.of("conversationId", conversationId, "userId", userId))
                .user(message)
                .stream()
                .chatResponse()
                .doOnNext(cr -> {
                    if (cr.getMetadata() != null && cr.getMetadata().getUsage() != null
                            && tokens(cr.getMetadata().getUsage().getCompletionTokens()) > 0) {
                        lastWithUsage.set(cr);
                    }
                })
                .map(cr -> {
                    String t = cr.getResult() != null && cr.getResult().getOutput() != null
                            ? cr.getResult().getOutput().getText() : null;
                    return t == null ? "" : t;
                })
                .doOnNext(full::append)
                .filter(t -> !t.isEmpty())
                .map(t -> org.springframework.http.codec.ServerSentEvent.<String>builder()
                        .event("delta").data(t).build())
                // 文本流结束即关闭工具事件 sink，merge 才能 complete
                .doOnComplete(() -> toolEventBus.unregister(conversationId));
        Flux<org.springframework.http.codec.ServerSentEvent<String>> tools = toolEventBus.flux(conversationId)
                .map(json -> org.springframework.http.codec.ServerSentEvent.<String>builder()
                        .event("tool").data(json).build());
        Flux<org.springframework.http.codec.ServerSentEvent<String>> head = Flux.just(
                org.springframework.http.codec.ServerSentEvent.<String>builder()
                        .event("sources").data(sourcesJson).build());
        String finalSources = sourcesJson;
        return Flux.concat(head, Flux.merge(text, tools))
                .doOnComplete(() -> {
                    chatMessageService.saveAssistant(conversationId, OutputGuard.mask(full.toString()), finalSources);
                    saveRequestLog(conversationId, userId, lastWithUsage.get(), elapsedMs(start), null);
                    toolEventBus.unregister(conversationId);
                })
                .doOnError(e -> {
                    saveRequestLog(conversationId, userId, lastWithUsage.get(), elapsedMs(start),
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                    toolEventBus.unregister(conversationId);
                })
                .doOnCancel(() -> {
                    if (full.length() > 0) {
                        chatMessageService.saveAssistant(conversationId, OutputGuard.mask(full.toString()), finalSources);
                    }
                    saveRequestLog(conversationId, userId, lastWithUsage.get(), elapsedMs(start),
                            "ClientAbort: 客户端中途断开");
                    toolEventBus.unregister(conversationId);
                });
    }

    private void saveRequestLog(String conversationId, Long userId, ChatResponse response, long latencyMs, String exception) {
        RequestLog log = new RequestLog();
        log.setConversationId(conversationId);
        log.setUserId(userId);
        log.setModel(response != null && response.getMetadata() != null ? response.getMetadata().getModel() : "unknown");
        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            log.setPromptTokens(tokens(response.getMetadata().getUsage().getPromptTokens()));
            log.setCompletionTokens(tokens(response.getMetadata().getUsage().getCompletionTokens()));
        } else {
            log.setPromptTokens(0L);
            log.setCompletionTokens(0L);
        }
        log.setLatencyMs(latencyMs);
        log.setException(exception);
        log.setCreatedAt(LocalDateTime.now());
        requestLogRepository.save(log);
    }

    private static long tokens(Number n) {
        return n == null ? 0L : n.longValue();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}