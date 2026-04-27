package com.gov.assistant.controller.admin;

import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.entity.document.KnowledgeDocument;
import com.gov.assistant.repository.document.KnowledgeDocumentRepository;
import com.gov.assistant.service.document.DocumentKnowledgeService;
import lombok.RequiredArgsConstructor;
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
public class DocumentKnowledgeAdminController {

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentKnowledgeService documentKnowledgeService;

    @GetMapping
    public ResponseEntity<Page<KnowledgeDocument>> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(documentRepository.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeDocument> getDocument(@PathVariable Long id) {
        return documentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

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

    @PostMapping("/{id}/retry")
    public ResponseEntity<KnowledgeDocument> retry(@PathVariable Long id) {
        return ResponseEntity.ok(documentKnowledgeService.retryDocument(id));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<KnowledgeDocument> publish(@PathVariable Long id) {
        return ResponseEntity.ok(documentKnowledgeService.publishDocument(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<KnowledgeDocument> reject(@PathVariable Long id,
                                                    @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(documentKnowledgeService.rejectDocument(id, reason));
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
}
