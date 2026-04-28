package com.gov.assistant.service.chat;

import com.gov.assistant.entity.chat.Conversation;
import com.gov.assistant.entity.chat.Message;
import com.gov.assistant.entity.document.KnowledgeItem;
import com.gov.assistant.repository.document.KnowledgeItemRepository;
import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.service.rag.RagKnowledgeService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 智能客服主编排服务。
 *
 * 调用链：
 * ChatController.sendMessage() -> processMessage()
 * -> IntentRecognitionService/StandardAnswerService/RagKnowledgeService/DocumentKnowledgeRetrievalService
 * -> ChatClient + MessageChatMemoryAdvisor -> ConversationService 保存消息。
 */
public class ChatService {

    private final ChatModel chatModel;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final ConversationService conversationService;
    private final IntentRecognitionService intentRecognitionService;
    private final StandardAnswerService standardAnswerService;
    private final RagKnowledgeService ragKnowledgeService;
    private final com.gov.assistant.service.document.DocumentKnowledgeRetrievalService documentKnowledgeRetrievalService;
    private final KnowledgeItemRepository knowledgeItemRepository;

    @Value("classpath:/prompt/system-prompt.txt")
    private Resource systemPromptResource;

    /**
     * 处理用户消息并返回 AI 回复，是前台问答的核心链路。
     *
     * 调用链：
     * ChatController.sendMessage() -> processMessage()
     * -> 校验会话状态 -> 意图识别 -> 转人工判断 -> 标准答案
     * -> RAG 语义召回 -> 文档关键词兜底 -> 手工知识库兜底
     * -> ChatClient 调用大模型 -> ConversationService 落库。
     */
    public ChatResponse processMessage(String sessionId, String userMessage) {
        return processMessageInternal(sessionId, userMessage, null);
    }

    /**
     * 处理用户消息并通过回调输出流式增量文本。
     *
     * 调用链：
     * ChatController.streamMessage() -> processMessageStream()
     * -> processMessageInternal(..., deltaConsumer)
     * -> ChatClient.stream()/本地文本分片 -> SSE delta。
     */
    public ChatResponse processMessageStream(String sessionId, String userMessage, Consumer<String> deltaConsumer) {
        return processMessageInternal(sessionId, userMessage, deltaConsumer);
    }

