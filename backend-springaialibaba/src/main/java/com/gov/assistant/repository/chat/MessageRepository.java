package com.gov.assistant.repository.chat;

import com.gov.assistant.entity.chat.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // 根据会话ID查询所有消息
    List<Message> findByConversationIdOrderBySequenceAsc(Long conversationId);

    // 根据会话ID和序号查询
    List<Message> findByConversationIdAndSequenceGreaterThanOrderBySequenceAsc(Long conversationId, Integer sequence);

    // 统计消息总数
    @Query("SELECT COUNT(m) FROM Message m WHERE m.createdAt BETWEEN :startTime AND :endTime")
    Long countMessagesByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // 统计AI回复的平均响应时间
    @Query("SELECT AVG(m.responseTime) FROM Message m WHERE m.senderType = 'AI' AND m.createdAt BETWEEN :startTime AND :endTime")
    Double calculateAverageResponseTime(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // 查询知识库命中的消息
    @Query("SELECT m FROM Message m WHERE m.knowledgeHit = true AND m.createdAt BETWEEN :startTime AND :endTime")
    List<Message> findKnowledgeHitMessages(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // 统计知识库命中次数
    @Query("SELECT COUNT(m) FROM Message m WHERE m.knowledgeHit = true AND m.createdAt BETWEEN :startTime AND :endTime")
    Long countKnowledgeHits(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
