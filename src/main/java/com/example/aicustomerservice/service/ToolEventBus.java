package com.example.aicustomerservice.service;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加法：SSE v2 工具事件总线。流请求注册 per-conversation sink，
 * @Tool 方法执行完落库后 publish 真入参/真耗时事件，Controller 合并进 SSE 流。
 * 流结束（complete/error/cancel）必须 unregister，避免泄漏。
 */
@Component
public class ToolEventBus {

    private final Map<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    public void register(String conversationId) {
        sinks.put(conversationId, Sinks.many().unicast().onBackpressureBuffer());
    }

    public void unregister(String conversationId) {
        Sinks.Many<String> sink = sinks.remove(conversationId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    /** payload 为 JSON 字符串；无注册流（同步 chat 路径）时静默丢弃 */
    public void publish(String conversationId, String payload) {
        Sinks.Many<String> sink = sinks.get(conversationId);
        if (sink != null) {
            sink.tryEmitNext(payload);
        }
    }

    public Flux<String> flux(String conversationId) {
        Sinks.Many<String> sink = sinks.get(conversationId);
        return sink == null ? Flux.empty() : sink.asFlux();
    }
}