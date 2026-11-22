package com.example.aicustomerservice.tool;

import com.example.aicustomerservice.entity.Order;
import com.example.aicustomerservice.repository.OrderRepository;
import com.example.aicustomerservice.service.ChatMessageService;
import com.example.aicustomerservice.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundTools {

    private final OrderRepository orderRepository;
    private final ChatMessageService chatMessageService;
    private final com.example.aicustomerservice.service.ToolEventBus toolEventBus;

    @Tool(description = "检查订单是否符合退款条件。当用户表达退款、退货意愿时调用此工具。"
            + "此工具只做资格检查，不会执行退款；退款必须由用户通过 /ai/refund/apply 生成申请、"
            + "再通过 /ai/refund/confirm 确认两步完成，禁止声称退款已执行。")
    public Object checkRefundEligibility(@ToolParam(description = "订单号，例如 10001") String orderNo, ToolContext toolContext) {
        long t0 = System.nanoTime();
        log.info("【Tool Calling】退款资格检查工具被大模型调用，订单号: {}", orderNo);
        String conversationId = (String) toolContext.getContext().get("conversationId");

        Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null) {
            return "未查询到订单号为 " + orderNo + " 的订单，请核对订单号";
        }
        Long currentUserId = ((Number) toolContext.getContext().get("userId")).longValue();
        if (!order.getUserId().equals(currentUserId)) {
            return "无权操作该订单：该订单不属于当前登录用户";
        }

        String reason = RefundService.checkEligibility(order);
        Object result;
        if (reason == null) {
            result = Map.of("eligible", true, "orderNo", orderNo, "amount", order.getAmount(),
                    "status", order.getStatus(),
                    "note", "符合退款条件。请告知用户：先调用 POST /ai/refund/apply 生成退款申请，再调用 POST /ai/refund/confirm 确认后才会执行退款");
        } else {
            result = Map.of("eligible", false, "orderNo", orderNo, "reason", reason);
        }

        long ms = (System.nanoTime() - t0) / 1_000_000;
        String args = "{\"orderNo\":\"" + orderNo.replaceAll("[^0-9A-Za-z-]", "") + "\"}";
        String info = "退款资格检查：订单=" + orderNo + "，结果=" + (reason == null ? "符合退款条件" : reason);
        chatMessageService.saveTool(conversationId, "checkRefundEligibility", info, args, ms);
        toolEventBus.publish(conversationId, "{\"name\":\"checkRefundEligibility\",\"args\":" + args + ",\"durationMs\":" + ms + "}");
        return result;
    }
}