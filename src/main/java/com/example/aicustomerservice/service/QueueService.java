package com.example.aicustomerservice.service;

import com.example.aicustomerservice.entity.QueueEntry;
import com.example.aicustomerservice.repository.QueueEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加法：转人工排队真值服务（替代前端演示位置）。
 * 位置 = 前方 WAITING 数 + 1；预计等待 = 位置 × 单人次服务时长（3 分钟）。
 * 管理端关闭 TRANSFER 工单 → SERVED；用户取消/结束会话 → CANCELLED。
 */
@Service
@RequiredArgsConstructor
public class QueueService {

    public static final String WAITING = "WAITING";
    public static final String CANCELLED = "CANCELLED";
    public static final String SERVED = "SERVED";
    /** 单人次平均服务时长（秒），用于预计等待推导 */
    public static final long SERVICE_SECONDS = 180;

    private final QueueEntryRepository repository;

    @Transactional
    public synchronized Map<String, Object> join(Long userId, String conversationId) {
        QueueEntry existing = repository
                .findFirstByConversationIdAndStatusOrderByIdDesc(conversationId, WAITING)
                .orElse(null);
        if (existing != null) {
            return status(conversationId);
        }
        QueueEntry e = new QueueEntry();
        e.setConversationId(conversationId);
        e.setUserId(userId);
        e.setStatus(WAITING);
        e.setJoinedAt(LocalDateTime.now());
        repository.save(e);
        return status(conversationId);
    }

    public Map<String, Object> status(String conversationId) {
        QueueEntry e = repository
                .findFirstByConversationIdAndStatusOrderByIdDesc(conversationId, WAITING)
                .orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        if (e == null) {
            m.put("active", false);
            m.put("position", 0);
            m.put("estimatedWaitSeconds", 0);
            return m;
        }
        long ahead = repository.findByStatusOrderByJoinedAtAscIdAsc(WAITING).stream()
                .filter(w -> w.getJoinedAt().isBefore(e.getJoinedAt())
                        || (w.getJoinedAt().equals(e.getJoinedAt()) && w.getId() < e.getId()))
                .count();
        long position = ahead + 1;
        m.put("active", true);
        m.put("position", position);
        m.put("estimatedWaitSeconds", position * SERVICE_SECONDS);
        m.put("joinedAt", e.getJoinedAt().toString());
        return m;
    }

    @Transactional
    public synchronized Map<String, Object> cancel(String conversationId) {
        repository.findFirstByConversationIdAndStatusOrderByIdDesc(conversationId, WAITING)
                .ifPresent(e -> {
                    e.setStatus(CANCELLED);
                    e.setLeftAt(LocalDateTime.now());
                    repository.save(e);
                });
        return status(conversationId);
    }

    /** 管理端关闭 TRANSFER 工单时叫号完成 */
    @Transactional
    public synchronized void markServed(String conversationId) {
        repository.findFirstByConversationIdAndStatusOrderByIdDesc(conversationId, WAITING)
                .ifPresent(e -> {
                    e.setStatus(SERVED);
                    e.setLeftAt(LocalDateTime.now());
                    repository.save(e);
                });
    }

    /** 当前等待队列长度（监控用） */
    public long waitingCount() {
        return repository.findByStatusOrderByJoinedAtAscIdAsc(WAITING).size();
    }

    public List<Map<String, Object>> waitingList() {
        return repository.findByStatusOrderByJoinedAtAscIdAsc(WAITING).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("conversationId", e.getConversationId());
            m.put("userId", e.getUserId());
            m.put("joinedAt", e.getJoinedAt().toString());
            return m;
        }).toList();
    }
}