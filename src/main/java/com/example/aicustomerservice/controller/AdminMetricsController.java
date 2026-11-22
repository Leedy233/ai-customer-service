package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.service.AdminMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端聚合指标接口（加法）：Token 消耗 / 响应耗时 / 系统监控。
 */
@RestController
@RequestMapping("/ai/admin")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService service;

    @GetMapping("/stats/tokens")
    public ApiResponse<Map<String, Object>> tokens(@RequestParam(defaultValue = "7") int days) {
        if (days != 7 && days != 30) {
            return ApiResponse.error(400, "days 仅支持 7 或 30");
        }
        return ApiResponse.success(service.tokens(days));
    }

    @GetMapping("/stats/latency")
    public ApiResponse<Map<String, Object>> latency(@RequestParam(defaultValue = "7") int days) {
        if (days != 7 && days != 30) {
            return ApiResponse.error(400, "days 仅支持 7 或 30");
        }
        return ApiResponse.success(service.latency(days));
    }

    @GetMapping("/system")
    public ApiResponse<Map<String, Object>> system() {
        return ApiResponse.success(service.system());
    }
}