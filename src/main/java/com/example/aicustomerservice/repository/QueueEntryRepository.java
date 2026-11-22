package com.example.aicustomerservice.repository;

import com.example.aicustomerservice.entity.QueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    Optional<QueueEntry> findFirstByConversationIdAndStatusOrderByIdDesc(String conversationId, String status);

    List<QueueEntry> findByStatusOrderByJoinedAtAscIdAsc(String status);
}