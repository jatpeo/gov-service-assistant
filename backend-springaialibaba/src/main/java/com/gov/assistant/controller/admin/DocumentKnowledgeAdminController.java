package com.gov.assistant.controller.admin;

import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.entity.document.KnowledgeDocument;
import com.gov.assistant.repository.document.KnowledgeDocumentRepository;
import com.gov.assistant.service.document.DocumentKnowledgeService;
import com.gov.assistant.service.rag.RagKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
/**
 * 管理后台文档知识库接口。
 *
 * 调用链：
 * 后台文档知识库页面 -> DocumentKnowledgeAdminController
 * -> DocumentKnowledgeService/KnowledgeDocumentRepository
 * -> 文档存储、解析、OCR、片段审核发布。
 */
public class DocumentKnowledgeAdminController {

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentKnowledgeService documentKnowledgeService;
    private final RagKnowledgeService ragKnowledgeService;

    /**
     * 分页查询上传过的文档。
     *
     * 调用链：
     * Documents 页面 -> GET /api/admin/documents -> KnowledgeDocumentRepository.findAll()。
     */
    @GetMapping
    public ResponseEntity<Page<KnowledgeDocument>> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(documentRepository.findAll(pageable));
    }

    /**
     * 获取单个文档元数据。
     *
     * 调用链：
     * Documents 详情页 -> GET /api/admin/documents/{id}
     * -> KnowledgeDocumentRepository.findById()。
     */
    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeDocument> getDocument(@PathVariable Long id) {
        return documentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 预览文档及其解析出的知识片段。
     *
     * 调用链：
     * Documents 预览 -> GET /api/admin/documents/{id}/preview
     * -> DocumentKnowledgeService.getDocumentChunks()。
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<Map<String, Object>> previewDocument(@PathVariable Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + id));
        List<KnowledgeChunk> chunks = documentKnowledgeService.getDocumentChunks(id);
        Map<String, Object> response = new HashMap<>();
        response.put("document", document);
        response.put("chunks", chunks);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<List<KnowledgeChunk>> listChunks(@PathVariable Long id) {
        return ResponseEntity.ok(documentKnowledgeService.getDocumentChunks(id));
    }

    @GetMapping("/chunks/{chunkId}/image")
    public ResponseEntity<Resource> getChunkImage(@PathVariable Long chunkId) throws IOException {
        KnowledgeChunk chunk = documentKnowledgeService.getChunkById(chunkId)
                .orElseThrow(() -> new RuntimeException("知识片段不存在: " + chunkId));
        if (!StringUtils.hasText(chunk.getSourceImagePath())) {
            return ResponseEntity.notFound().build();
        }

        java.nio.file.Path imagePath = java.nio.file.Path.of(chunk.getSourceImagePath());
        if (!java.nio.file.Files.exists(imagePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(imagePath);
        String contentType = java.nio.file.Files.probeContentType(imagePath);
        if (!StringUtils.hasText(contentType)) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .body(resource);
    }

    /**
     * 上传并解析知识库文档。
     *
     * 调用链：
     * Documents 上传 -> POST /api/admin/documents/upload
     * -> DocumentKnowledgeService.ingestDocument()
     * -> 文件存储/PDF 或 Word 解析/OCR/片段入库。
     */
    @PostMapping("/upload")
    public ResponseEntity<KnowledgeDocument> uploadDocument(@RequestParam("file") MultipartFile file,
                                                           @RequestParam("documentType") String documentType,
                                                           @RequestParam(required = false) String uploadedBy) {
        KnowledgeDocument.DocumentType type = parseDocumentType(documentType);
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new RuntimeException("文件名不能为空");
        }
        return ResponseEntity.ok(documentKnowledgeService.ingestDocument(file, type, uploadedBy));
    }

    /**
     * 重新解析文档。
     *
     * 调用链：
     * Documents 重试 -> POST /api/admin/documents/{id}/retry
     * -> DocumentKnowledgeService.retryDocument()
     * -> refreshRagIndex() -> RagKnowledgeService.rebuildIndexSafely()。
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<KnowledgeDocument> retry(@PathVariable Long id) {
        KnowledgeDocument document = documentKnowledgeService.retryDocument(id);
        refreshRagIndex("重新解析文档");
        return ResponseEntity.ok(document);
    }

    /**
     * 发布文档片段，使其可被聊天检索链路使用。
     *
     * 调用链：
     * Documents 发布 -> POST /api/admin/documents/{id}/publish
     * -> DocumentKnowledgeService.publishDocument()
     * -> refreshRagIndex() -> RagKnowledgeService.rebuildIndexSafely()
     * -> 后续 ChatService/RagKnowledgeService 可检索。
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<KnowledgeDocument> publish(@PathVariable Long id) {
        KnowledgeDocument document = documentKnowledgeService.publishDocument(id);
        refreshRagIndex("发布文档片段");
        return ResponseEntity.ok(document);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<KnowledgeDocument> reject(@PathVariable Long id,
                                                    @RequestParam(required = false) String reason) {
        KnowledgeDocument document = documentKnowledgeService.rejectDocument(id, reason);
        refreshRagIndex("拒绝文档");
        return ResponseEntity.ok(document);
    }

    private KnowledgeDocument.DocumentType parseDocumentType(String documentType) {
        if (!StringUtils.hasText(documentType)) {
            throw new RuntimeException("文档类型不能为空");
        }
        try {
            return KnowledgeDocument.DocumentType.valueOf(documentType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            if ("PDF".equalsIgnoreCase(documentType)) {
                return KnowledgeDocument.DocumentType.PDF_OPERATION_MANUAL;
            }
            if ("WORD".equalsIgnoreCase(documentType) || "DOCX".equalsIgnoreCase(documentType)) {
                return KnowledgeDocument.DocumentType.WORD_FAQ;
            }
            throw new RuntimeException("不支持的文档类型: " + documentType);
        }
    }

    /**
     * 文档知识发生发布/重试/拒绝后，同步刷新 RAG 索引，避免已删除或已失效的片段继续命中。
     *
     * 调用链：
     * 文档审核/发布 -> refreshRagIndex()
     * -> RagKnowledgeService.rebuildIndexSafely()
     * -> 重新装载当前可检索片段。
     */
    private void refreshRagIndex(String reason) {
        log.info("触发 RAG 索引刷新: {}", reason);
        ragKnowledgeService.rebuildIndexSafely();
    }
}
