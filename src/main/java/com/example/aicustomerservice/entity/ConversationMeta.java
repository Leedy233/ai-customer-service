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
 * 加法：会话元数据（服务端摘要 + 删除墓碑）。
 * 删除会话保留审计日志与工单，靠墓碑阻止会话列表/历史复活。
 */
@Entity
@Table(name = "ai_conversation_meta")
@Getter
@Setter
@NoArgsConstructor
public class ConversationMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String conversationId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 200)
    private String summary;

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}