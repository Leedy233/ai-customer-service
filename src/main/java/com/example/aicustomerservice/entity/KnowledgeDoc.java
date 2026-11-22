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
 * RAG 知识库文档（加法）：classpath 种子迁移入库后的可管理知识源。
 * source=CLASSPATH 内置种子 / ADMIN 管理端创建。
 */
@Entity
@Table(name = "ai_knowledge_doc")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}