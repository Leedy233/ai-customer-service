package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.security.CurrentUser;
import com.example.aicustomerservice.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ai/refund")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final CurrentUser currentUser;

    @PostMapping("/apply")
    public ApiResponse<Map<String, Object>> apply(@RequestParam(defaultValue = "default") String conversationId,
                                                  @RequestParam String orderNo) {
        return wrap(refundService.apply(conversationId, orderNo, currentUser.requireUserId()));
    }

    @PostMapping("/confirm")
    public ApiResponse<Map<String, Object>> confirm(@RequestParam Long refundId) {
        return wrap(refundService.confirm(refundId, currentUser.requireUserId()));
    }

    private ApiResponse<Map<String, Object>> wrap(Map<String, Object> result) {
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ApiResponse.success(result);
        }
        return ApiResponse.error(400, (String) result.get("message"));
    }
}