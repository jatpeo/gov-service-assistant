package com.gov.assistant.service;

import com.gov.assistant.entity.Conversation;
import com.gov.assistant.entity.Message;
import com.gov.assistant.repository.KnowledgeItemRepository;
import com.gov.assistant.entity.document.KnowledgeChunk;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatLanguageModel chatModel;
    private final ConversationService conversationService;
    private final IntentRecognitionService intentRecognitionService;
    private final StandardAnswerService standardAnswerService;
    private final com.gov.assistant.service.document.DocumentKnowledgeRetrievalService documentKnowledgeRetrievalService;
    private final KnowledgeItemRepository knowledgeItemRepository;

    @Value("classpath:/prompt/system-prompt.txt")
    private Resource systemPromptResource;

    /**
     * 处理用户消息并返回AI回复
     */
    public ChatResponse processMessage(String sessionId, String userMessage) {
        long startTime = System.currentTimeMillis();

        // 0. 获取会话，检查是否已转人工
        Conversation conversation = conversationService.getConversationBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        // 先识别用户意图，后续各分支都复用
        IntentRecognitionService.IntentResult intentResult = intentRecognitionService.recognizeIntent(userMessage);
        List<Message> history = conversationService.getConversationMessages(sessionId);

        // 如果会话已转人工，直接保存消息并返回
        if (conversation.getStatus() == Conversation.ConversationStatus.HANDOFF) {
            // 保存用户消息
            conversationService.addMessage(sessionId, Message.SenderType.USER, userMessage,
                    intentResult.intentType(), intentResult.confidence());

            return ChatResponse.builder()
                    .content("")
                    .intentType(intentResult.intentType())
                    .intentConfidence(intentResult.confidence())
                    .knowledgeHit(false)
                    .needHandoff(true)
                    .build();
        }

        boolean explicitHandoffRequest = intentRecognitionService.containsHumanHandoffKeyword(userMessage)
                || (intentRecognitionService.isHandoffSelectionReply(userMessage)
                && isLatestSystemHandoffPrompt(history));

        // 2. 显式转人工，直接接入
        if (explicitHandoffRequest) {
            conversationService.addMessage(sessionId, Message.SenderType.USER, userMessage,
                    intentResult.intentType(), intentResult.confidence());

            conversationService.handoffToHuman(sessionId, null);

            // 添加系统消息
            String systemMessage = "正在为您转接人工客服，请稍候...";
            conversationService.addMessage(sessionId, Message.SenderType.SYSTEM,
                    systemMessage,
                    intentResult.intentType(), intentResult.confidence(),
                    Message.MessageType.TRANSFER);

            return ChatResponse.builder()
                    .content(systemMessage)
                    .intentType(intentResult.intentType())
                    .intentConfidence(intentResult.confidence())
                    .knowledgeHit(false)
                    .needHandoff(true)
                    .build();
        }

        // 2. 标准答案优先命中
        Optional<StandardAnswerService.StandardAnswerResult> standardAnswer =
                standardAnswerService.match(userMessage, intentResult.intentType());
        if (standardAnswer.isPresent()) {
            StandardAnswerService.StandardAnswerResult result = standardAnswer.get();

            conversationService.addMessage(sessionId, Message.SenderType.USER, userMessage,
                    intentResult.intentType(), intentResult.confidence());

            long responseTime = System.currentTimeMillis() - startTime;
            Message aiMessage = conversationService.addMessage(sessionId, Message.SenderType.AI,
                    result.response(), intentResult.intentType(), result.confidence());
            aiMessage.setResponseTime(responseTime);
            aiMessage.setKnowledgeHit(false);

            return ChatResponse.builder()
                    .content(result.response())
                    .intentType(result.intentType())
                    .intentConfidence(result.confidence())
                    .knowledgeHit(false)
                    .responseTime(responseTime)
                    .needHandoff(false)
                    .build();
        }

        // 3. 搜索文档知识片段
        List<KnowledgeChunk> documentChunks = searchDocumentKnowledgeBase(userMessage);
        if (!documentChunks.isEmpty()) {
            conversationService.addMessage(sessionId, Message.SenderType.USER, userMessage,
                    intentResult.intentType(), intentResult.confidence());

            String prompt = buildDocumentPrompt(userMessage, documentChunks, intentResult);
            String aiResponse = chatModel.generate(prompt);
            long responseTime = System.currentTimeMillis() - startTime;
            String chunkIdList = String.join(",",
                    documentChunks.stream()
                            .map(chunk -> String.valueOf(chunk.getId()))
                            .toList());

            Message aiMessage = conversationService.addMessage(sessionId, Message.SenderType.AI, aiResponse,
                    null, null);
            aiMessage.setResponseTime(responseTime);
            aiMessage.setKnowledgeHit(true);
            aiMessage.setKnowledgeSources(String.join(",", documentKnowledgeRetrievalService.formatChunkSources(documentChunks)));
            aiMessage.setKnowledgeSourceChunkIds(chunkIdList);

            return ChatResponse.builder()
                    .content(aiResponse)
                    .intentType(intentResult.intentType())
                    .intentConfidence(intentResult.confidence())
                    .knowledgeHit(true)
                    .knowledgeSources(documentKnowledgeRetrievalService.formatChunkSources(documentChunks))
                    .responseTime(responseTime)
                    .build();
        }

        // 4. 搜索手工知识库
        List<com.gov.assistant.entity.KnowledgeItem> knowledgeItems = searchKnowledgeBase(userMessage);
        boolean knowledgeHit = !knowledgeItems.isEmpty();

        // 先保存用户消息
        conversationService.addMessage(sessionId, Message.SenderType.USER, userMessage,
                intentResult.intentType(), intentResult.confidence());

        if (!knowledgeHit) {
            String fallbackMessage = buildNoKnowledgeFallbackMessage();
            conversationService.addMessage(sessionId, Message.SenderType.SYSTEM,
                    fallbackMessage,
                    intentResult.intentType(), intentResult.confidence(),
                    Message.MessageType.SYSTEM);

            long responseTime = System.currentTimeMillis() - startTime;
            return ChatResponse.builder()
                    .content(fallbackMessage)
                    .intentType(intentResult.intentType())
                    .intentConfidence(intentResult.confidence())
                    .knowledgeHit(false)
                    .responseTime(responseTime)
                    .needHandoff(false)
                    .build();
        }

        // 5. 构建AI提示
        String prompt = buildPrompt(userMessage, knowledgeItems, intentResult);

        // 6. 调用AI模型
        String aiResponse = chatModel.generate(prompt);

        long responseTime = System.currentTimeMillis() - startTime;

        // 7. 保存AI回复
        Message aiMessage = conversationService.addMessage(sessionId, Message.SenderType.AI, aiResponse,
                null, null);
        aiMessage.setResponseTime(responseTime);
        aiMessage.setKnowledgeHit(knowledgeHit);
        if (knowledgeHit && !knowledgeItems.isEmpty()) {
            aiMessage.setKnowledgeSources(knowledgeItems.get(0).getItemCode());
        }

        return ChatResponse.builder()
                .content(aiResponse)
                .intentType(intentResult.intentType())
                .intentConfidence(intentResult.confidence())
                .knowledgeHit(knowledgeHit)
                .knowledgeSources(knowledgeItems.stream()
                        .map(com.gov.assistant.entity.KnowledgeItem::getQuestion)
                        .toList())
                .responseTime(responseTime)
                .build();
    }

    private List<KnowledgeChunk> searchDocumentKnowledgeBase(String query) {
        return documentKnowledgeRetrievalService.searchPublishedChunks(query);
    }

    /**
     * 是否最近一条系统消息在提示人工接入选项
     */
    private boolean isLatestSystemHandoffPrompt(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }

        Message lastMessage = history.get(history.size() - 1);
        if (lastMessage.getSenderType() != Message.SenderType.SYSTEM) {
            return false;
        }

        String content = lastMessage.getContent();
        return content != null && (content.contains("是否接入人工客服")
                || content.contains("转人工客服")
                || content.contains("换个话题"));
    }

    /**
     * 知识库未命中时的兜底回复
     */
    private String buildNoKnowledgeFallbackMessage() {
        return """
                抱歉，暂时没有找到相关内容。

                您可以选择：
                1. 换个话题
                2. 转人工客服

                如需转人工，请直接输入“转人工”或回复“2”。
                """.trim();
    }

    /**
     * 搜索知识库
     */
    private List<com.gov.assistant.entity.KnowledgeItem> searchKnowledgeBase(String query) {
        // 提取关键词（简单实现，可优化）
        String keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<com.gov.assistant.entity.KnowledgeItem> results = knowledgeItemRepository.searchByKeyword(keywords);

        // 限制返回数量
        return results.stream()
                .limit(3)
                .toList();
    }

    /**
     * 提取关键词
     */
    private String extractKeywords(String text) {
        // 移除常见停用词
        String[] stopWords = {"的", "了", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这"};
        String result = text;
        for (String stopWord : stopWords) {
            result = result.replace(stopWord, "");
        }
        return result.trim();
    }

    /**
     * 构建AI提示
     */
    private String buildPrompt(String userMessage,
                               List<com.gov.assistant.entity.KnowledgeItem> knowledgeItems,
                               IntentRecognitionService.IntentResult intentResult) {
        StringBuilder prompt = new StringBuilder();

        // 系统提示
        try {
            String systemPrompt = new String(systemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            prompt.append(systemPrompt).append("\n\n");
        } catch (IOException e) {
            log.warn("无法加载系统提示文件，使用默认提示");
            prompt.append("你是国有资本监管平台的智能客服助手，专业、严谨、高效地回答用户问题。").append("\n\n");
        }

        // 意图信息
        prompt.append("【用户意图】").append(intentResult.intentType()).append("\n");
        prompt.append("【置信度】").append(String.format("%.2f", intentResult.confidence())).append("\n\n");

        // 知识库参考
        if (!knowledgeItems.isEmpty()) {
            prompt.append("【知识库参考】\n");
            for (int i = 0; i < knowledgeItems.size(); i++) {
                com.gov.assistant.entity.KnowledgeItem item = knowledgeItems.get(i);
                prompt.append(i + 1).append(". ").append(item.getQuestion()).append("\n");
                prompt.append("   ").append(item.getAnswer()).append("\n\n");
            }
        }

        // 用户问题
        prompt.append("【用户问题】").append(userMessage).append("\n\n");
        prompt.append("请基于以上信息，给出专业、准确的回答。如果知识库中没有相关信息，请明确告知用户，并建议转人工服务。");

        return prompt.toString();
    }

    private String buildDocumentPrompt(String userMessage,
                                       List<KnowledgeChunk> chunks,
                                       IntentRecognitionService.IntentResult intentResult) {
        StringBuilder prompt = new StringBuilder();

        try {
            String systemPrompt = new String(systemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            prompt.append(systemPrompt).append("\n\n");
        } catch (IOException e) {
            log.warn("无法加载系统提示文件，使用默认提示");
            prompt.append("你是国有资本监管平台的智能客服助手，专业、严谨、高效地回答用户问题。").append("\n\n");
        }

        prompt.append("【用户意图】").append(intentResult.intentType()).append("\n");
        prompt.append("【置信度】").append(String.format("%.2f", intentResult.confidence())).append("\n\n");
        prompt.append("【文档知识片段】\n");
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            prompt.append(i + 1).append(". ");
            prompt.append(Optional.ofNullable(chunk.getSourceFileName()).orElse("document")).append(" | ");
            if (chunk.getPageNo() != null) {
                prompt.append("第").append(chunk.getPageNo()).append("页 | ");
            }
            if (chunk.getImageIndex() != null) {
                prompt.append("图片").append(chunk.getImageIndex() + 1).append(" | ");
            }
            if (chunk.getChunkTitle() != null) {
                prompt.append(chunk.getChunkTitle()).append("\n");
            } else {
                prompt.append("知识片段").append("\n");
            }
            prompt.append(chunk.getContent()).append("\n");
            if (chunk.getOcrText() != null && !chunk.getOcrText().isBlank()) {
                prompt.append("OCR: ").append(chunk.getOcrText()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("【用户问题】").append(userMessage).append("\n\n");
        prompt.append("请基于上述文档知识片段作答，优先引用原文信息。若片段不足，请明确说明并建议转人工服务。");
        return prompt.toString();
    }

    /**
     * 创建新会话
     */
    public String createConversation(String userId, String userName) {
        Conversation conversation = conversationService.createConversation(userId, userName);
        return conversation.getSessionId();
    }

    /**
     * 关闭会话
     */
    public void closeConversation(String sessionId, Integer satisfactionScore) {
        conversationService.closeConversation(sessionId, satisfactionScore);
    }

    /**
     * 获取会话消息历史
     */
    public List<Message> getConversationHistory(String sessionId) {
        return conversationService.getConversationMessages(sessionId);
    }

    /**
     * 聊天响应
     */
    public record ChatResponse(String content,
                               Conversation.IntentType intentType,
                               Double intentConfidence,
                               boolean knowledgeHit,
                               List<String> knowledgeSources,
                               Long responseTime,
                               boolean needHandoff) {
        public ChatResponse {
        }

        public static ChatResponseBuilder builder() {
            return new ChatResponseBuilder();
        }

        public static class ChatResponseBuilder {
            private String content;
            private Conversation.IntentType intentType;
            private Double intentConfidence;
            private boolean knowledgeHit;
            private List<String> knowledgeSources;
            private Long responseTime;
            private boolean needHandoff;

            public ChatResponseBuilder content(String content) {
                this.content = content;
                return this;
            }

            public ChatResponseBuilder intentType(Conversation.IntentType intentType) {
                this.intentType = intentType;
                return this;
            }

            public ChatResponseBuilder intentConfidence(Double intentConfidence) {
                this.intentConfidence = intentConfidence;
                return this;
            }

            public ChatResponseBuilder knowledgeHit(boolean knowledgeHit) {
                this.knowledgeHit = knowledgeHit;
                return this;
            }

            public ChatResponseBuilder knowledgeSources(List<String> knowledgeSources) {
                this.knowledgeSources = knowledgeSources;
                return this;
            }

            public ChatResponseBuilder responseTime(Long responseTime) {
                this.responseTime = responseTime;
                return this;
            }

            public ChatResponseBuilder needHandoff(boolean needHandoff) {
                this.needHandoff = needHandoff;
                return this;
            }

            public ChatResponse build() {
                return new ChatResponse(content, intentType, intentConfidence, knowledgeHit,
                        knowledgeSources, responseTime, needHandoff);
            }
        }
    }
}
