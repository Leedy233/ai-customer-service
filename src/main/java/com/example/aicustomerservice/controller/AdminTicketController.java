package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.common.PageVO;
import com.example.aicustomerservice.security.CurrentUser;
import com.example.aicustomerservice.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端工单接口（加法）：列表 / 详情 / 接单 / 关闭。
 */
@RestController
@RequestMapping("/ai/admin/tickets")
@RequiredArgsConstructor
public class AdminTicketController {

    private final TicketService ticketService;
    private final CurrentUser currentUser;

    @GetMapping
    public ApiResponse<PageVO<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(ticketService.adminList(status, kind, page, Math.max(1, Math.min(size, 50))));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(ticketService.adminDetail(id));
    }

    @PostMapping("/{id}/process")
    public ApiResponse<Map<String, Object>> process(@PathVariable Long id) {
        var t = ticketService.process(id, currentUser.username());
        return ApiResponse.success(ticketService.vo(t));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable Long id,
                                                  @RequestParam(required = false) String note) {
        var t = ticketService.close(id, note, currentUser.username());
        return ApiResponse.success(ticketService.vo(t));
    }
}