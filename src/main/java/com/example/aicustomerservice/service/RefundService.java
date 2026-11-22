package com.example.aicustomerservice.service;

import com.example.aicustomerservice.entity.Order;
import com.example.aicustomerservice.entity.RefundOrder;
import com.example.aicustomerservice.repository.OrderRepository;
import com.example.aicustomerservice.repository.RefundOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    public static final String STATUS_PENDING = "PENDING_CONFIRM";
    public static final String STATUS_REFUNDED = "REFUNDED";

    private final OrderRepository orderRepository;
    private final RefundOrderRepository refundOrderRepository;
    private final ChatMessageService chatMessageService;

    /**
     * 生成退款申请（只创建待确认记录，不修改订单数据）。
     */
    @Transactional
    public Map<String, Object> apply(String conversationId, String orderNo, Long userId) {
        Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null) {
            return Map.of("success", false, "message", "未查询到订单 " + orderNo);
        }
        if (!order.getUserId().equals(userId)) {
            return Map.of("success", false, "message", "无权操作该订单：订单不属于当前登录用户");
        }
        String reason = checkEligibility(order);
        if (reason != null) {
            return Map.of("success", false, "message", reason);
        }
        if (refundOrderRepository.existsByOrderNoAndStatusIn(orderNo, List.of(STATUS_PENDING, STATUS_REFUNDED))) {
            return Map.of("success", false, "message", "该订单已有进行中或已完成的退款申请，请勿重复提交");
        }

        RefundOrder refund = new RefundOrder();
        refund.setOrderNo(orderNo);
        refund.setUserId(order.getUserId());
        refund.setConversationId(conversationId);
        refund.setAmount(order.getAmount());
        refund.setStatus(STATUS_PENDING);
        refund.setCreatedAt(LocalDateTime.now());
        refundOrderRepository.save(refund);

        chatMessageService.save(conversationId, "tool",
                "生成退款申请：refundId=" + refund.getId() + "，订单=" + orderNo + "，金额=" + order.getAmount() + "，状态=待用户确认",
                "refundApply");
        log.info("【退款流程】生成待确认退款申请 refundId={}，订单={}", refund.getId(), orderNo);

        return Map.of("success", true, "refundId", refund.getId(), "orderNo", orderNo,
                "amount", order.getAmount(), "message", "退款申请已生成，需用户调用 /ai/refund/confirm 确认后才会执行");
    }

    /**
     * 用户确认后才真正执行退款：更新订单状态并留下操作记录。
     */
    @Transactional
    public Map<String, Object> confirm(Long refundId, Long userId) {
        RefundOrder refund = refundOrderRepository.findById(refundId).orElse(null);
        if (refund == null) {
            return Map.of("success", false, "message", "未找到退款申请 " + refundId);
        }
        if (!refund.getUserId().equals(userId)) {
            return Map.of("success", false, "message", "无权操作该退款申请");
        }
        if (!STATUS_PENDING.equals(refund.getStatus())) {
            return Map.of("success", false, "message", "退款申请当前状态为 " + refund.getStatus() + "，不可重复确认");
        }

        refund.setStatus(STATUS_REFUNDED);
        refund.setConfirmedAt(LocalDateTime.now());
        refundOrderRepository.save(refund);

        Order order = orderRepository.findByOrderNo(refund.getOrderNo()).orElseThrow();
        order.setStatus("退款中");
        orderRepository.save(order);

        chatMessageService.save(refund.getConversationId(), "tool",
                "用户确认退款：refundId=" + refundId + "，订单=" + refund.getOrderNo() + "，订单状态更新为退款中",
                "refundConfirm");
        log.info("【退款流程】用户确认退款 refundId={}，订单={} 状态更新为退款中", refundId, refund.getOrderNo());

        return Map.of("success", true, "refundId", refundId, "orderNo", refund.getOrderNo(),
                "message", "退款已确认执行，订单状态更新为退款中");
    }

    /**
     * 退款资格校验（只读），返回 null 表示符合退款条件。
     */
    public static String checkEligibility(Order order) {
        if ("退款中".equals(order.getStatus()) || "已退款".equals(order.getStatus())) {
            return "订单已在退款流程中，不能重复申请";
        }
        if (order.getCreateTime() != null && order.getCreateTime().isBefore(LocalDateTime.now().minusDays(7))) {
            return "订单已超过 7 天退款期限";
        }
        return null;
    }
}