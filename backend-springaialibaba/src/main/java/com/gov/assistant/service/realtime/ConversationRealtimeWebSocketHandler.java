package com.gov.assistant.service.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * 会话实时消息 WebSocket 处理器。
 *
 * 调用链：
 * 浏览器 WebSocket 连接 /ws/conversation
 * -> afterConnectionEstablished()
 * -> ConversationRealtimeService.register()
 * -> ConversationService.addMessage() 后广播消息到当前连接。
 */
public class ConversationRealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_SESSION_ID = "sessionId";

    private final ConversationRealtimeService conversationRealtimeService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        String token = extractToken(session);
        if (sessionId == null || token == null || !conversationRealtimeService.validateSubscription(sessionId, token)) {
            log.warn("WebSocket 订阅校验失败: sessionId={}", sessionId);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        session.getAttributes().put(ATTR_SESSION_ID, sessionId);
        conversationRealtimeService.register(sessionId, session);
        log.info("WebSocket 连接建立: sessionId={}", sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = (String) session.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId != null) {
            conversationRealtimeService.unregister(sessionId, session);
        }
        log.info("WebSocket 连接关闭: sessionId={}, status={}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = (String) session.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId != null) {
            conversationRealtimeService.unregister(sessionId, session);
        }
        log.warn("WebSocket 传输异常: sessionId={}, error={}", sessionId, exception.getMessage());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
            // 连接已经失效时直接忽略。
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 当前实时通道只负责服务端推送，客户端不需要通过 WebSocket 反向发送消息。
        // 若前端意外发送文本，直接忽略即可。
    }

    private String extractSessionId(WebSocketSession session) {
        return UriComponentsBuilder.fromUri(Objects.requireNonNull(session.getUri()))
                .build()
                .getQueryParams()
                .getFirst("sessionId");
    }

    private String extractToken(WebSocketSession session) {
        return UriComponentsBuilder.fromUri(Objects.requireNonNull(session.getUri()))
                .build()
                .getQueryParams()
                .getFirst("token");
    }
}
