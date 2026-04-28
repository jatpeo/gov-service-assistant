package com.gov.assistant.entity.document;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_items")
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 手工知识库条目实体。
 *
 * 调用链：
 * KnowledgeAdminController 维护条目 -> KnowledgeItemRepository 持久化；
 * ChatService 兜底检索/RagKnowledgeService 构建索引时读取启用条目。
 */
public class KnowledgeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 知识条目编号
    @Column(name = "item_code", unique = true, nullable = false)
    private String itemCode;

    // 知识分类
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private KnowledgeCategory category;

    // 问题/标题
    @Column(name = "question", nullable = false, length = 500)
    private String question;

    // 答案内容
    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    // 关键词（多个用逗号分隔，用于检索）
    @Column(name = "keywords")
    private String keywords;

    // 相关法规/政策文件
    @Column(name = "related_policy")
    private String relatedPolicy;

    // 适用对象
    @Column(name = "applicable_target")
    private String applicableTarget;

    // 状态：ENABLED-启用, DISABLED-禁用
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ENABLED;

    // 浏览次数
    @Column(name = "view_count")
    private Integer viewCount = 0;

    // 有效次数（被引用次数）
    @Column(name = "hit_count")
    private Integer hitCount = 0;

    // 创建人
    @Column(name = "created_by")
    private String createdBy;

    // 更新人
    @Column(name = "updated_by")
    private String updatedBy;

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

    public enum KnowledgeCategory {
        POLICY_REGULATION,     // 政策法规
        BUSINESS_GUIDE,        // 业务指南
        OPERATION_PROCESS,     // 操作流程
        FAQ,                   // 常见问题
        SYSTEM_FUNCTION,       // 系统功能
        DATA_REPORT,          // 数据报表
        SECURITY_COMPLIANCE   // 安全合规
    }

    public enum Status {
        ENABLED,    // 启用
        DISABLED    // 禁用
    }
}
