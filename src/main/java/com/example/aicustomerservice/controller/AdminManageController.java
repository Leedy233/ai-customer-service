package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.common.PageVO;
import com.example.aicustomerservice.service.AdminManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端业务管理接口（加法）：订单 / 退款 / 对话审计 / 请求日志。
 * 权限由 AdminInterceptor 强制 ADMIN；审计为全量只读视角。
 */
@RestController
@RequestMapping("/ai/admin")
@RequiredArgsConstructor
public class AdminManageController {

    private final AdminManageService service;

    @GetMapping("/orders")
    public ApiResponse<PageVO<Map<String, Object>>> orders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.orders(orderNo, status, page, clamp(size)));
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<Map<String, Object>> orderDetail(@PathVariable String orderNo) {
        return ApiResponse.success(service.orderDetail(orderNo));
    }

    @PostMapping("/orders/{orderNo}/ship")
    public ApiResponse<Map<String, Object>> ship(@PathVariable String orderNo) {
        var order = service.ship(orderNo);
        return ApiResponse.success(Map.of("orderNo", order.getOrderNo(), "status", order.getStatus()));
    }

    @GetMapping("/refunds")
    public ApiResponse<PageVO<Map<String, Object>>> refunds(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.refunds(status, orderNo, page, clamp(size)));
    }

    @PostMapping("/refunds/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id,
                                                   @RequestParam(required = false) String note) {
        var r = service.reject(id, note);
        return ApiResponse.success(Map.of("id", r.getId(), "status", r.getStatus()));
    }

    @PostMapping("/refunds/{id}/complete")
    public ApiResponse<Map<String, Object>> complete(@PathVariable Long id,
                                                     @RequestParam(required = false) String note) {
        var r = service.complete(id, note);
        return ApiResponse.success(Map.of("id", r.getId(), "status", r.getStatus()));
    }

    @GetMapping("/audit/conversations")
    public ApiResponse<PageVO<Map<String, Object>>> auditConversations(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.auditConversations(keyword, username, page, clamp(size)));
    }

    @GetMapping("/audit/conversations/{id}/messages")
    public ApiResponse<List<Map<String, Object>>> auditMessages(@PathVariable String id) {
        return ApiResponse.success(service.auditMessages(id));
    }

    @GetMapping("/logs")
    public ApiResponse<PageVO<Map<String, Object>>> logs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String model,
            @RequestParam(defaultValue = "false") boolean onlyError,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.logs(userId, model, onlyError, page, clamp(size)));
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(size, 50));
    }
}