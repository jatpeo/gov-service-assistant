package com.gov.assistant.controller.chat;

import com.gov.assistant.entity.chat.Conversation;
import com.gov.assistant.entity.chat.Message;
import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.entity.auth.UserAccount;
import com.gov.assistant.service.chat.ChatService;
import com.gov.assistant.service.chat.ConversationService;
import com.gov.assistant.service.auth.CurrentUserService;
import com.gov.assistant.service.document.DocumentKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
/**
 * 前台智能客服接口。
 *
 * 调用链：
 * ChatView -> ChatController -> CurrentUserService 校验登录用户
 * -> ChatService/ConversationService/DocumentKnowledgeService -> Repository/AI。
 */
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final DocumentKnowledgeService documentKnowledgeService;
    private final CurrentUserService currentUserService;

    /**
     * 创建当前登录用户的新会话。
     *
     * 调用链：
     * ChatView 新建会话 -> POST /api/chat/conversation
     * -> CurrentUserService.requireUser() -> ChatService.createConversation()
     * -> ConversationService.createConversation() -> ConversationRepository.save()。
     */
    @PostMapping("/conversation")
    public ResponseEntity<Map<String, String>> createConversation(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            Authentication authentication) {

        UserAccount user = currentUserService.requireUser(authentication);
        String sessionId = chatService.createConversation(user.getUsername(), user.getDisplayName());
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    /**
     * 获取当前登录用户的会话列表，用于左侧“我的会话”。
     *
     * 调用链：
     * ChatView 初始化/刷新 -> GET /api/chat/conversations
     * -> CurrentUserService.requireUser() -> ConversationService.getUserConversations()。
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummary>> getMyConversations(Authentication authentication) {
        UserAccount user = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(conversationService.getUserConversations(user.getUsername())
                .stream()
                .map(ConversationSummary::from)
                .toList());
    }

    /**
     * 删除当前用户自己的会话及消息。
     *
     * 调用链：
     * ChatView 点击删除 -> DELETE /api/chat/conversations/{sessionId}
     * -> ensureConversationOwner() -> ConversationService.deleteConversation()。
     */
    @DeleteMapping("/conversations/{sessionId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String sessionId,
                                                   Authentication authentication) {
        ensureConversationOwner(sessionId, authentication);
        conversationService.deleteConversation(sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 发送用户消息并触发智能问答主链路。
     *
     * 调用链：
     * ChatView 输入/快捷服务自动发送 -> POST /api/chat/message
     * -> ensureConversationOwner() -> ChatService.processMessage()
     * -> 意图识别/标准答案/RAG/文档检索/大模型/转人工。
     */
    @PostMapping("/message")
    public ResponseEntity<ChatService.ChatResponse> sendMessage(
            @RequestParam String sessionId,
            @RequestParam String message,
            Authentication authentication) {

        ensureConversationOwner(sessionId, authentication);
        log.info("收到消息: sessionId={}, message={}", sessionId, message);
        ChatService.ChatResponse response = chatService.processMessage(sessionId, message);
        return ResponseEntity.ok(response);
    }

    /**
     * 发送消息并以 SSE 形式流式返回 AI 回复。
     *
     * 调用链：
     * ChatView fetch stream -> POST /api/chat/message/stream
     * -> ensureConversationOwner() -> ChatService.processMessageStream()
     * -> SseEmitter(delta/done/error)。
     */
    @PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @RequestParam String sessionId,
            @RequestParam String message,
            Authentication authentication) {

        ensureConversationOwner(sessionId, authentication);
        SseEmitter emitter = new SseEmitter(0L);

        // 异步执行，避免长时间阻塞 Servlet 线程。
        // SSE 连接建立后，服务端会持续发送 delta/done/error 事件。
        CompletableFuture.runAsync(() -> {
            try {
                ChatService.ChatResponse response = chatService.processMessageStream(sessionId, message, delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(delta));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                // done 事件携带最终响应，前端可在流结束后补齐会话状态或兜底字段。
                emitter.send(SseEmitter.event().name("done").data(response));
                emitter.complete();
            } catch (Exception e) {
                try {
                    // 任何异常都以 SSE error 事件返回，便于前端统一处理。
                    emitter.send(SseEmitter.event().name("error").data(Map.of(
                            "message", e.getMessage() == null ? "流式响应失败" : e.getMessage()
                    )));
                } catch (IOException ignored) {
                    // 客户端断开时不再额外处理，SseEmitter 会在 completeWithError 中释放。
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 获取某个会话的完整消息历史。
     *
     * 调用链：
     * ChatView 切换会话 -> GET /api/chat/history/{sessionId}
     * -> ensureConversationOwner() -> ChatService.getConversationHistory()
     * -> ConversationService.getConversationMessages()。
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<Message>> getConversationHistory(@PathVariable String sessionId,
                                                               Authentication authentication) {
        ensureConversationOwner(sessionId, authentication);
        List<Message> messages = chatService.getConversationHistory(sessionId);
        return ResponseEntity.ok(messages);
    }

    /**
     * 用户主动关闭会话，可附带满意度分数。
     *
     * 调用链：
     * ChatView 点击结束会话 -> POST /api/chat/close/{sessionId}
     * -> ensureConversationOwner() -> ChatService.closeConversation()
     * -> ConversationService.closeConversation()。
     */
    @PostMapping("/close/{sessionId}")
    public ResponseEntity<Void> closeConversation(
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer satisfactionScore,
            Authentication authentication) {

        ensureConversationOwner(sessionId, authentication);
        chatService.closeConversation(sessionId, satisfactionScore);
        return ResponseEntity.ok().build();
    }

    /**
     * 用户主动请求转人工。
     *
     * 调用链：
     * ChatView 点击/输入转人工 -> POST /api/chat/handoff/{sessionId}
     * -> ensureConversationOwner()。当前真实转人工状态流转主要由 ChatService.processMessage() 中的转人工分支处理。
     */
    @PostMapping("/handoff/{sessionId}")
    public ResponseEntity<Map<String, String>> requestHandoff(@PathVariable String sessionId,
                                                              Authentication authentication) {
        ensureConversationOwner(sessionId, authentication);
        // 实际转人工逻辑在 ChatService 中处理
        return ResponseEntity.ok(Map.of("status", "handoff_requested", "message", "已为您转接人工客服"));
    }

    /**
     * 获取 AI 回复中引用的文档图片资源。
     *
     * 调用链：
     * ChatView 展示文档图片 -> GET /api/chat/document-chunks/{chunkId}/image
     * -> DocumentKnowledgeService.getChunkById() -> 本地文件系统读取图片。
     */
    @GetMapping("/document-chunks/{chunkId}/image")
    public ResponseEntity<Resource> getChunkImage(@PathVariable Long chunkId) throws IOException {
        KnowledgeChunk chunk = documentKnowledgeService.getChunkById(chunkId)
                .orElseThrow(() -> new RuntimeException("知识片段不存在: " + chunkId));
        if (chunk.getSourceImagePath() == null || chunk.getSourceImagePath().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        java.nio.file.Path imagePath = java.nio.file.Path.of(chunk.getSourceImagePath());
        if (!java.nio.file.Files.exists(imagePath)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = java.nio.file.Files.probeContentType(imagePath);
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .body(new FileSystemResource(imagePath));
    }

    /**
     * 校验会话归属，防止用户访问他人的会话历史或删除他人会话。
     *
     * 调用链：
     * ChatController 各会话级接口 -> CurrentUserService.requireUser()
     * -> ConversationService.getConversationBySessionId()。
     */
    private void ensureConversationOwner(String sessionId, Authentication authentication) {
        UserAccount user = currentUserService.requireUser(authentication);
        Conversation conversation = conversationService.getConversationBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));
        if (!user.getUsername().equals(conversation.getUserId())) {
            throw new IllegalArgumentException("无权访问该会话");
        }
    }

    public record ConversationSummary(
            String sessionId,
            String title,
            Conversation.ConversationStatus status,
            Conversation.IntentType primaryIntent,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ConversationSummary from(Conversation conversation) {
            return new ConversationSummary(
                    conversation.getSessionId(),
                    buildTitle(conversation),
                    conversation.getStatus(),
                    conversation.getPrimaryIntent(),
                    conversation.getCreatedAt(),
                    conversation.getUpdatedAt()
            );
        }

        private static String buildTitle(Conversation conversation) {
            if (conversation.getPrimaryIntent() != null) {
                return switch (conversation.getPrimaryIntent()) {
                    case POLICY_CONSULTATION -> "政策咨询";
                    case BUSINESS_PROCESSING -> "业务办理";
                    case PROGRESS_QUERY -> "进度查询";
                    case TECHNICAL_SUPPORT -> "技术支持";
                    case ACCOUNT_PERMISSION -> "账号权限";
                    case COMPLAINT_SUGGESTION -> "投诉建议";
                    case OTHER -> "智能咨询";
                };
            }
            return "新的咨询";
        }
    }
}
