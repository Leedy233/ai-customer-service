package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.entity.Order;
import com.example.aicustomerservice.security.CurrentUser;
import com.example.aicustomerservice.service.UserContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单权威读接口（验收修复加法，原缺失清单 #2 的最小落地）：
 * 订单卡片不再信任模型文本字段，改以本接口 DB 真值渲染。
 */
@RestController
@RequestMapping("/ai/orders")
@RequiredArgsConstructor
public class OrderController {

    private final UserContentService userContentService;
    private final CurrentUser currentUser;

    @GetMapping("/{orderNo}")
    public ApiResponse<Order> get(@PathVariable String orderNo) {
        Order order = userContentService.ownOrder(currentUser.requireUserId(), orderNo);
        if (order == null) {
            return ApiResponse.error(404, "未查询到订单或订单不属于当前用户");
        }
        return ApiResponse.success(order);
    }

    /** 加法：本人订单列表（右栏不再硬编码订单号逐单取） */
    @GetMapping
    public ApiResponse<java.util.List<Order>> list() {
        return ApiResponse.success(userContentService.ownOrders(currentUser.requireUserId()));
    }
}