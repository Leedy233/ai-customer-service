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
public class OrderTools {

    private final OrderRepository orderRepository;
    private final ChatMessageService chatMessageService;
    private final com.example.aicustomerservice.service.ToolEventBus toolEventBus;

    @Tool(description = "根据订单号查询订单的状态和详细信息（商品名称、金额、下单时间）。"
            + "当用户询问订单状态、订单详情等问题时调用此工具，禁止凭空编造订单信息。")
    public Object queryOrder(@ToolParam(description = "订单号，例如 10001") String orderNo, ToolContext toolContext) {
        long t0 = System.nanoTime();
        log.info("【Tool Calling】订单查询工具被大模型调用，订单号: {}", orderNo);
        String conversationId = (String) toolContext.getContext().get("conversationId");

        Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null) {
            return "未查询到订单号为 " + orderNo + " 的订单，请核对订单号";
        }

        // 生产环境必须的权限校验：订单必须属于当前登录用户
        Long currentUserId = ((Number) toolContext.getContext().get("userId")).longValue();
        if (!order.getUserId().equals(currentUserId)) {
            log.info("【Tool Calling】权限校验拦截：订单 {} 属于用户 {}，当前登录用户 {}",
                    orderNo, order.getUserId(), currentUserId);
            return "无权查询该订单：该订单不属于当前登录用户";
        }

        // 加法：真入参 + 真耗时落库并推 SSE v2 事件
        long ms = (System.nanoTime() - t0) / 1_000_000;
        String args = "{\"orderNo\":\"" + orderNo.replaceAll("[^0-9A-Za-z-]", "") + "\"}";
        String info = "订单查询：订单=" + orderNo + "，商品=" + order.getProductName() + "，状态=" + order.getStatus();
        chatMessageService.saveTool(conversationId, "queryOrder", info, args, ms);
        toolEventBus.publish(conversationId, "{\"name\":\"queryOrder\",\"args\":" + args + ",\"durationMs\":" + ms + "}");
        return order;
    }
}