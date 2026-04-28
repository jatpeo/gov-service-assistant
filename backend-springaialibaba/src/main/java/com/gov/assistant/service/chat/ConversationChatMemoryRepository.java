package com.gov.assistant.service.chat;

import com.gov.assistant.entity.chat.Message;
import com.gov.assistant.repository.chat.ConversationRepository;
import com.gov.assistant.repository.chat.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
/**
 * Spring AI ChatMemoryRepository 适配器。
 *
 * 调用链：
 * ChatService.callAiWithMemory()
 * -> MessageChatMemoryAdvisor
 * -> ChatMemory
 * -> ConversationChatMemoryRepository 从业务消息表读取历史上下文。
 */
public class ConversationChatMemoryRepository implements ChatMemoryRepository {

    private static final int MAX_MEMORY_MESSAGES = 20;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * 返回系统中已有会话 ID，供 ChatMemory 识别可用会话。
     *
     * 调用链：
     * Spring AI ChatMemory -> findConversationIds()
     * -> ConversationRepository.findAll()。
     */
    @Override
    public List<String> findConversationIds() {
        return conversationRepository.findAll()
                .stream()
                .map(com.gov.assistant.entity.chat.Conversation::getSessionId)
                .toList();
    }

    /**
     * 按会话读取最近的用户/AI消息，转换成 Spring AI 消息对象。
     *
     * 调用链：
     * MessageChatMemoryAdvisor -> findByConversationId()
     * -> MessageRepository.findByConversationIdOrderBySequenceAsc()
     * -> toSpringAiMessage()。
     */
    @Override
    public List<org.springframework.ai.chat.messages.Message> findByConversationId(String conversationId) {
        return conversationRepository.findBySessionId(conversationId)
                .map(conversation -> messageRepository.findByConversationIdOrderBySequenceAsc(conversation.getId())
                        .stream()
                        .filter(this::isMemoryMessage)
                        .skip(Math.max(0, countMemoryMessages(conversation.getId()) - MAX_MEMORY_MESSAGES))
                        .map(this::toSpringAiMessage)
                        .flatMap(Optional::stream)
                        .toList())
                .orElse(List.of());
    }

    /**
     * Spring AI 写入记忆的回调。
     *
     * 调用链：
     * MessageChatMemoryAdvisor -> saveAll()。
     * 业务侧统一通过 ConversationService.addMessage() 持久化，因此这里不重复写库。
     */
    @Override
    public void saveAll(String conversationId, List<org.springframework.ai.chat.messages.Message> messages) {
        // The application persists messages through ConversationService. This repository reads that
        // durable history and lets MessageChatMemoryAdvisor assemble the Spring AI memory window.
    }

    /**
     * 清理指定会话记忆的回调。
     *
     * 调用链：
     * ChatMemory -> deleteByConversationId()。
     * 当前系统保留历史审计数据，因此真实删除由 ConversationService.deleteConversation() 控制。
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        // Conversation records are retained for audit/history, so memory is cleared with the conversation lifecycle.
    }

    private long countMemoryMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderBySequenceAsc(conversationId)
                .stream()
                .filter(this::isMemoryMessage)
                .count();
    }

    private boolean isMemoryMessage(Message message) {
        return message.getSenderType() == Message.SenderType.USER
                || message.getSenderType() == Message.SenderType.AI;
    }

    private Optional<org.springframework.ai.chat.messages.Message> toSpringAiMessage(Message message) {
        if (message.getSenderType() == Message.SenderType.USER) {
            return Optional.of(new UserMessage(message.getContent()));
        }
        if (message.getSenderType() == Message.SenderType.AI) {
            return Optional.of(new AssistantMessage(message.getContent()));
        }
        return Optional.empty();
    }
}
