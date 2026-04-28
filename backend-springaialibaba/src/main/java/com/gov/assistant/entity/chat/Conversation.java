package com.gov.assistant.entity.chat;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "conversations")
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 会话主表实体。
 *
 * 调用链：
 * ConversationService 创建/更新会话 -> ConversationRepository 持久化；
 * ChatController/ConversationAdminController 查询后返回前端展示。
 */
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 会话唯一标识
    @Column(name = "session_id", unique = true, nullable = false)
    private String sessionId;

    // 用户ID（匿名用户可为空）
    @Column(name = "user_id")
    private String userId;

    // 用户昵称
    @Column(name = "user_name")
    private String userName;

    // 会话状态：ACTIVE-进行中, CLOSED-已关闭, HANDOFF-已转人工
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    // 主要意图类型
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_intent")
    private IntentType primaryIntent;

    // 满意度评分 1-5
    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    // 是否已转人工
    @Column(name = "human_handoff")
    private Boolean humanHandoff = false;

    // 转人工时间
    @Column(name = "handoff_time")
    private LocalDateTime handoffTime;

    // 客服人员ID
    @Column(name = "agent_id")
    private String agentId;

    // 创建时间
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 更新时间
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 结束时间
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // 关联的消息列表
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    private List<Message> messages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ConversationStatus {
        ACTIVE,      // 进行中
        CLOSED,      // 已关闭
        HANDOFF,     // 已转人工
        TIMEOUT      // 已超时
    }

    public enum IntentType {
        POLICY_CONSULTATION,   // 政策咨询
        BUSINESS_PROCESSING,   // 业务办理
        PROGRESS_QUERY,        // 进度查询
        TECHNICAL_SUPPORT,     // 技术支持
        ACCOUNT_PERMISSION,    // 账号权限
        COMPLAINT_SUGGESTION,  // 投诉建议
        OTHER                  // 其他
    }
}
