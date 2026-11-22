package com.example.aicustomerservice.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端统计：全部聚合自现有 MySQL 表，不引入新数据源。
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final EntityManager em;

    public Map<String, Object> overview() {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("todayConversations", single("select count(distinct conversation_id) from ai_chat_message where date(created_at)=curdate()"));
        vo.put("todayRequests", single("select count(*) from ai_request_log where date(created_at)=curdate()"));
        vo.put("todayTokens", single("select coalesce(sum(prompt_tokens+completion_tokens),0) from ai_request_log where date(created_at)=curdate()"));
        vo.put("avgLatencyMs", round1(single("select coalesce(avg(latency_ms),0) from ai_request_log where date(created_at)=curdate()")));
        vo.put("totalOrders", single("select count(*) from t_order"));
        vo.put("totalUsers", single("select count(*) from ai_user"));
        vo.put("pendingRefunds", single("select count(*) from ai_refund where status='PENDING_CONFIRM'"));
        vo.put("refundedCount", single("select count(*) from ai_refund where status='REFUNDED'"));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        List<Object[]> statusRows = em.createNativeQuery("select status, count(*) from t_order group by status").getResultList();
        for (Object[] row : statusRows) {
            byStatus.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        vo.put("ordersByStatus", byStatus);
        return vo;
    }

    public List<Map<String, Object>> trends(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            String d = LocalDate.now().minusDays(days - 1L - i).toString();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", d);
            p.put("requests", 0L);
            p.put("tokens", 0L);
            p.put("avgLatencyMs", 0.0);
            p.put("conversations", 0L);
            byDate.put(d, p);
        }
        List<Object[]> reqRows = em.createNativeQuery(
                        "select date(created_at) d, count(*) c, coalesce(sum(prompt_tokens+completion_tokens),0) t, "
                                + "coalesce(avg(latency_ms),0) l from ai_request_log where created_at >= :start group by d order by d")
                .setParameter("start", start).getResultList();
        for (Object[] row : reqRows) {
            Map<String, Object> p = byDate.get(String.valueOf(row[0]));
            if (p != null) {
                p.put("requests", ((Number) row[1]).longValue());
                p.put("tokens", ((Number) row[2]).longValue());
                p.put("avgLatencyMs", round1((Number) row[3]));
            }
        }
        List<Object[]> convRows = em.createNativeQuery(
                        "select date(created_at) d, count(distinct conversation_id) c from ai_chat_message "
                                + "where created_at >= :start group by d order by d")
                .setParameter("start", start).getResultList();
        for (Object[] row : convRows) {
            Map<String, Object> p = byDate.get(String.valueOf(row[0]));
            if (p != null) {
                p.put("conversations", ((Number) row[1]).longValue());
            }
        }
        return new ArrayList<>(byDate.values());
    }

    private long single(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private double round1(Number n) {
        return BigDecimal.valueOf(n.doubleValue()).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}