package com.example.aicustomerservice.service;

import com.example.aicustomerservice.common.PageVO;
import com.example.aicustomerservice.entity.Ticket;
import com.example.aicustomerservice.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单服务（加法）：用户侧创建/取消/评价/本人列表；管理侧列表/详情/处理/关闭。
 * 工单号规则 GD + yyyyMMdd + 3 位当日序号（与前端原 mock 格式一致）。
 * 不写 ai_chat_message，避免污染 ChatMemory。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    public static final String OPEN = "OPEN";
    public static final String PROCESSING = "PROCESSING";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";

    private final TicketRepository ticketRepository;
    private final com.example.aicustomerservice.repository.UserRepository userRepository;
    private final ConversationMetaService conversationMetaService;
    private final QueueService queueService;

    private Map<Long, String> names() {
        Map<Long, String> map = new LinkedHashMap<>();
        userRepository.findAll().forEach(u -> map.put(u.getId(), u.getUsername()));
        return map;
    }

    @Transactional
    public synchronized Ticket create(Long userId, String conversationId, String kind,
                                      String category, String summary, Boolean resolved) {
        if (!"TRANSFER".equals(kind) && !"END".equals(kind)) {
            throw new IllegalArgumentException("kind 仅支持 TRANSFER 或 END");
        }
        Ticket t = new Ticket();
        t.setTicketNo(nextTicketNo());
        t.setUserId(userId);
        t.setConversationId(conversationId);
        t.setKind(kind);
        t.setCategory(category);
        t.setSummary(summary);
        t.setResolved(resolved);
        t.setStatus("TRANSFER".equals(kind) ? OPEN : CLOSED);
        t.setCreatedAt(LocalDateTime.now());
        if (CLOSED.equals(t.getStatus())) {
            t.setClosedAt(LocalDateTime.now());
        }
        ticketRepository.save(t);
        // 加法：结束会话生成服务端摘要
        if ("END".equals(kind)) {
            conversationMetaService.summarizeOnEnd(userId, conversationId);
        }
        log.info("【工单】创建 {} kind={} conv={}", t.getTicketNo(), kind, conversationId);
        return t;
    }

    private String nextTicketNo() {
        String prefix = "GD" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = ticketRepository.findTopByTicketNoStartingWithOrderByTicketNoDesc(prefix)
                .map(t -> Integer.parseInt(t.getTicketNo().substring(prefix.length())) + 1)
                .orElse(1);
        return prefix + String.format("%03d", seq);
    }

    @Transactional
    public Ticket cancel(Long userId, Long id) {
        Ticket t = ticketRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        if (!t.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该工单");
        }
        if (!OPEN.equals(t.getStatus())) {
            throw new IllegalArgumentException("仅待处理工单可取消");
        }
        t.setStatus(CANCELLED);
        t.setClosedAt(LocalDateTime.now());
        // 加法：取消转人工工单同步退排队
        if ("TRANSFER".equals(t.getKind())) {
            queueService.cancel(t.getConversationId());
        }
        return ticketRepository.save(t);
    }

    @Transactional
    public Ticket rate(Long userId, Long id, int rating, String feedback, Boolean resolved) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分需为 1-5");
        }
        Ticket t = ticketRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        if (!t.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该工单");
        }
        t.setRating(rating);
        t.setFeedback(feedback);
        if (resolved != null) {
            t.setResolved(resolved);
        }
        return ticketRepository.save(t);
    }

    @Transactional
    public Ticket resolve(Long userId, Long id, boolean resolved) {
        Ticket t = ticketRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        if (!t.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该工单");
        }
        t.setResolved(resolved);
        return ticketRepository.save(t);
    }

    public List<Map<String, Object>> mine(Long userId) {
        Map<Long, String> names = names();
        return ticketRepository.findByUserIdOrderByIdDesc(userId).stream().map(t -> vo(t, names)).toList();
    }

    public PageVO<Map<String, Object>> adminList(String status, String kind, int page, int size) {
        List<Ticket> all = ticketRepository.findAll().stream()
                .filter(t -> status == null || status.isBlank() || t.getStatus().equals(status))
                .filter(t -> kind == null || kind.isBlank() || t.getKind().equals(kind))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .toList();
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        Map<String, Object> meta = new LinkedHashMap<>();
        for (String s : List.of(OPEN, PROCESSING, CLOSED, CANCELLED)) {
            meta.put(s, all.stream().filter(t -> t.getStatus().equals(s)).count());
        }
        Map<Long, String> names = names();
        return PageVO.of(all.size(), page, size, all.subList(from, to).stream().map(t -> vo(t, names)).toList(), meta);
    }

    public Map<String, Object> adminDetail(Long id) {
        Ticket t = ticketRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        return vo(t, names());
    }

    @Transactional
    public Ticket process(Long id, String assignee) {
        Ticket t = ticketRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        if (!OPEN.equals(t.getStatus())) {
            throw new IllegalArgumentException("仅待处理工单可接单");
        }
        t.setStatus(PROCESSING);
        t.setAssignee(assignee);
        return ticketRepository.save(t);
    }

    @Transactional
    public Ticket close(Long id, String note, String assignee) {
        Ticket t = ticketRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        if (!OPEN.equals(t.getStatus()) && !PROCESSING.equals(t.getStatus())) {
            throw new IllegalArgumentException("当前状态不可关闭");
        }
        t.setStatus(CLOSED);
        t.setCloseNote(note);
        t.setAssignee(assignee);
        t.setClosedAt(LocalDateTime.now());
        // 加法：管理端关闭转人工工单 = 叫号完成
        if ("TRANSFER".equals(t.getKind())) {
            queueService.markServed(t.getConversationId());
        }
        return ticketRepository.save(t);
    }

    public Map<String, Object> vo(Ticket t) {
        return vo(t, names());
    }

    public Map<String, Object> vo(Ticket t, Map<Long, String> names) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("ticketNo", t.getTicketNo());
        m.put("userId", t.getUserId());
        m.put("username", names.getOrDefault(t.getUserId(), String.valueOf(t.getUserId())));
        m.put("conversationId", t.getConversationId());
        m.put("kind", t.getKind());
        m.put("category", t.getCategory());
        m.put("summary", t.getSummary());
        m.put("status", t.getStatus());
        m.put("rating", t.getRating());
        m.put("feedback", t.getFeedback());
        m.put("resolved", t.getResolved());
        m.put("assignee", t.getAssignee());
        m.put("closeNote", t.getCloseNote());
        m.put("createdAt", t.getCreatedAt().toString());
        m.put("closedAt", t.getClosedAt() == null ? null : t.getClosedAt().toString());
        return m;
    }
}