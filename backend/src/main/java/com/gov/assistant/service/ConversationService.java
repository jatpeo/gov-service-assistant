package com.gov.assistant.service;

import com.gov.assistant.entity.Conversation;
import com.gov.assistant.entity.Message;
import com.gov.assistant.repository.ConversationRepository;
import com.gov.assistant.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * 创建新会话
     */
    @Transactional
    public Conversation createConversation(String userId, String userName) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        Conversation conversation = Conversation.builder()
                .sessionId(sessionId)
                .userId(userId)
                .userName(userName)
                .status(Conversation.ConversationStatus.ACTIVE)
                .humanHandoff(false)
                .build();

        Conversation saved = conversationRepository.save(conversation);
        log.info("创建新会话: sessionId={}, userId={}", sessionId, userId);
        return saved;
    }

    /**
     * 根据会话ID获取会话
     */
    public Optional<Conversation> getConversationBySessionId(String sessionId) {
        return conversationRepository.findBySessionId(sessionId);
    }

    /**
     * 获取会话的所有消息
     */
    public List<Message> getConversationMessages(String sessionId) {
        return conversationRepository.findBySessionId(sessionId)
                .map(conversation -> messageRepository.findByConversationIdOrderBySequenceAsc(conversation.getId()))
                .orElse(List.of());
    }

    /**
     * 添加消息到会话
     */
    @Transactional
    public Message addMessage(String sessionId, Message.SenderType senderType, String content,
                              Conversation.IntentType intentType, Double intentConfidence) {
        return addMessage(sessionId, senderType, content, intentType, intentConfidence, Message.MessageType.TEXT);
    }

    /**
     * 添加消息到会话
     */
    @Transactional
    public Message addMessage(String sessionId, Message.SenderType senderType, String content,
                              Conversation.IntentType intentType, Double intentConfidence,
                              Message.MessageType messageType) {
        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        // 计算消息序号
        int sequence = conversation.getMessages().size() + 1;

        Message message = Message.builder()
                .conversation(conversation)
                .sequence(sequence)
                .senderType(senderType)
                .content(content)
                .intentType(intentType)
                .intentConfidence(intentConfidence)
                .messageType(messageType == null ? Message.MessageType.TEXT : messageType)
                .build();

        Message saved = messageRepository.save(message);
        conversation.getMessages().add(saved);

        // 更新会话的主要意图（取第一个用户消息的意图）
        if (senderType == Message.SenderType.USER && conversation.getPrimaryIntent() == null) {
            conversation.setPrimaryIntent(intentType);
            conversationRepository.save(conversation);
        }

        return saved;
    }

    /**
     * 转人工处理
     */
    @Transactional
    public void handoffToHuman(String sessionId, String agentId) {
        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        conversation.setStatus(Conversation.ConversationStatus.HANDOFF);
        conversation.setHumanHandoff(true);
        conversation.setHandoffTime(LocalDateTime.now());
        conversation.setAgentId(agentId);

        conversationRepository.save(conversation);
        log.info("会话转人工: sessionId={}, agentId={}", sessionId, agentId);
    }

    /**
     * 关闭会话
     */
    @Transactional
    public void closeConversation(String sessionId, Integer satisfactionScore) {
        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        conversation.setStatus(Conversation.ConversationStatus.CLOSED);
        conversation.setSatisfactionScore(satisfactionScore);
        conversation.setEndedAt(LocalDateTime.now());

        conversationRepository.save(conversation);
        log.info("关闭会话: sessionId={}, satisfactionScore={}", sessionId, satisfactionScore);
    }

    /**
     * 分页查询所有会话
     */
    public Page<Conversation> getAllConversations(Pageable pageable) {
        return conversationRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * 查询用户的会话历史
     */
    public List<Conversation> getUserConversations(String userId) {
        return conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 查询需要转人工的会话
     */
    public List<Conversation> getHandoffConversations() {
        return conversationRepository.findByHumanHandoffTrueOrderByHandoffTimeDesc();
    }

    /**
     * 处理超时会话
     */
    @Transactional
    public void handleTimeoutConversations() {
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(30);
        List<Conversation> timeoutConversations = conversationRepository.findTimeoutConversations(timeoutTime);

        for (Conversation conversation : timeoutConversations) {
            conversation.setStatus(Conversation.ConversationStatus.TIMEOUT);
            conversation.setEndedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
            log.info("会话超时关闭: sessionId={}", conversation.getSessionId());
        }
    }

    /**
     * 统计今日会话数
     */
    public Long countTodayConversations() {
        return conversationRepository.countTodayConversations(LocalDateTime.now().toLocalDate().atStartOfDay());
    }
}
