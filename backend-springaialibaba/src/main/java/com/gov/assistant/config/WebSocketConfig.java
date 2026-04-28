package com.gov.assistant.config;

import com.gov.assistant.service.realtime.ConversationRealtimeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置。
 *
 * 调用链：
 * 浏览器 WebSocket 连接 -> /ws/conversation
 * -> ConversationRealtimeWebSocketHandler
 * -> ConversationRealtimeService 广播消息。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ConversationRealtimeWebSocketHandler conversationRealtimeWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(conversationRealtimeWebSocketHandler, "/ws/conversation")
                .setAllowedOriginPatterns("*");
    }
}
