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

/**
 * 人工客服工单（加法）：转人工排队与结束会话回执的真实持久化。
 * kind=TRANSFER 转人工；kind=END 结束会话服务回执。
 * status=OPEN 待处理 / PROCESSING 处理中 / CLOSED 已完成 / CANCELLED 已取消。
 */
@Entity
@Table(name = "ai_ticket")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, unique = true)
    private String ticketNo;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(length = 32)
    private String category;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(nullable = false, length = 16)
    private String status;

    private Integer rating;

    @Column(length = 200)
    private String feedback;

    private Boolean resolved;

    @Column(length = 32)
    private String assignee;

    @Column(length = 200)
    private String closeNote;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime closedAt;
}