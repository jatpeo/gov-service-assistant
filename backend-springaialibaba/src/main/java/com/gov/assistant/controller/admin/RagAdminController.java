package com.gov.assistant.controller.admin;

import com.gov.assistant.service.rag.RagKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/rag")
@RequiredArgsConstructor
/**
 * RAG 管理接口。
 *
 * 调用链：
 * 管理后台 RAG 操作 -> RagAdminController -> RagKnowledgeService
 * -> EmbeddingModel/VectorStore。
 */
public class RagAdminController {

    private final RagKnowledgeService ragKnowledgeService;

    /**
     * 查询当前内存向量索引状态。
     *
     * 调用链：
     * 管理后台 -> GET /api/admin/rag/status
     * -> RagKnowledgeService.isReady()/getDocumentCount()。
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("ready", ragKnowledgeService.isReady());
        result.put("documentCount", ragKnowledgeService.getDocumentCount());
        result.put("lastRebuiltAt", ragKnowledgeService.getLastRebuiltAt());
        result.put("lastRebuildSuccess", ragKnowledgeService.isLastRebuildSuccess());
        result.put("lastRebuildMessage", ragKnowledgeService.getLastRebuildMessage());
        return ResponseEntity.ok(result);
    }

    /**
     * 手动重建 RAG 向量索引。
     *
     * 调用链：
     * 管理后台 -> POST /api/admin/rag/rebuild
     * -> RagKnowledgeService.rebuildIndex()
     * -> KnowledgeItem/KnowledgeChunk -> EmbeddingModel -> VectorStore。
     */
    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuild() {
        boolean success = ragKnowledgeService.rebuildIndexSafely();
        Map<String, Object> result = new HashMap<>();
        result.put("ready", ragKnowledgeService.isReady());
        result.put("documentCount", ragKnowledgeService.getDocumentCount());
        result.put("rebuiltAt", ragKnowledgeService.getLastRebuiltAt());
        result.put("success", success);
        result.put("message", ragKnowledgeService.getLastRebuildMessage());
        return ResponseEntity.ok(result);
    }
}
