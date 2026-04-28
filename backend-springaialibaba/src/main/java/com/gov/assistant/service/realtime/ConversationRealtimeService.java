package com.gov.assistant.service.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.assistant.entity.chat.Conversation;
import com.gov.assistant.entity.chat.Message;
import com.gov.assistant.entity.auth.UserAccount;
import com.gov.assistant.repository.chat.ConversationRepository;
import com.gov.assistant.repository.auth.UserAccountRepository;
import com.gov.assistant.service.auth.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 会话实时推送服务。
 *
 * 调用链：
 * ConversationService.addMessage() / 会话状态变更
 * -> ConversationRealtimeService.broadcastMessage()/broadcastConversationUpdate()
 * -> ConversationRealtimeWebSocketHandler -> 浏览器 WebSocket。
 */
public class ConversationRealtimeService {

    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final UserAccountRepository userAccountRepository;
    private final ConversationRepository conversationRepository;

    private final Map<String, Set<WebSocketSession>> sessionSubscribers = new ConcurrentHashMap<>();

    /**
     * 注册一个会话订阅者。
     *
     * 调用链：
     * WebSocket 建连成功 -> register()
     * -> 记住当前 sessionId 对应的浏览器连接。
     */
    public void register(String sessionId, WebSocketSession webSocketSession) {
        sessionSubscribers.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet())
                .add(webSocketSession);
    }

    /**
     * 移除一个会话订阅者。
     *
     * 调用链：
     * WebSocket 关闭/异常 -> unregister()
     * -> 清理 sessionId 下已断开的连接。
     */
    public void unregister(String sessionId, WebSocketSession webSocketSession) {
        Set<WebSocketSession> sessions = sessionSubscribers.get(sessionId);
        if (sessions == null) {
            return;
        }
        sessions.remove(webSocketSession);
        if (sessions.isEmpty()) {
            sessionSubscribers.remove(sessionId);
        }
    }

    /**
     * 广播一条消息到同一会话的所有在线客户端。
     *
     * 调用链：
     * ConversationService.addMessage()
     * -> broadcastMessage()
     * -> 消息 JSON 推送到用户端/人工客服端。
     */
    public void broadcastMessage(Message message) {
        if (message == null || message.getConversation() == null) {
            return;
        }

        String sessionId = message.getConversation().getSessionId();
        send(sessionId, realtimeEvent("message", sessionId, toMessagePayload(message)));
    }

    /**
     * 广播会话状态变化，供前端做列表刷新或状态提示。
     *
     * 调用链：
     * ConversationService.handoffToHuman()/closeConversation()
     * -> broadcastConversationUpdate()
     * -> 前端可据此刷新列表或状态展示。
     */
    public void broadcastConversationUpdate(Conversation conversation, String eventType, String note) {
        if (conversation == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", conversation.getStatus());
        payload.put("humanHandoff", conversation.getHumanHandoff());
        payload.put("agentId", conversation.getAgentId());
        payload.put("note", note);

        send(conversation.getSessionId(), realtimeEvent(eventType, conversation.getSessionId(), payload));
    }

    /**
     * 校验 WebSocket 连接是否允许订阅目标会话。
     *
     * 调用链：
     * ConversationRealtimeWebSocketHandler.afterConnectionEstablished()
     * -> validateSubscription() -> TokenService.parseToken()
     * -> ConversationService.getConversationBySessionId()。
     */
    public boolean validateSubscription(String sessionId, String token) {
        Optional<TokenService.TokenClaims> claims = tokenService.parseToken(token);
        if (claims.isEmpty()) {
            return false;
        }

        Optional<UserAccount> userAccount = userAccountRepository.findByUsername(claims.get().username());
        if (userAccount.isEmpty() || userAccount.get().getStatus() != UserAccount.Status.ACTIVE) {
            return false;
        }

        UserAccount user = userAccount.get();
        if (user.getRole() == UserAccount.UserRole.USER) {
            return conversationRepository.findBySessionId(sessionId)
                    .map(conversation -> user.getUsername().equals(conversation.getUserId()))
                    .orElse(false);
        }

        return true;
    }

    private void send(String sessionId, ConversationRealtimeEvent event) {
        Set<WebSocketSession> sessions = sessionSubscribers.getOrDefault(sessionId, Collections.emptySet());
        if (sessions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("序列化实时事件失败: {}", e.getMessage());
            return;
        }

        for (WebSocketSession session : sessions) {
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                log.warn("推送实时消息失败: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }
    }

    private ConversationRealtimeEvent realtimeEvent(String eventType, String sessionId, Object payload) {
        return new ConversationRealtimeEvent(eventType, sessionId, payload);
    }

    private Map<String, Object> toMessagePayload(Message message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", message.getId());
        payload.put("sessionId", Optional.ofNullable(message.getConversation()).map(Conversation::getSessionId).orElse(null));
        payload.put("senderType", message.getSenderType());
        payload.put("content", message.getContent());
        payload.put("createdAt", message.getCreatedAt());
        payload.put("messageType", message.getMessageType());
        payload.put("intentType", message.getIntentType());
        payload.put("intentConfidence", message.getIntentConfidence());
        payload.put("knowledgeHit", message.getKnowledgeHit());
        payload.put("knowledgeSources", message.getKnowledgeSources());
        payload.put("knowledgeSourceChunkIds", message.getKnowledgeSourceChunkIds());
        payload.put("responseTime", message.getResponseTime());
        payload.put("isRead", message.getIsRead());
        return payload;
    }

    public record ConversationRealtimeEvent(String eventType, String sessionId, Object payload) {
    }
}
