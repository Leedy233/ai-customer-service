package com.example.aicustomerservice.repository;

import com.example.aicustomerservice.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    List<RequestLog> findByConversationIdStartingWith(String prefix);

    boolean existsByConversationIdAndUserId(String conversationId, Long userId);

    /** 会话创建者 = 该会话最早一条请求日志的用户（所有权判定依据，防"加入即拥有"） */
    java.util.Optional<RequestLog> findFirstByConversationIdOrderByIdAsc(String conversationId);

    java.util.Optional<RequestLog> findFirstByConversationIdAndUserIdOrderByIdAsc(String conversationId, Long userId);

    @Query("select r.conversationId, max(r.createdAt) from RequestLog r where r.userId = :userId "
            + "group by r.conversationId order by max(r.createdAt) desc")
    List<Object[]> findConversationSummaries(@Param("userId") Long userId);
}