package com.example.aicustomerservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 简易限流：每个 IP 每分钟最多 60 次 /ai/** 请求，超出返回 429。
 * 生产环境建议替换为 Redis + 令牌桶（如 Bucket4j / Gateway 限流）。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int LIMIT = 60;
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = request.getRemoteAddr();
        Deque<Long> queue = hits.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        while (!queue.isEmpty() && now - queue.peekFirst() > WINDOW_MS) {
            queue.pollFirst();
        }
        if (queue.size() >= LIMIT) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
            return false;
        }
        queue.addLast(now);
        return true;
    }

    /**
     * 监控快照（加法）：当前窗口内被跟踪 IP 数与命中总数，供系统运行监控页展示。
     */
    public Map<String, Object> snapshot() {
        long now = System.currentTimeMillis();
        long hitCount = 0;
        for (Deque<Long> queue : hits.values()) {
            hitCount += queue.stream().filter(t -> now - t <= WINDOW_MS).count();
        }
        return Map.of("limitPerMinute", LIMIT, "trackedIps", hits.size(), "hitsLastMinute", hitCount);
    }
}