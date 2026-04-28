package com.gov.assistant.entity.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "messages")
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 会话消息实体。
 *
 * 调用链：
 * ConversationService.addMessage() 创建消息 -> MessageRepository 持久化；
 * ChatService/人工客服回复/系统通知都会通过该实体记录消息轨迹。
 */
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 所属会话
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @JsonIgnore
    private Conversation conversation;

    // 消息序号（用于排序）
    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    // 发送者类型：USER-用户, AI-AI助手, HUMAN-人工客服, SYSTEM-系统
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false)
    private SenderType senderType;

    // 消息内容
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 意图识别结果
    @Enumerated(EnumType.STRING)
    @Column(name = "intent_type")
    private Conversation.IntentType intentType;

    // 意图置信度 0-1
    @Column(name = "intent_confidence")
    private Double intentConfidence;

    // 是否命中知识库
    @Column(name = "knowledge_hit")
    private Boolean knowledgeHit = false;

    // 知识库来源（多个用逗号分隔）
    @Column(name = "knowledge_sources")
    private String knowledgeSources;

    // 知识库图片片段ID（多个用逗号分隔）
    @Column(name = "knowledge_source_chunk_ids", length = 1000)
    private String knowledgeSourceChunkIds;

    // 消息类型：TEXT-文本, IMAGE-图片, FILE-文件, TRANSFER-转人工提示
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType = MessageType.TEXT;

    // 是否已读
    @Column(name = "is_read")
    private Boolean isRead = false;

    // 创建时间
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 响应时间（毫秒）- AI回复耗时
    @Column(name = "response_time")
    private Long responseTime;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum SenderType {
        USER,    // 用户
        AI,      // AI助手
        HUMAN,   // 人工客服
        SYSTEM   // 系统消息
    }

    public enum MessageType {
        TEXT,     // 文本
        IMAGE,    // 图片
        FILE,     // 文件
        TRANSFER, // 转人工提示
        SYSTEM    // 系统消息
    }
}
