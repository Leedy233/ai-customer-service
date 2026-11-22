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
@Table(name = "t_order")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = false, length = 32)
    private String orderNo;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status;

    private LocalDateTime createTime;

    /** 加法：发货时间（管理端发货动作写入） */
    private LocalDateTime shippedAt;

    /** 加法：预计送达（发货时 = 发货时间 + 2 天） */
    private LocalDateTime eta;
}