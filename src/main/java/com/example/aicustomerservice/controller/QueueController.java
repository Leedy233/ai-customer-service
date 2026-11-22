package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.security.CurrentUser;
import com.example.aicustomerservice.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 加法：转人工排队真值接口（替代前端演示位置）。
 * join 幂等（同会话重复加入返回当前排队状态）；status 供前端轮询。
 */
@RestController
@RequestMapping("/ai/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final CurrentUser currentUser;

    @PostMapping("/join")
    public ApiResponse<Map<String, Object>> join(@RequestBody Map<String, String> body) {
        String conversationId = body.get("conversationId");
        if (conversationId == null || conversationId.isBlank()) {
            return ApiResponse.error(400, "conversationId 必填");
        }
        return ApiResponse.success(queueService.join(currentUser.requireUserId(), conversationId));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(@RequestParam String conversationId) {
        return ApiResponse.success(queueService.status(conversationId));
    }

    @PostMapping("/cancel")
    public ApiResponse<Map<String, Object>> cancel(@RequestBody Map<String, String> body) {
        String conversationId = body.get("conversationId");
        if (conversationId == null || conversationId.isBlank()) {
            return ApiResponse.error(400, "conversationId 必填");
        }
        return ApiResponse.success(queueService.cancel(conversationId));
    }
}