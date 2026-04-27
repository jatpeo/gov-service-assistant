package com.gov.assistant.controller.admin;

import com.gov.assistant.entity.Conversation;
import com.gov.assistant.entity.Message;
import com.gov.assistant.repository.ConversationRepository;
import com.gov.assistant.repository.MessageRepository;
import com.gov.assistant.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConversationAdminController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationService conversationService;

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<Page<Conversation>> getConversations(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(conversationRepository.findAllByOrderByCreatedAtDesc(pageable));
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/conversations/{sessionId}")
    public ResponseEntity<Conversation> getConversation(@PathVariable String sessionId) {
        return conversationRepository.findBySessionId(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取会话消息
     */
    @GetMapping("/conversations/{sessionId}/messages")
    public ResponseEntity<List<Message>> getConversationMessages(@PathVariable String sessionId) {
        Conversation conversation = conversationRepository.findBySessionId(sessionId).orElse(null);
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(messageRepository.findByConversationIdOrderBySequenceAsc(conversation.getId()));
    }

    /**
     * 获取转人工会话（待接入列表）
     * 只返回状态为 HANDOFF 且未分配客服的会话
     */
    @GetMapping("/handoff-conversations")
    public ResponseEntity<List<Conversation>> getHandoffConversations() {
        return ResponseEntity.ok(
                conversationRepository.findByHumanHandoffTrueAndStatusAndAgentIdIsNullOrderByHandoffTimeDesc(
                        Conversation.ConversationStatus.HANDOFF
                )
        );
    }

    /**
     * 获取我的接入会话
     */
    @GetMapping("/my-sessions")
    public ResponseEntity<List<Conversation>> getMySessions() {
        // TODO: 从当前登录用户获取 agentId
        String agentId = "agent_001";
        return ResponseEntity.ok(conversationRepository.findByAgentIdAndStatus(agentId, Conversation.ConversationStatus.HANDOFF));
    }

    /**
     * 接入会话
     */
    @PostMapping("/conversations/{sessionId}/accept")
    public ResponseEntity<Void> acceptConversation(@PathVariable String sessionId) {
        // TODO: 从当前登录用户获取 agentId
        String agentId = "agent_001";
        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));
        conversation.setAgentId(agentId);
        conversationRepository.save(conversation);

        // 添加系统消息通知用户
        conversationService.addMessage(sessionId, Message.SenderType.SYSTEM,
                "人工客服已接入，现在可以开始对话了",
                null, null);

        log.info("客服接入会话: sessionId={}, agentId={}", sessionId, agentId);
        return ResponseEntity.ok().build();
    }

    /**
     * 关闭会话
     */
    @PostMapping("/conversations/{sessionId}/close")
    public ResponseEntity<Void> closeConversation(@PathVariable String sessionId) {
        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));
        conversation.setStatus(Conversation.ConversationStatus.CLOSED);
        conversation.setEndedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        log.info("关闭会话: sessionId={}", sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 完成会话
     */
    @PostMapping("/conversations/{sessionId}/complete")
    public ResponseEntity<Void> completeConversation(@PathVariable String sessionId) {
        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));
        conversation.setStatus(Conversation.ConversationStatus.CLOSED);
        conversation.setEndedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        log.info("完成会话: sessionId={}", sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 人工回复消息
     */
    @PostMapping("/conversations/{sessionId}/reply")
    public ResponseEntity<Message> replyMessage(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        String content = request.get("content");
        Message message = conversationService.addMessage(
                sessionId,
                Message.SenderType.HUMAN,
                content,
                null,
                null
        );
        log.info("人工客服回复: sessionId={}, content={}", sessionId, content);
        return ResponseEntity.ok(message);
    }
}
