package com.gov.assistant.controller.admin;

import com.gov.assistant.entity.chat.Conversation;
import com.gov.assistant.entity.chat.Message;
import com.gov.assistant.entity.auth.UserAccount;
import com.gov.assistant.repository.chat.ConversationRepository;
import com.gov.assistant.repository.chat.MessageRepository;
import com.gov.assistant.repository.auth.UserAccountRepository;
import com.gov.assistant.service.chat.ConversationService;
import com.gov.assistant.service.auth.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
/**
 * 管理后台会话与人工接入接口。
 *
 * 调用链：
 * Handoff/Conversations 后台页面 -> ConversationAdminController
 * -> ConversationService/ConversationRepository/MessageRepository
 * -> 会话状态流转和人工消息落库。
 */
public class ConversationAdminController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final CurrentUserService currentUserService;
    private final UserAccountRepository userAccountRepository;

    /**
     * 分页获取全部会话列表。
     *
     * 调用链：
     * 会话管理页面 -> GET /api/admin/conversations
     * -> ConversationRepository.findAllByOrderByCreatedAtDesc()。
     */
    @GetMapping("/conversations")
    public ResponseEntity<Page<Conversation>> getConversations(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(conversationRepository.findAllByOrderByCreatedAtDesc(pageable));
    }

    /**
     * 获取单个会话详情。
     *
     * 调用链：
     * 会话详情弹窗 -> GET /api/admin/conversations/{sessionId}
     * -> ConversationRepository.findBySessionId()。
     */
    @GetMapping("/conversations/{sessionId}")
    public ResponseEntity<Conversation> getConversation(@PathVariable String sessionId) {
        return conversationRepository.findBySessionId(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取会话消息明细。
     *
     * 调用链：
     * 会话详情/人工接入弹窗 -> GET /api/admin/conversations/{sessionId}/messages
     * -> MessageRepository.findByConversationIdOrderBySequenceAsc()。
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
     *
     * 调用链：
     * Handoff 页面待接入表格 -> GET /api/admin/handoff-conversations
     * -> ConversationRepository.findByHumanHandoffTrueAndStatusAndAgentIdIsNullOrderByHandoffTimeDesc()。
     */
    @GetMapping("/handoff-conversations")
    public ResponseEntity<List<HandoffSessionResponse>> getHandoffConversations() {
        return ResponseEntity.ok(
                conversationRepository.findByHumanHandoffTrueAndStatusAndAgentIdIsNullOrderByHandoffTimeDesc(
                        Conversation.ConversationStatus.HANDOFF
                )
                        .stream()
                        .map(this::toHandoffSession)
                        .toList()
        );
    }

    /**
     * 获取当前客服已接入的会话。
     *
     * 调用链：
     * Handoff 页面“我的接入会话” -> GET /api/admin/my-sessions
     * -> CurrentUserService.requireUser()
     * -> ConversationRepository.findByAgentIdAndStatus()。
     */
    @GetMapping("/my-sessions")
    public ResponseEntity<List<MySessionResponse>> getMySessions(Authentication authentication) {
        UserAccount user = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(conversationRepository.findByAgentIdAndStatus(user.getUsername(), Conversation.ConversationStatus.HANDOFF)
                .stream()
                .map(this::toMySession)
                .toList());
    }

    /**
     * 获取人工接入概览统计。
     *
     * 调用链：
     * Handoff 页面顶部统计 -> GET /api/admin/handoff-summary
     * -> UserAccountRepository 统计在线客服 -> ConversationRepository 统计待接入会话。
     */
    @GetMapping("/handoff-summary")
    public ResponseEntity<Map<String, Long>> getHandoffSummary() {
        long onlineAgents = userAccountRepository.countByRoleAndStatus(UserAccount.UserRole.AGENT, UserAccount.Status.ACTIVE);
        long pending = conversationRepository.findByHumanHandoffTrueAndStatusAndAgentIdIsNullOrderByHandoffTimeDesc(
                Conversation.ConversationStatus.HANDOFF
        ).size();
        return ResponseEntity.ok(Map.of("onlineAgents", onlineAgents, "pendingCount", pending));
    }

    /**
     * 当前客服接入待处理会话。
     *
     * 调用链：
     * Handoff 页面点击接入 -> POST /api/admin/conversations/{sessionId}/accept
     * -> CurrentUserService.requireUser()
     * -> ConversationRepository.save()
     * -> ConversationService.addMessage() 通知用户。
     */
    @PostMapping("/conversations/{sessionId}/accept")
    public ResponseEntity<Void> acceptConversation(@PathVariable String sessionId, Authentication authentication) {
        UserAccount user = currentUserService.requireUser(authentication);
        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));
        conversation.setAgentId(user.getUsername());
        conversationRepository.save(conversation);

        // 添加系统消息通知用户
        conversationService.addMessage(sessionId, Message.SenderType.SYSTEM,
                "人工客服已接入，现在可以开始对话了",
                null, null);

        log.info("客服接入会话: sessionId={}, agentId={}", sessionId, user.getUsername());
        return ResponseEntity.ok().build();
    }

    /**
     * 后台关闭会话。
     *
     * 调用链：
     * Handoff/会话管理页面 -> POST /api/admin/conversations/{sessionId}/close
     * -> ConversationRepository.save()。
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
     * 人工客服标记会话完成。
     *
     * 调用链：
     * Handoff 页面点击完成 -> POST /api/admin/conversations/{sessionId}/complete
     * -> ConversationRepository.save()。
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
     * 人工客服回复用户消息。
     *
     * 调用链：
     * Handoff 对话弹窗 -> POST /api/admin/conversations/{sessionId}/reply
     * -> ConversationService.addMessage(senderType=HUMAN)
     * -> MessageRepository.save()。
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

    private HandoffSessionResponse toHandoffSession(Conversation conversation) {
        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceAsc(conversation.getId());
        Message lastMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        long waitTime = conversation.getHandoffTime() == null
                ? 0
                : java.time.Duration.between(conversation.getHandoffTime(), LocalDateTime.now()).toSeconds();
        return new HandoffSessionResponse(
                conversation.getSessionId(),
                conversation.getUserName(),
                conversation.getPrimaryIntent(),
                conversation.getHandoffTime(),
                waitTime,
                lastMessage == null ? "" : lastMessage.getContent()
        );
    }

    private MySessionResponse toMySession(Conversation conversation) {
        long duration = conversation.getHandoffTime() == null
                ? 0
                : java.time.Duration.between(conversation.getHandoffTime(), LocalDateTime.now()).toSeconds();
        long unreadCount = messageRepository.findByConversationIdOrderBySequenceAsc(conversation.getId())
                .stream()
                .filter(message -> message.getSenderType() == Message.SenderType.USER)
                .filter(message -> Boolean.FALSE.equals(message.getIsRead()))
                .count();
        return new MySessionResponse(
                conversation.getSessionId(),
                conversation.getUserName(),
                conversation.getHandoffTime(),
                duration,
                unreadCount
        );
    }

    public record HandoffSessionResponse(
            String sessionId,
            String userName,
            Conversation.IntentType intentType,
            LocalDateTime handoffTime,
            Long waitTime,
            String lastMessage
    ) {
    }

    public record MySessionResponse(
            String sessionId,
            String userName,
            LocalDateTime acceptTime,
            Long duration,
            Long unreadCount
    ) {
    }
}
