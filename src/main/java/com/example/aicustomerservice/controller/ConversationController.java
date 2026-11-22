package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.security.CurrentUser;
import com.example.aicustomerservice.service.UserContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会话历史读接口（验收修复加法，原缺失清单 #1）。
 */
@RestController
@RequestMapping("/ai/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final UserContentService userContentService;
    private final CurrentUser currentUser;
    private final com.example.aicustomerservice.service.ConversationMetaService conversationMetaService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(userContentService.conversations(currentUser.requireUserId()));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<Map<String, Object>>> messages(@PathVariable String id) {
        List<Map<String, Object>> ms = userContentService.messages(currentUser.requireUserId(), id);
        if (ms == null) {
            return ApiResponse.error(403, "无权查看该会话");
        }
        return ApiResponse.success(ms);
    }

    /** 加法：真删除会话（消息清除，审计日志/工单保留，墓碑防复活） */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        boolean ok = conversationMetaService.delete(currentUser.requireUserId(), id);
        if (!ok) {
            return ApiResponse.error(403, "无权删除该会话");
        }
        return ApiResponse.success(Map.of("deleted", true));
    }
}