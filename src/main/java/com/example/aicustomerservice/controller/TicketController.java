package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.security.CurrentUser;
import com.example.aicustomerservice.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户侧工单接口（加法）：转人工 / 结束回执创建、取消排队、满意度评价、本人工单列表。
 * 替代前端原 mock 工单号；排队位置仍为前端演示值（排队接口待后端补充）。
 */
@RestController
@RequestMapping("/ai/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final CurrentUser currentUser;

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String conversationId = String.valueOf(body.getOrDefault("conversationId", ""));
        String kind = String.valueOf(body.getOrDefault("kind", ""));
        if (conversationId.isBlank()) {
            return ApiResponse.error(400, "conversationId 不能为空");
        }
        String category = body.get("category") == null ? null : String.valueOf(body.get("category"));
        String summary = body.get("summary") == null ? null : String.valueOf(body.get("summary"));
        Boolean resolved = body.get("resolved") instanceof Boolean b ? b : null;
        var t = ticketService.create(currentUser.requireUserId(), conversationId, kind, category, summary, resolved);
        return ApiResponse.success(ticketService.vo(t));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        var t = ticketService.cancel(currentUser.requireUserId(), id);
        return ApiResponse.success(ticketService.vo(t));
    }

    @PostMapping("/{id}/rate")
    public ApiResponse<Map<String, Object>> rate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int rating = body.get("rating") instanceof Number n ? n.intValue() : 0;
        String feedback = body.get("feedback") == null ? null : String.valueOf(body.get("feedback"));
        Boolean resolved = body.get("resolved") instanceof Boolean b ? b : null;
        var t = ticketService.rate(currentUser.requireUserId(), id, rating, feedback, resolved);
        return ApiResponse.success(ticketService.vo(t));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<Map<String, Object>> resolve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean resolved = Boolean.TRUE.equals(body.get("resolved"));
        var t = ticketService.resolve(currentUser.requireUserId(), id, resolved);
        return ApiResponse.success(ticketService.vo(t));
    }

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine() {
        return ApiResponse.success(ticketService.mine(currentUser.requireUserId()));
    }
}