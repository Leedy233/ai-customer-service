package com.example.aicustomerservice.repository;

import com.example.aicustomerservice.entity.ConversationMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationMetaRepository extends JpaRepository<ConversationMeta, Long> {

    Optional<ConversationMeta> findByConversationId(String conversationId);
}