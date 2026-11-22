package com.example.aicustomerservice.service;

import com.example.aicustomerservice.entity.ChatMessageEntity;
import com.example.aicustomerservice.entity.Order;
import com.example.aicustomerservice.repository.ChatMessageRepository;
import com.example.aicustomerservice.repository.OrderRepository;
import com.example.aicustomerservice.repository.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户侧读接口服务（验收修复加法）：会话列表/历史消息/本人订单。
 * 所有权一律以 ai_request_log 的 userId 为准，禁止跨用户读取。
 */
@Service
@RequiredArgsConstructor
public class UserContentService {

    private final RequestLogRepository requestLogRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OrderRepository orderRepository;
    private final com.example.aicustomerservice.repository.ConversationMetaRepository conversationMetaRepository;

    public List<Map<String, Object>> conversations(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : requestLogRepository.findConversationSummaries(userId)) {
            String convId = String.valueOf(row[0]);
            // 加法：已删除会话（墓碑）不再出现在列表
            var meta = conversationMetaRepository.findByConversationId(convId);
            if (meta.map(m -> Boolean.TRUE.equals(m.getDeleted())).orElse(false)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", convId);
            m.put("lastActiveAt", row[1] instanceof LocalDateTime t ? t.toString() : String.valueOf(row[1]));
            m.put("title", titleFor(userId, convId));
            m.put("summary", meta.map(cm -> cm.getSummary()).orElse(null));
            out.add(m);
        }
        return out;
    }

    /**
     * 标题取"本人首条用户消息"：创建者取会话首条；参与者取加入之后自己的首条，
     * 避免把创建者的消息内容泄露给后来加入者。
     */
    private String titleFor(Long userId, String convId) {
        boolean creator = requestLogRepository.findFirstByConversationIdOrderByIdAsc(convId)
                .map(r -> r.getUserId().equals(userId))
                .orElse(false);
        java.util.Optional<ChatMessageEntity> first;
        if (creator) {
            first = chatMessageRepository.findFirstByConversationIdAndRoleOrderByIdAsc(convId, "user");
        } else {
            LocalDateTime since = requestLogRepository
                    .findFirstByConversationIdAndUserIdOrderByIdAsc(convId, userId)
                    .map(r -> r.getCreatedAt())
                    .orElse(null);
            first = since == null
                    ? java.util.Optional.empty()
                    : chatMessageRepository.findFirstByConversationIdAndRoleAndCreatedAtGreaterThanEqualOrderByIdAsc(convId, "user", since);
        }
        return first.map(u -> u.getContent().length() > 20 ? u.getContent().substring(0, 20) : u.getContent())
                .orElse(convId);
    }

    public boolean ownsConversation(Long userId, String conversationId) {
        // 所有权 = 创建者；后来向同 conversationId 发消息的人不获得读取权
        return requestLogRepository.findFirstByConversationIdOrderByIdAsc(conversationId)
                .map(r -> r.getUserId().equals(userId))
                .orElse(false);
    }

    /** 返回 null 表示无所有权 */
    public List<Map<String, Object>> messages(Long userId, String conversationId) {
        if (!ownsConversation(userId, conversationId)) {
            return null;
        }
        // 加法：已删除会话历史不可读
        if (conversationMetaRepository.findByConversationId(conversationId)
                .map(m -> Boolean.TRUE.equals(m.getDeleted())).orElse(false)) {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatMessageEntity e : chatMessageRepository.findByConversationIdOrderByIdAsc(conversationId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("role", e.getRole());
            m.put("content", e.getContent());
            m.put("toolName", e.getToolName());
            m.put("toolArgs", e.getToolArgs());
            m.put("toolDurationMs", e.getToolDurationMs());
            m.put("sources", e.getSources());
            m.put("createdAt", e.getCreatedAt().toString());
            out.add(m);
        }
        return out;
    }

    /** 仅本人订单；不存在或他人订单返回 null */
    public Order ownOrder(Long userId, String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .filter(o -> o.getUserId().equals(userId))
                .orElse(null);
    }

    /** 加法：本人订单列表 */
    public List<Order> ownOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreateTimeDescIdDesc(userId);
    }
}