package com.example.aicustomerservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_chat_message")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 64)
    private String toolName;

    /** 加法：工具真入参 JSON（SSE v2 工具事件落库） */
    @Column(length = 512)
    private String toolArgs;

    /** 加法：工具真耗时毫秒 */
    private Long toolDurationMs;

    /** 加法：assistant 行真溯源 JSON 数组 [{doc,section,excerpt}] */
    @Column(columnDefinition = "text")
    private String sources;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}