    private ChatResponse processMessageInternal(String sessionId, String userMessage, Consumer<String> deltaConsumer) {
        long startTime = System.currentTimeMillis();

        // 0. 先取会话。
        // 这里是整条链路的入口前置校验：会话不存在直接失败，避免后续消息落到空会话。
        Conversation conversation = conversationService.getConversationBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        // 1. 先做意图识别。
        // 后面的标准答案、兜底提示、统计埋点、人工接入判断都会复用这个结果。
        IntentRecognitionService.IntentResult intentResult = intentRecognitionService.recognizeIntent(userMessage);
        List<Message> history = conversationService.getConversationMessages(sessionId);

        // 2. 如果会话已经在人工模式，AI 不再继续回答，只记录用户消息并让前端维持人工对话状态。
        if (conversation.getStatus() == Conversation.ConversationStatus.HANDOFF) {
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

        // 3. 用户明确要求转人工时，先落消息，再把会话状态切成 HANDOFF，并给出系统提示。
        if (explicitHandoffRequest) {
            conversationService.addMessage(sessionId, Message.SenderType.USER, userMessage,
                    intentResult.intentType(), intentResult.confidence());

            conversationService.handoffToHuman(sessionId, null);

            String systemMessage = "正在为您转接人工客服，请稍候...";
            conversationService.addMessage(sessionId, Message.SenderType.SYSTEM,
                    systemMessage,
                    intentResult.intentType(), intentResult.confidence(),
                    Message.MessageType.TRANSFER);
            emitTextChunks(systemMessage, deltaConsumer);

            return ChatResponse.builder()
                    .content(systemMessage)
                    .intentType(intentResult.intentType())
                    .intentConfidence(intentResult.confidence())
                    .knowledgeHit(false)
                    .needHandoff(true)
                    .build();
        }

        // 4. 标准答案优先。
        // 这一层是最确定、最稳定的回答，不需要调用大模型。
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
            emitTextChunks(result.response(), deltaConsumer);

            return ChatResponse.builder()
                    .content(result.response())
                    .intentType(result.intentType())
                    .intentConfidence(result.confidence())
                    .knowledgeHit(false)
                    .responseTime(responseTime)
                    .needHandoff(false)
                    .build();
        }

        // 5. RAG 语义召回。
        // 先向量检索，再把召回内容喂给大模型生成更自然的回复。
        List<RagKnowledgeService.RagHit> ragHits = ragKnowledgeService.search(userMessage);
        if (!ragHits.isEmpty()) {
            String prompt = buildRagPrompt(userMessage, ragHits, intentResult);
            String aiResponse = callAiWithMemory(sessionId, prompt, deltaConsumer);
            long responseTime = System.currentTimeMillis() - startTime;

            // 先保存用户消息，再保存 AI 回复，便于历史回放和会话记忆保持一致。
            conversationService.addMessage(sessionId, Message.SenderType.USER, userMessage,
                    intentResult.intentType(), intentResult.confidence());

            Message aiMessage = conversationService.addMessage(sessionId, Message.SenderType.AI, aiResponse,
                    null, null);
            aiMessage.setResponseTime(responseTime);
            aiMessage.setKnowledgeHit(true);
            aiMessage.setKnowledgeSources(formatRagSources(ragHits));
            aiMessage.setKnowledgeSourceChunkIds(formatRagChunkIds(ragHits));

            return ChatResponse.builder()
                    .content(aiResponse)
                    .intentType(intentResult.intentType())
                    .intentConfidence(intentResult.confidence())
                    .knowledgeHit(true)
                    .knowledgeSources(ragHits.stream()
                            .map(RagKnowledgeService.RagHit::sourceName)
                            .distinct()
                            .toList())
                    .responseTime(responseTime)
                    .build();
        }

        // 6. 文档片段关键词兜底。
        // RAG 没命中时，再用结构化文档片段做一次更保守的检索。
        List<KnowledgeChunk> documentChunks = searchDocumentKnowledgeBase(userMessage);
        if (!documentChunks.isEmpty()) {
            String prompt = buildDocumentPrompt(userMessage, documentChunks, intentResult);
            String aiResponse = callAiWithMemory(sessionId, prompt, deltaConsumer);
            long responseTime = System.currentTimeMillis() - startTime;
            String chunkIdList = String.join(",",
                    documentChunks.stream()
                            .map(chunk -> String.valueOf(chunk.getId()))
                            .toList());

            conversationService.addMessage(sessionId, Message.SenderType.USER, userMessage,
                    intentResult.intentType(), intentResult.confidence());

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

        // 7. 手工知识库关键词兜底。
        // 这是最后一个知识命中层，仍然命中时继续用大模型润色成自然语言。
        List<KnowledgeItem> knowledgeItems = searchKnowledgeBase(userMessage);
        boolean knowledgeHit = !knowledgeItems.isEmpty();

        if (!knowledgeHit) {
            // 8. 仍然没有命中时，给用户一个更人性化的兜底回复，而不是直接说“没找到”。
            conversationService.addMessage(sessionId, Message.SenderType.USER, userMessage,
                    intentResult.intentType(), intentResult.confidence());

            String fallbackMessage = buildNoKnowledgeFallbackMessage(intentResult.intentType());
            conversationService.addMessage(sessionId, Message.SenderType.AI,
                    fallbackMessage,
                    intentResult.intentType(), intentResult.confidence(),
                    Message.MessageType.TEXT);
            emitTextChunks(fallbackMessage, deltaConsumer);

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

        // 9. 手工知识库命中后，构造提示词再交给大模型组织语言。
        String prompt = buildPrompt(userMessage, knowledgeItems, intentResult);

        // 10. 调用大模型。
        String aiResponse = callAiWithMemory(sessionId, prompt, deltaConsumer);

        long responseTime = System.currentTimeMillis() - startTime;

        // 11. 落库用户消息。
        conversationService.addMessage(sessionId, Message.SenderType.USER, userMessage,
                intentResult.intentType(), intentResult.confidence());

        // 12. 落库 AI 回复。
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
                        .map(KnowledgeItem::getQuestion)
                        .toList())
                .responseTime(responseTime)
                .build();
    }

    /**
     * 文档知识库关键词兜底检索。
     *
     * 调用链：
     * processMessage() 在 RAG 未命中时调用 -> DocumentKnowledgeRetrievalService.searchPublishedChunks()。
     */
    private List<KnowledgeChunk> searchDocumentKnowledgeBase(String query) {
        return documentKnowledgeRetrievalService.searchPublishedChunks(query);
    }

    /**
     * 带会话记忆调用大模型。
     *
     * 调用链：
     * processMessage()/知识命中分支 -> callAiWithMemory()
     * -> ChatClient -> MessageChatMemoryAdvisor
     * -> ConversationChatMemoryRepository 读取历史消息。
     */
    private String callAiWithMemory(String sessionId, String prompt) {
        return callAiWithMemory(sessionId, prompt, null);
    }

    private String callAiWithMemory(String sessionId, String prompt, Consumer<String> deltaConsumer) {
        if (deltaConsumer == null) {
            // 非流式模式：直接阻塞获取完整回复。
            return callAiWithMemoryBlocking(sessionId, prompt);
        }

        StringBuilder content = new StringBuilder();
        // 流式模式：边生成边向前端推送 delta，最后再拼成完整内容用于落库。
        ChatClient.builder(chatModel)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build()
                .prompt()
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, sessionId))
                .user(prompt)
                .stream()
                .content()
                .doOnNext(delta -> {
                    if (delta != null && !delta.isEmpty()) {
                        content.append(delta);
                        deltaConsumer.accept(delta);
                    }
                })
                .blockLast();
        return content.toString();
    }

    private String callAiWithMemoryBlocking(String sessionId, String prompt) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build()
                .prompt()
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, sessionId))
                .user(prompt)
                .call()
                .content();
    }

    private void emitTextChunks(String content, Consumer<String> deltaConsumer) {
        if (deltaConsumer == null || content == null || content.isEmpty()) {
            return;
        }
        // 本地文本也拆成小块推送，前端流式体验会更一致。
        int chunkSize = 12;
        for (int start = 0; start < content.length(); start += chunkSize) {
            int end = Math.min(content.length(), start + chunkSize);
            deltaConsumer.accept(content.substring(start, end));
        }
    }

    private String formatRagSources(List<RagKnowledgeService.RagHit> ragHits) {
        return String.join(",", ragHits.stream()
                .map(hit -> hit.sourceType() + ":" + hit.sourceName())
                .distinct()
                .toList());
    }

    private String formatRagChunkIds(List<RagKnowledgeService.RagHit> ragHits) {
        return String.join(",", ragHits.stream()
                .filter(hit -> "DOCUMENT_CHUNK".equals(hit.sourceType()))
                .map(RagKnowledgeService.RagHit::sourceId)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .toList());
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
    private String buildNoKnowledgeFallbackMessage(Conversation.IntentType intentType) {
        // 这里保持提示尽量简短，避免首屏系统消息过长，把“帮助引导”做成“说明书”。
        if (intentType == null || intentType == Conversation.IntentType.OTHER) {
            return """
                    我主要协助平台相关事项。

                    如果您想咨询政策、办理业务、查询进度、处理账号权限或了解系统使用，我可以继续帮您梳理。

                    需要人工协助时，直接输入“转人工”即可。
                    """.trim();
        }

        return switch (intentType) {
            case POLICY_CONSULTATION -> """
                    我可以帮您继续梳理政策咨询。

                    您可以补充政策名称、业务场景、适用对象或想确认的条款，我会尽量按适用范围、依据和注意事项来说明。

                    需要人工核实时，输入“转人工”即可。
                    """.trim();
            case BUSINESS_PROCESSING -> """
                    我可以继续协助您梳理业务办理流程。

                    您可以补充要办理的事项、所属单位、当前环节或遇到的卡点，我会按办理条件、材料、步骤和注意事项来说明。

                    需要人工协助时，输入“转人工”即可。
                    """.trim();
            case PROGRESS_QUERY -> """
                    我可以帮您判断进度查询需要哪些信息。

                    通常请补充业务类型、申报单位、工单编号或提交时间中的任一项，我会继续引导您查询。

                    需要人工进一步核实时，输入“转人工”即可。
                    """.trim();
            case TECHNICAL_SUPPORT -> """
                    我可以先帮您做基础排查。

                    请补充问题现象、出现时间、页面提示内容和所在功能模块，我会按可能原因、处理步骤和下一步来协助。

                    如果影响正常办理，输入“转人工”即可。
                    """.trim();
            case ACCOUNT_PERMISSION -> """
                    我可以协助您梳理账号权限问题。

                    请补充账号名称、所属单位、需要访问的功能模块和页面提示，我会帮您判断是账号未开通、角色未分配还是权限配置异常。

                    需要后台核实时，输入“转人工”即可。
                    """.trim();
            case COMPLAINT_SUGGESTION -> """
                    我已收到您的投诉或建议诉求。

                    为便于受理，请补充涉及事项、发生时间、相关单位或页面截图，以及您希望解决的问题。

                    也可以直接输入“转人工”，我会为您转接人工客服继续处理。
                    """.trim();
            case OTHER -> """
                    我主要协助平台相关事项。

                    如果您愿意，可以继续补充更具体的信息，我会帮您梳理；需要人工协助时，直接输入“转人工”即可。
                    """.trim();
        };
    }

    /**
     * 搜索知识库
     */
    /**
     * 手工知识库关键词兜底检索。
     *
     * 调用链：
     * processMessage() 在 RAG 和文档片段均未命中时调用
     * -> KnowledgeItemRepository.searchByKeyword()。
     */
    private List<KnowledgeItem> searchKnowledgeBase(String query) {
        // 提取关键词（简单实现，可优化）
        String keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<KnowledgeItem> results = knowledgeItemRepository.searchByKeyword(keywords);

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
                               List<KnowledgeItem> knowledgeItems,
                               IntentRecognitionService.IntentResult intentResult) {
        StringBuilder prompt = new StringBuilder();

        // 系统提示
        try {
            String systemPrompt = new String(systemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            prompt.append(systemPrompt).append("\n\n");
        } catch (IOException e) {
            log.warn("无法加载系统提示文件，使用默认提示");
            prompt.append("你是智能客服助手，专业、严谨、高效地回答用户问题。").append("\n\n");
        }

        // 意图信息
        prompt.append("【用户意图】").append(intentResult.intentType()).append("\n");
        prompt.append("【置信度】").append(String.format("%.2f", intentResult.confidence())).append("\n\n");

        // 知识库参考
        if (!knowledgeItems.isEmpty()) {
            prompt.append("【知识库参考】\n");
            for (int i = 0; i < knowledgeItems.size(); i++) {
                KnowledgeItem item = knowledgeItems.get(i);
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
            prompt.append("你是智能客服助手，专业、严谨、高效地回答用户问题。").append("\n\n");
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

    private String buildRagPrompt(String userMessage,
                                  List<RagKnowledgeService.RagHit> ragHits,
                                  IntentRecognitionService.IntentResult intentResult) {
        StringBuilder prompt = new StringBuilder();

        try {
            String systemPrompt = new String(systemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            prompt.append(systemPrompt).append("\n\n");
        } catch (IOException e) {
            log.warn("无法加载系统提示文件，使用默认提示");
            prompt.append("你是智能客服助手，专业、严谨、高效地回答用户问题。").append("\n\n");
        }

        prompt.append("【用户意图】").append(intentResult.intentType()).append("\n");
        prompt.append("【置信度】").append(String.format("%.2f", intentResult.confidence())).append("\n\n");
        prompt.append("【RAG召回知识】\n");
        for (int i = 0; i < ragHits.size(); i++) {
            RagKnowledgeService.RagHit hit = ragHits.get(i);
            prompt.append(i + 1).append(". 来源：").append(hit.sourceName()).append("\n");
            if (hit.score() != null) {
                prompt.append("   相似度：").append(String.format("%.4f", hit.score())).append("\n");
            }
            prompt.append(hit.content()).append("\n\n");
        }

        prompt.append("【用户问题】").append(userMessage).append("\n\n");
        prompt.append("""
                请基于【RAG召回知识】回答用户问题：
                1. 优先使用召回知识中的事实，不要编造未提供的信息；
                2. 如果召回知识只能部分回答，请明确说明哪些内容仍需补充；
                3. 回复要自然、有人情味，避免机械地说“没有找到相关内容”；
                4. 如果确实无法判断，请引导用户补充信息或输入“转人工”。
                """.trim());
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
