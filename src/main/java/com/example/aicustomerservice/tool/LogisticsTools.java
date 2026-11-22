package com.example.aicustomerservice.tool;

import com.example.aicustomerservice.entity.Order;
import com.example.aicustomerservice.repository.OrderRepository;
import com.example.aicustomerservice.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogisticsTools {

    private final OrderRepository orderRepository;
    private final ChatMessageService chatMessageService;
    private final com.example.aicustomerservice.service.ToolEventBus toolEventBus;

    @Tool(description = "查询订单的物流发货进度。当用户询问物流、发货时间、快递进度、包裹位置时调用此工具。")
    public Object queryLogistics(@ToolParam(description = "订单号，例如 10001") String orderNo, ToolContext toolContext) {
        long t0 = System.nanoTime();
        log.info("【Tool Calling】物流查询工具被大模型调用，订单号: {}", orderNo);
        String conversationId = (String) toolContext.getContext().get("conversationId");

        Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null) {
            return "未查询到订单号为 " + orderNo + " 的订单，请核对订单号";
        }
        Long currentUserId = ((Number) toolContext.getContext().get("userId")).longValue();
        if (!order.getUserId().equals(currentUserId)) {
            return "无权查询该订单：该订单不属于当前登录用户";
        }

        String info = switch (order.getStatus()) {
            case "待发货" -> "订单 " + orderNo + " 尚未发货，预计 48 小时内发出";
            // 加法：预计送达改读订单 eta 真值（发货时写入），无 eta 的旧数据回退原文案
            case "已发货" -> "订单 " + orderNo + "：顺丰快递，已发货，包裹已到达杭州中转中心，"
                    + (order.getEta() != null ? "预计 " + order.getEta().toLocalDate() + " 送达" : "预计明天送达");
            case "已完成" -> "订单 " + orderNo + "：顺丰快递，已签收";
            case "退款中" -> "订单 " + orderNo + " 正在退款流程中，发货已拦截";
            default -> "订单 " + orderNo + " 当前状态为 " + order.getStatus() + "，暂无物流信息";
        };

        long ms = (System.nanoTime() - t0) / 1_000_000;
        String args = "{\"orderNo\":\"" + orderNo.replaceAll("[^0-9A-Za-z-]", "") + "\"}";
        chatMessageService.saveTool(conversationId, "queryLogistics", info, args, ms);
        toolEventBus.publish(conversationId, "{\"name\":\"queryLogistics\",\"args\":" + args + ",\"durationMs\":" + ms + "}");
        return info;
    }
}