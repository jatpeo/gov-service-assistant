package com.gov.assistant.config;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * Spring AI Alibaba 多轮对话记忆配置。
 *
 * 调用链：
 * Spring 容器启动 -> ChatMemoryConfig
 * -> ConversationChatMemoryRepository 作为持久化源
 * -> ChatService.callAiWithMemory() 注入 MessageChatMemoryAdvisor。
 */
public class ChatMemoryConfig {

    /**
     * 创建窗口式会话记忆，只保留最近 maxMessages 条消息参与上下文。
     *
     * 调用链：
     * ChatService.callAiWithMemory()
     * -> MessageChatMemoryAdvisor
     * -> ChatMemory
     * -> ConversationChatMemoryRepository。
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * 创建 ChatClient 使用的记忆 Advisor。
     *
     * 调用链：
     * ChatService.callAiWithMemory()
     * -> ChatClient.builder(...).defaultAdvisors(messageChatMemoryAdvisor)。
     */
    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
