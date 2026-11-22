package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端 RAG 知识库接口（加法）：CRUD / 启停 / chunk 预览，变更实时同步向量库。
 */
@RestController
@RequestMapping("/ai/admin/knowledge")
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(knowledgeService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(knowledgeService.detail(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "").trim();
        String content = body.getOrDefault("content", "").trim();
        if (title.isEmpty() || content.isEmpty()) {
            return ApiResponse.error(400, "标题与内容不能为空");
        }
        var doc = knowledgeService.create(title, content);
        return ApiResponse.success(Map.of("id", doc.getId(), "title", doc.getTitle()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "").trim();
        String content = body.getOrDefault("content", "").trim();
        if (title.isEmpty() || content.isEmpty()) {
            return ApiResponse.error(400, "标题与内容不能为空");
        }
        var doc = knowledgeService.update(id, title, content);
        return ApiResponse.success(Map.of("id", doc.getId(), "title", doc.getTitle()));
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<Map<String, Object>> toggle(@PathVariable Long id) {
        var doc = knowledgeService.toggle(id);
        return ApiResponse.success(Map.of("id", doc.getId(), "enabled", doc.getEnabled()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResponse.success(Map.of("id", id, "deleted", true));
    }
}