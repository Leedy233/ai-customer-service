package com.example.aicustomerservice.service;

import com.example.aicustomerservice.common.PageVO;
import com.example.aicustomerservice.entity.Order;
import com.example.aicustomerservice.entity.RefundOrder;
import com.example.aicustomerservice.repository.OrderRepository;
import com.example.aicustomerservice.repository.RefundOrderRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端业务管理（加法）：订单 / 退款 / 对话审计 / 请求日志。
 * 审计为 ADMIN 全量视角（与用户侧所有权隔离接口分开），只读不改聊天数据。
 */
@Service
@RequiredArgsConstructor
public class AdminManageService {

    public static final String REFUND_PENDING = "PENDING_CONFIRM";
    public static final String REFUND_REFUNDED = "REFUNDED";
    public static final String REFUND_REJECTED = "REJECTED";
    public static final String REFUND_COMPLETED = "COMPLETED";

    private final EntityManager em;
    private final OrderRepository orderRepository;
    private final RefundOrderRepository refundOrderRepository;

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(jakarta.persistence.Query q) {
        return q.getResultList();
    }

    private Map<Long, String> usernames() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Object[] row : rows(em.createNativeQuery("select id, username from ai_user"))) {
            map.put(((Number) row[0]).longValue(), String.valueOf(row[1]));
        }
        return map;
    }

    // ---------- 订单 ----------

    public PageVO<Map<String, Object>> orders(String orderNo, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1=1");
        if (orderNo != null && !orderNo.isBlank()) {
            where.append(" and order_no like :orderNo");
        }
        if (status != null && !status.isBlank()) {
            where.append(" and status = :status");
        }
        var countQ = em.createNativeQuery("select count(*) from t_order" + where);
        var listQ = em.createNativeQuery(
                "select id, order_no, user_id, product_name, amount, status, create_time from t_order"
                        + where + " order by id desc");
        if (orderNo != null && !orderNo.isBlank()) {
            countQ.setParameter("orderNo", "%" + orderNo + "%");
            listQ.setParameter("orderNo", "%" + orderNo + "%");
        }
        if (status != null && !status.isBlank()) {
            countQ.setParameter("status", status);
            listQ.setParameter("status", status);
        }
        long total = ((Number) countQ.getSingleResult()).longValue();
        listQ.setFirstResult((page - 1) * size).setMaxResults(size);
        Map<Long, String> names = usernames();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : rows(listQ)) {
            list.add(orderVo(row, names));
        }
        return PageVO.of(total, page, size, list);
    }

    private Map<String, Object> orderVo(Object[] row, Map<Long, String> names) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ((Number) row[0]).longValue());
        m.put("orderNo", String.valueOf(row[1]));
        long uid = ((Number) row[2]).longValue();
        m.put("userId", uid);
        m.put("username", names.getOrDefault(uid, String.valueOf(uid)));
        m.put("productName", String.valueOf(row[3]));
        m.put("amount", row[4]);
        m.put("status", String.valueOf(row[5]));
        m.put("createTime", row[6] == null ? null : String.valueOf(row[6]));
        return m;
    }

    public Map<String, Object> orderDetail(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        Map<Long, String> names = usernames();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", order.getOrderNo());
        m.put("userId", order.getUserId());
        m.put("username", names.getOrDefault(order.getUserId(), String.valueOf(order.getUserId())));
        m.put("productName", order.getProductName());
        m.put("amount", order.getAmount());
        m.put("status", order.getStatus());
        m.put("createTime", order.getCreateTime() == null ? null : order.getCreateTime().toString());
        List<Map<String, Object>> refunds = new ArrayList<>();
        for (RefundOrder r : refundOrderRepository.findAll().stream()
                .filter(r -> r.getOrderNo().equals(orderNo)).toList()) {
            refunds.add(refundVo(r, names));
        }
        m.put("refunds", refunds);
        return m;
    }

    @Transactional
    public Order ship(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!"待发货".equals(order.getStatus())) {
            throw new IllegalArgumentException("仅待发货订单可执行发货");
        }
        order.setStatus("已发货");
        // 加法：发货时间 + 预计送达（+2 天）真值写入
        order.setShippedAt(LocalDateTime.now());
        order.setEta(LocalDateTime.now().plusDays(2));
        return orderRepository.save(order);
    }

    // ---------- 退款 ----------

    public PageVO<Map<String, Object>> refunds(String status, String orderNo, int page, int size) {
        List<RefundOrder> all = refundOrderRepository.findAll().stream()
                .filter(r -> status == null || status.isBlank() || r.getStatus().equals(status))
                .filter(r -> orderNo == null || orderNo.isBlank() || r.getOrderNo().contains(orderNo))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .toList();
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        Map<Long, String> names = usernames();
        Map<String, Object> meta = new LinkedHashMap<>();
        for (String s : List.of(REFUND_PENDING, REFUND_REFUNDED, REFUND_COMPLETED, REFUND_REJECTED)) {
            meta.put(s, all.stream().filter(r -> r.getStatus().equals(s)).count());
        }
        return PageVO.of(all.size(), page, size,
                all.subList(from, to).stream().map(r -> refundVo(r, names)).toList(), meta);
    }

    private Map<String, Object> refundVo(RefundOrder r, Map<Long, String> names) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("orderNo", r.getOrderNo());
        m.put("userId", r.getUserId());
        m.put("username", names.getOrDefault(r.getUserId(), String.valueOf(r.getUserId())));
        m.put("conversationId", r.getConversationId());
        m.put("amount", r.getAmount());
        m.put("status", r.getStatus());
        m.put("adminNote", r.getAdminNote());
        m.put("createdAt", r.getCreatedAt().toString());
        m.put("confirmedAt", r.getConfirmedAt() == null ? null : r.getConfirmedAt().toString());
        return m;
    }

    @Transactional
    public RefundOrder reject(Long id, String note) {
        RefundOrder r = refundOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("退款申请不存在"));
        if (!REFUND_PENDING.equals(r.getStatus())) {
            throw new IllegalArgumentException("仅待确认申请可驳回");
        }
        r.setStatus(REFUND_REJECTED);
        r.setAdminNote(note);
        return refundOrderRepository.save(r);
    }

    @Transactional
    public RefundOrder complete(Long id, String note) {
        RefundOrder r = refundOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("退款申请不存在"));
        if (!REFUND_REFUNDED.equals(r.getStatus())) {
            throw new IllegalArgumentException("仅已确认执行的退款可完结");
        }
        r.setStatus(REFUND_COMPLETED);
        r.setAdminNote(note);
        refundOrderRepository.save(r);
        Order order = orderRepository.findByOrderNo(r.getOrderNo())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if ("退款中".equals(order.getStatus())) {
            order.setStatus("已退款");
            orderRepository.save(order);
        }
        return r;
    }

    // ---------- 对话审计 ----------

    public PageVO<Map<String, Object>> auditConversations(String keyword, String username, int page, int size) {
        StringBuilder sql = new StringBuilder(
                "select l.conversation_id cid,"
                        + " (select x.user_id from ai_request_log x where x.conversation_id=l.conversation_id order by x.id limit 1) owner,"
                        + " (select count(*) from ai_chat_message m where m.conversation_id=l.conversation_id) mc,"
                        + " (select count(*) from ai_chat_message m where m.conversation_id=l.conversation_id and m.role='tool') tc,"
                        + " (select m.content from ai_chat_message m where m.conversation_id=l.conversation_id and m.role='user' order by m.id limit 1) preview,"
                        + " max(l.created_at) last"
                        + " from ai_request_log l");
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" where exists (select 1 from ai_chat_message m where m.conversation_id=l.conversation_id and m.content like :kw)");
        }
        sql.append(" group by l.conversation_id order by last desc");
        var q = em.createNativeQuery(sql.toString());
        if (keyword != null && !keyword.isBlank()) {
            q.setParameter("kw", "%" + keyword + "%");
        }
        Map<Long, String> names = usernames();
        List<Map<String, Object>> all = new ArrayList<>();
        for (Object[] row : rows(q)) {
            long uid = ((Number) row[1]).longValue();
            String uname = names.getOrDefault(uid, String.valueOf(uid));
            if (username != null && !username.isBlank() && !uname.contains(username)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("conversationId", String.valueOf(row[0]));
            m.put("userId", uid);
            m.put("username", uname);
            m.put("messageCount", ((Number) row[2]).longValue());
            m.put("toolCount", ((Number) row[3]).longValue());
            m.put("preview", row[4] == null ? "" : String.valueOf(row[4]));
            m.put("lastActiveAt", String.valueOf(row[5]));
            all.add(m);
        }
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageVO.of(all.size(), page, size, all.subList(from, to));
    }

    public List<Map<String, Object>> auditMessages(String conversationId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : rows(em.createNativeQuery(
                        "select id, role, content, tool_name, created_at from ai_chat_message"
                                + " where conversation_id = :cid order by id")
                .setParameter("cid", conversationId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) row[0]).longValue());
            m.put("role", String.valueOf(row[1]));
            m.put("content", String.valueOf(row[2]));
            m.put("toolName", row[3] == null ? null : String.valueOf(row[3]));
            m.put("createdAt", String.valueOf(row[4]));
            out.add(m);
        }
        return out;
    }

    // ---------- 请求日志 ----------

    public PageVO<Map<String, Object>> logs(Long userId, String model, boolean onlyError, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1=1");
        if (userId != null) {
            where.append(" and l.user_id = :userId");
        }
        if (model != null && !model.isBlank()) {
            where.append(" and l.model = :model");
        }
        if (onlyError) {
            where.append(" and l.exception is not null");
        }
        var countQ = em.createNativeQuery("select count(*) from ai_request_log l" + where);
        var listQ = em.createNativeQuery(
                "select l.id, l.conversation_id, l.user_id, l.model, l.prompt_tokens, l.completion_tokens,"
                        + " l.latency_ms, l.exception, l.created_at, u.username"
                        + " from ai_request_log l left join ai_user u on u.id = l.user_id"
                        + where + " order by l.id desc");
        if (userId != null) {
            countQ.setParameter("userId", userId);
            listQ.setParameter("userId", userId);
        }
        if (model != null && !model.isBlank()) {
            countQ.setParameter("model", model);
            listQ.setParameter("model", model);
        }
        long total = ((Number) countQ.getSingleResult()).longValue();
        listQ.setFirstResult((page - 1) * size).setMaxResults(size);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : rows(listQ)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) row[0]).longValue());
            m.put("conversationId", String.valueOf(row[1]));
            m.put("userId", ((Number) row[2]).longValue());
            m.put("model", row[3] == null ? null : String.valueOf(row[3]));
            m.put("promptTokens", row[4] == null ? 0 : ((Number) row[4]).longValue());
            m.put("completionTokens", row[5] == null ? 0 : ((Number) row[5]).longValue());
            m.put("latencyMs", ((Number) row[6]).longValue());
            m.put("exception", row[7] == null ? null : String.valueOf(row[7]));
            m.put("createdAt", String.valueOf(row[8]));
            m.put("username", row[9] == null ? String.valueOf(row[2]) : String.valueOf(row[9]));
            list.add(m);
        }
        return PageVO.of(total, page, size, list);
    }
}