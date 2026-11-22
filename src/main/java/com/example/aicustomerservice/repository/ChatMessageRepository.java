package com.example.aicustomerservice.repository;

import com.example.aicustomerservice.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findByConversationIdOrderByIdAsc(String conversationId);

    Optional<ChatMessageEntity> findFirstByConversationIdAndRoleOrderByIdAsc(String conversationId, String role);

    Optional<ChatMessageEntity> findFirstByConversationIdAndRoleAndCreatedAtGreaterThanEqualOrderByIdAsc(
            String conversationId, String role, LocalDateTime createdAt);

    /** 加法：末条助手答复（会话摘要提取用） */
    Optional<ChatMessageEntity> findFirstByConversationIdAndRoleOrderByIdDesc(String conversationId, String role);

    /** 加法：真删除会话消息 */
    void deleteByConversationId(String conversationId);
}