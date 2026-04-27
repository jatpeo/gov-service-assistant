package com.gov.assistant.repository;

import com.gov.assistant.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // 根据会话ID查询
    Optional<Conversation> findBySessionId(String sessionId);

    // 查询用户的所有会话
    List<Conversation> findByUserIdOrderByCreatedAtDesc(String userId);

    // 分页查询所有会话
    Page<Conversation> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 根据状态查询会话
    List<Conversation> findByStatusOrderByCreatedAtDesc(Conversation.ConversationStatus status);

    // 根据意图类型查询
    List<Conversation> findByPrimaryIntentOrderByCreatedAtDesc(Conversation.IntentType intentType);

    // 查询已转人工的会话
    List<Conversation> findByHumanHandoffTrueOrderByHandoffTimeDesc();

    // 查询待接入的会话（已转人工、状态为HANDOFF、未分配客服）
    List<Conversation> findByHumanHandoffTrueAndStatusAndAgentIdIsNullOrderByHandoffTimeDesc(Conversation.ConversationStatus status);

    // 查询时间范围内的会话
    @Query("SELECT c FROM Conversation c WHERE c.createdAt BETWEEN :startTime AND :endTime ORDER BY c.createdAt DESC")
    List<Conversation> findByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // 统计各意图类型的数量
    @Query("SELECT c.primaryIntent, COUNT(c) FROM Conversation c WHERE c.createdAt BETWEEN :startTime AND :endTime GROUP BY c.primaryIntent")
    List<Object[]> countByIntentTypeAndTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // 统计满意度分布
    @Query("SELECT c.satisfactionScore, COUNT(c) FROM Conversation c WHERE c.satisfactionScore IS NOT NULL AND c.createdAt BETWEEN :startTime AND :endTime GROUP BY c.satisfactionScore")
    List<Object[]> countSatisfactionByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // 查询超时会话（超过30分钟未活动）
    @Query("SELECT c FROM Conversation c WHERE c.status = 'ACTIVE' AND c.updatedAt < :timeoutTime")
    List<Conversation> findTimeoutConversations(@Param("timeoutTime") LocalDateTime timeoutTime);

    // 统计今日会话数
    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.createdAt >= :today")
    Long countTodayConversations(@Param("today") LocalDateTime today);

    // 统计转人工率
    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.humanHandoff = true AND c.createdAt BETWEEN :startTime AND :endTime")
    Long countHandoffConversations(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // 根据客服ID和状态查询会话
    List<Conversation> findByAgentIdAndStatus(String agentId, Conversation.ConversationStatus status);
}
