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

@Entity
@Table(name = "ai_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    /**
     * 角色：USER / ADMIN。加法列，旧数据由 DataSeeder 回填 USER。
     */
    @Column(length = 20)
    private String role;

    /** 加法：客户资料真值（/ai/me 服务端脱敏后返回） */
    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    /** 加法：客户标签，逗号分隔 */
    @Column(length = 200)
    private String tags;
}