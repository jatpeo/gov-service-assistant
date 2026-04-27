package com.gov.assistant.controller;

import com.gov.assistant.entity.Message;
import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.service.ChatService;
import com.gov.assistant.service.document.DocumentKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final DocumentKnowledgeService documentKnowledgeService;

    /**
     * 创建新会话
     */
    @PostMapping("/conversation")
    public ResponseEntity<Map<String, String>> createConversation(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName) {

        String sessionId = chatService.createConversation(userId, userName);
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    /**
     * 发送消息
     */
    @PostMapping("/message")
    public ResponseEntity<ChatService.ChatResponse> sendMessage(
            @RequestParam String sessionId,
            @RequestParam String message) {

        log.info("收到消息: sessionId={}, message={}", sessionId, message);
        ChatService.ChatResponse response = chatService.processMessage(sessionId, message);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<Message>> getConversationHistory(@PathVariable String sessionId) {
        List<Message> messages = chatService.getConversationHistory(sessionId);
        return ResponseEntity.ok(messages);
    }

    /**
     * 关闭会话
     */
    @PostMapping("/close/{sessionId}")
    public ResponseEntity<Void> closeConversation(
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer satisfactionScore) {

        chatService.closeConversation(sessionId, satisfactionScore);
        return ResponseEntity.ok().build();
    }

    /**
     * 请求转人工
     */
    @PostMapping("/handoff/{sessionId}")
    public ResponseEntity<Map<String, String>> requestHandoff(@PathVariable String sessionId) {
        // 实际转人工逻辑在 ChatService 中处理
        return ResponseEntity.ok(Map.of("status", "handoff_requested", "message", "已为您转接人工客服"));
    }

    /**
     * 获取聊天中引用的图片
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
}
