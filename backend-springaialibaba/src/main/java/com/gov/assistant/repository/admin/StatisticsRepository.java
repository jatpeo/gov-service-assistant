package com.gov.assistant.repository.admin;

import com.gov.assistant.entity.admin.Statistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatisticsRepository extends JpaRepository<Statistics, Long> {

    // 根据日期查询
    Optional<Statistics> findByStatDate(LocalDate statDate);

    // 查询日期范围内的统计
    List<Statistics> findByStatDateBetweenOrderByStatDateAsc(LocalDate startDate, LocalDate endDate);

    // 查询最近N天的统计
    @Query("SELECT s FROM Statistics s WHERE s.statDate >= :startDate ORDER BY s.statDate ASC")
    List<Statistics> findRecentStatistics(@Param("startDate") LocalDate startDate);

    // 计算总满意度平均分
    @Query("SELECT AVG(s.averageRating) FROM Statistics s WHERE s.statDate BETWEEN :startDate AND :endDate")
    Double calculateAverageSatisfaction(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // 计算总会话数
    @Query("SELECT SUM(s.totalConversations) FROM Statistics s WHERE s.statDate BETWEEN :startDate AND :endDate")
    Long sumTotalConversations(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // 计算总转人工数
    @Query("SELECT SUM(s.handoffConversations) FROM Statistics s WHERE s.statDate BETWEEN :startDate AND :endDate")
    Long sumHandoffConversations(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // 计算总消息数
    @Query("SELECT SUM(s.totalMessages) FROM Statistics s WHERE s.statDate BETWEEN :startDate AND :endDate")
    Long sumTotalMessages(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // 计算知识库命中率
    @Query("SELECT AVG(s.knowledgeHitRate) FROM Statistics s WHERE s.statDate BETWEEN :startDate AND :endDate")
    Double calculateAverageKnowledgeHitRate(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // 查询今日统计（不存在则返回null）
    @Query("SELECT s FROM Statistics s WHERE s.statDate = CURRENT_DATE")
    Optional<Statistics> findTodayStatistics();

    // 汇总各意图类型数量
    @Query("SELECT " +
           "SUM(s.policyConsultationCount), " +
           "SUM(s.businessProcessingCount), " +
           "SUM(s.progressQueryCount), " +
           "SUM(s.technicalSupportCount), " +
           "SUM(s.accountPermissionCount), " +
           "SUM(s.complaintSuggestionCount) " +
           "FROM Statistics s WHERE s.statDate BETWEEN :startDate AND :endDate")
    List<Object[]> sumIntentTypeCounts(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
