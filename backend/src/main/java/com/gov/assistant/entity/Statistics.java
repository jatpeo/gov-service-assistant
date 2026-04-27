package com.gov.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "statistics")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Statistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 统计日期
    @Column(name = "stat_date", unique = true, nullable = false)
    private LocalDate statDate;

    // ===== 会话统计 =====

    // 总会话数
    @Column(name = "total_conversations")
    private Integer totalConversations = 0;

    // 活跃会话数
    @Column(name = "active_conversations")
    private Integer activeConversations = 0;

    // 已关闭会话数
    @Column(name = "closed_conversations")
    private Integer closedConversations = 0;

    // 转人工会话数
    @Column(name = "handoff_conversations")
    private Integer handoffConversations = 0;

    // ===== 消息统计 =====

    // 总消息数
    @Column(name = "total_messages")
    private Integer totalMessages = 0;

    // 用户消息数
    @Column(name = "user_messages")
    private Integer userMessages = 0;

    // AI回复数
    @Column(name = "ai_responses")
    private Integer aiResponses = 0;

    // ===== 意图分类统计 =====

    // 政策咨询数
    @Column(name = "policy_consultation_count")
    private Integer policyConsultationCount = 0;

    // 业务办理数
    @Column(name = "business_processing_count")
    private Integer businessProcessingCount = 0;

    // 进度查询数
    @Column(name = "progress_query_count")
    private Integer progressQueryCount = 0;

    // 技术支持数
    @Column(name = "technical_support_count")
    private Integer technicalSupportCount = 0;

    // 账号权限数
    @Column(name = "account_permission_count")
    private Integer accountPermissionCount = 0;

    // 投诉建议数
    @Column(name = "complaint_suggestion_count")
    private Integer complaintSuggestionCount = 0;

    // ===== 满意度统计 =====

    // 总评分次数
    @Column(name = "total_ratings")
    private Integer totalRatings = 0;

    // 评分总和
    @Column(name = "rating_sum")
    private Integer ratingSum = 0;

    // 平均评分
    @Column(name = "average_rating")
    private Double averageRating = 0.0;

    // 5星评价数
    @Column(name = "five_star_count")
    private Integer fiveStarCount = 0;

    // 4星评价数
    @Column(name = "four_star_count")
    private Integer fourStarCount = 0;

    // 3星及以下评价数
    @Column(name = "low_rating_count")
    private Integer lowRatingCount = 0;

    // ===== 知识库统计 =====

    // 知识库命中次数
    @Column(name = "knowledge_hit_count")
    private Integer knowledgeHitCount = 0;

    // 未命中次数
    @Column(name = "knowledge_miss_count")
    private Integer knowledgeMissCount = 0;

    // 命中率
    @Column(name = "knowledge_hit_rate")
    private Double knowledgeHitRate = 0.0;

    // ===== 人工客服统计 =====

    // 人工接入数
    @Column(name = "human_handoff_count")
    private Integer humanHandoffCount = 0;

    // 平均响应时间（秒）
    @Column(name = "avg_response_time")
    private Double avgResponseTime = 0.0;

    // 创建时间
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 更新时间
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
