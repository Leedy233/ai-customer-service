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
@Table(name = "ai_request_log")
@Getter
@Setter
@NoArgsConstructor
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 64)
    private String model;

    private Long promptTokens;

    private Long completionTokens;

    @Column(nullable = false)
    private Long latencyMs;

    @Column(columnDefinition = "text")
    private String exception;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}