package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端统计接口。权限由 AdminInterceptor 强制校验（ADMIN）。
 */
@RestController
@RequestMapping("/ai/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(adminStatsService.overview());
    }

    @GetMapping("/trends")
    public ApiResponse<List<Map<String, Object>>> trends(@RequestParam(defaultValue = "7") int days) {
        if (days != 7 && days != 30) {
            return ApiResponse.error(400, "days 仅支持 7 或 30");
        }
        return ApiResponse.success(adminStatsService.trends(days));
    }
}