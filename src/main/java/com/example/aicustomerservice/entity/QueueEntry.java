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
 * 加法：转人工排队真值表。位置 = 同状态 WAITING 中 joinedAt 更早者数量 + 1。
 */
@Entity
@Table(name = "ai_queue_entry")
@Getter
@Setter
@NoArgsConstructor
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(nullable = false)
    private Long userId;

    /** WAITING / CANCELLED / SERVED */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;
}