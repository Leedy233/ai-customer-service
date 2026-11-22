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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_refund")
@Getter
@Setter
@NoArgsConstructor
public class RefundOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String orderNo;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime confirmedAt;

    /**
     * 管理端操作备注（加法列）：驳回理由 / 完结备注。
     */
    @Column(length = 200)
    private String adminNote;

    /**
     * 乐观锁：防并发确认竞态（加法列）。
     */
    @jakarta.persistence.Version
    private Long version;
}