package com.example.aicustomerservice.service;

import com.example.aicustomerservice.config.RateLimitInterceptor;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端聚合指标（加法）：Token 消耗 / 响应耗时分布 / 系统运行监控。
 * 百分位在小数据量下于 Java 侧计算（MySQL 8 无原生 percentile 聚合）。
 */
@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private final EntityManager em;
    private final KnowledgeService knowledgeService;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final HealthEndpoint healthEndpoint;

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(jakarta.persistence.Query q) {
        return q.getResultList();
    }

    public Map<String, Object> tokens(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        Map<String, Map<String, Object>> byDay = emptyDays(days);
        for (Object[] row : rows(em.createNativeQuery(
                        "select date(created_at) d, coalesce(sum(prompt_tokens),0) p, coalesce(sum(completion_tokens),0) c, count(*) n"
                                + " from ai_request_log where created_at >= :start group by d order by d")
                .setParameter("start", start))) {
            Map<String, Object> p = byDay.get(String.valueOf(row[0]));
            if (p != null) {
                p.put("promptTokens", ((Number) row[1]).longValue());
                p.put("completionTokens", ((Number) row[2]).longValue());
                p.put("requests", ((Number) row[3]).longValue());
            }
        }
        List<Map<String, Object>> byModel = new ArrayList<>();
        for (Object[] row : rows(em.createNativeQuery(
                        "select coalesce(model,'unknown') m, count(*) n, coalesce(sum(prompt_tokens+completion_tokens),0) t"
                                + " from ai_request_log where created_at >= :start group by m order by t desc")
                .setParameter("start", start))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("model", String.valueOf(row[0]));
            m.put("requests", ((Number) row[1]).longValue());
            m.put("tokens", ((Number) row[2]).longValue());
            byModel.add(m);
        }
        Map<Long, String> names = usernames();
        List<Map<String, Object>> byUser = new ArrayList<>();
        for (Object[] row : rows(em.createNativeQuery(
                        "select user_id u, coalesce(sum(prompt_tokens+completion_tokens),0) t, count(*) n"
                                + " from ai_request_log where created_at >= :start group by u order by t desc limit 10")
                .setParameter("start", start))) {
            long uid = ((Number) row[0]).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", uid);
            m.put("username", names.getOrDefault(uid, String.valueOf(uid)));
            m.put("tokens", ((Number) row[1]).longValue());
            m.put("requests", ((Number) row[2]).longValue());
            byUser.add(m);
        }
        long prompt = byDay.values().stream().mapToLong(p -> (Long) p.get("promptTokens")).sum();
        long completion = byDay.values().stream().mapToLong(p -> (Long) p.get("completionTokens")).sum();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("totalPromptTokens", prompt);
        out.put("totalCompletionTokens", completion);
        out.put("totalTokens", prompt + completion);
        out.put("byDay", new ArrayList<>(byDay.values()));
        out.put("byModel", byModel);
        out.put("byUser", byUser);
        return out;
    }

    public Map<String, Object> latency(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<Object[]> logRows = rows(em.createNativeQuery(
                        "select id, conversation_id, user_id, coalesce(model,'unknown'), latency_ms, created_at"
                                + " from ai_request_log where created_at >= :start order by id")
                .setParameter("start", start));
        Map<String, List<Long>> dayValues = new LinkedHashMap<>();
        Map<String, List<Long>> modelValues = new LinkedHashMap<>();
        List<Long> all = new ArrayList<>();
        List<Map<String, Object>> slowest = new ArrayList<>();
        Map<Long, String> names = usernames();
        for (Object[] row : logRows) {
            long ms = ((Number) row[4]).longValue();
            String day = String.valueOf(row[5]).substring(0, 10);
            dayValues.computeIfAbsent(day, k -> new ArrayList<>()).add(ms);
            modelValues.computeIfAbsent(String.valueOf(row[3]), k -> new ArrayList<>()).add(ms);
            all.add(ms);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) row[0]).longValue());
            m.put("conversationId", String.valueOf(row[1]));
            long uid = ((Number) row[2]).longValue();
            m.put("username", names.getOrDefault(uid, String.valueOf(uid)));
            m.put("model", String.valueOf(row[3]));
            m.put("latencyMs", ms);
            m.put("createdAt", String.valueOf(row[5]));
            slowest.add(m);
        }
        slowest.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("latencyMs")).reversed());
        Map<String, Map<String, Object>> byDay = emptyDays(days);
        for (Map.Entry<String, List<Long>> e : dayValues.entrySet()) {
            Map<String, Object> p = byDay.get(e.getKey());
            if (p != null) {
                fillStats(p, e.getValue());
            }
        }
        List<Map<String, Object>> byModel = new ArrayList<>();
        for (Map.Entry<String, List<Long>> e : modelValues.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("model", e.getKey());
            fillStats(m, e.getValue());
            byModel.add(m);
        }
        byModel.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("requests")).reversed());
        Map<String, Object> overall = new LinkedHashMap<>();
        fillStats(overall, all);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("overall", overall);
        out.put("byDay", new ArrayList<>(byDay.values()));
        out.put("byModel", byModel);
        out.put("slowest", slowest.subList(0, Math.min(10, slowest.size())));
        return out;
    }

    private void fillStats(Map<String, Object> target, List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        long sum = sorted.stream().mapToLong(Long::longValue).sum();
        target.put("requests", (long) sorted.size());
        target.put("avgMs", sorted.isEmpty() ? 0.0 : round1(sum / (double) sorted.size()));
        target.put("p50Ms", percentile(sorted, 50));
        target.put("p95Ms", percentile(sorted, 95));
        target.put("maxMs", sorted.isEmpty() ? 0L : sorted.get(sorted.size() - 1));
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    public Map<String, Object> system() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        var memory = ManagementFactory.getMemoryMXBean();
        var threads = ManagementFactory.getThreadMXBean();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMb", memory.getHeapMemoryUsage().getUsed() / 1024 / 1024);
        jvm.put("heapMaxMb", memory.getHeapMemoryUsage().getMax() / 1024 / 1024);
        jvm.put("nonHeapUsedMb", memory.getNonHeapMemoryUsage().getUsed() / 1024 / 1024);
        jvm.put("threads", threads.getThreadCount());
        jvm.put("uptimeMs", runtime.getUptime());
        jvm.put("startedAt", java.time.Instant.ofEpochMilli(runtime.getStartTime()).toString());
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("pid", ProcessHandle.current().pid());

        Map<String, Object> db = new LinkedHashMap<>();
        long t0 = System.nanoTime();
        String dbStatus = "UP";
        try {
            em.createNativeQuery("select 1").getSingleResult();
        } catch (Exception e) {
            dbStatus = "DOWN";
        }
        db.put("status", dbStatus);
        db.put("pingMs", round1((System.nanoTime() - t0) / 1_000_000.0));
        Map<String, Long> tables = new LinkedHashMap<>();
        for (String table : List.of("ai_user", "t_order", "ai_refund", "ai_ticket",
                "ai_chat_message", "ai_request_log", "ai_knowledge_doc")) {
            tables.put(table, ((Number) em.createNativeQuery("select count(*) from " + table).getSingleResult()).longValue());
        }
        db.put("tables", tables);

        Map<String, Object> rag = new LinkedHashMap<>();
        rag.put("docs", knowledgeService.list().size());
        rag.put("enabledDocs", knowledgeService.enabledCount());
        rag.put("chunks", knowledgeService.totalChunks());

        Map<String, Object> health = new LinkedHashMap<>();
        HealthDescriptor component = healthEndpoint.health();
        health.put("status", component.getStatus().getCode());
        Map<String, String> components = new LinkedHashMap<>();
        if (component instanceof CompositeHealthDescriptor composite) {
            composite.getComponents().forEach((k, v) -> components.put(k, v.getStatus().getCode()));
        }
        health.put("components", components);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", dbStatus.equals("UP") ? "UP" : "DEGRADED");
        out.put("jvm", jvm);
        out.put("db", db);
        out.put("rag", rag);
        out.put("rateLimit", rateLimitInterceptor.snapshot());
        out.put("health", health);
        out.put("redisNote", "redis 容器在跑但应用未引入客户端依赖，当前未接入（限流为进程内实现）");
        out.put("checkedAt", LocalDateTime.now().toString());
        return out;
    }

    private Map<String, Map<String, Object>> emptyDays(int days) {
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            String d = LocalDate.now().minusDays(days - 1L - i).toString();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", d);
            p.put("promptTokens", 0L);
            p.put("completionTokens", 0L);
            p.put("requests", 0L);
            p.put("avgMs", 0.0);
            p.put("p50Ms", 0L);
            p.put("p95Ms", 0L);
            p.put("maxMs", 0L);
            byDate.put(d, p);
        }
        return byDate;
    }

    private Map<Long, String> usernames() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Object[] row : rows(em.createNativeQuery("select id, username from ai_user"))) {
            map.put(((Number) row[0]).longValue(), String.valueOf(row[1]));
        }
        return map;
    }

    private double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}