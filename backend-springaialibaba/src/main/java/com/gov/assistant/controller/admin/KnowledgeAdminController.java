package com.gov.assistant.controller.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.assistant.entity.document.KnowledgeItem;
import com.gov.assistant.repository.document.KnowledgeItemRepository;
import com.gov.assistant.service.rag.RagKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
/**
 * 手工知识库管理接口。
 *
 * 调用链：
 * 后台知识库页面 -> KnowledgeAdminController
 * -> KnowledgeItemRepository；
 * 前台问答兜底链路由 ChatService -> KnowledgeItemRepository 读取已启用知识。
 */
public class KnowledgeAdminController {

    private final KnowledgeItemRepository knowledgeItemRepository;
    private final ObjectMapper objectMapper;
    private final RagKnowledgeService ragKnowledgeService;

    /**
     * 分页查询手工知识库条目。
     *
     * 调用链：
     * 知识库管理页 -> GET /api/admin/knowledge
     * -> KnowledgeItemRepository.findKnowledgeItems()。
     */
    @GetMapping("/knowledge")
    public ResponseEntity<Page<KnowledgeItem>> getKnowledgeItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size);
        KnowledgeItem.Status statusFilter = parseKnowledgeStatus(status);
        return ResponseEntity.ok(knowledgeItemRepository.findKnowledgeItems(keyword, statusFilter, pageable));
    }

    /**
     * 按关键词搜索手工知识库。
     *
     * 调用链：
     * 知识库管理页搜索 -> GET /api/admin/knowledge/search
     * -> KnowledgeItemRepository.searchByKeyword()。
     */
    @GetMapping("/knowledge/search")
    public ResponseEntity<List<KnowledgeItem>> searchKnowledge(@RequestParam String keyword) {
        return ResponseEntity.ok(knowledgeItemRepository.searchByKeyword(keyword));
    }

    /**
     * 新增手工知识条目。
     *
     * 调用链：
     * 知识库管理页 -> POST /api/admin/knowledge
     * -> KnowledgeItemRepository.save()
     * -> refreshRagIndex() -> RagKnowledgeService.rebuildIndexSafely()。
     */
    @PostMapping("/knowledge")
    public ResponseEntity<KnowledgeItem> addKnowledgeItem(@RequestBody KnowledgeItem item) {
        KnowledgeItem toSave = KnowledgeItem.builder()
                .itemCode("KB" + System.currentTimeMillis())
                .category(item.getCategory())
                .question(item.getQuestion())
                .answer(item.getAnswer())
                .keywords(item.getKeywords())
                .relatedPolicy(item.getRelatedPolicy())
                .applicableTarget(item.getApplicableTarget())
                .status(item.getStatus() == null ? KnowledgeItem.Status.ENABLED : item.getStatus())
                .viewCount(item.getViewCount() == null ? 0 : item.getViewCount())
                .hitCount(item.getHitCount() == null ? 0 : item.getHitCount())
                .createdBy(item.getCreatedBy())
                .updatedBy(item.getUpdatedBy())
                .build();
        KnowledgeItem saved = knowledgeItemRepository.save(toSave);
        refreshRagIndex("新增手工知识条目");
        return ResponseEntity.ok(saved);
    }

    /**
     * 更新手工知识条目。
     *
     * 调用链：
     * 知识库管理页 -> PUT /api/admin/knowledge/{id}
     * -> KnowledgeItemRepository.findById()
     * -> applyKnowledgeItemUpdates() -> KnowledgeItemRepository.save()。
     * -> refreshRagIndex() -> RagKnowledgeService.rebuildIndexSafely()。
     */
    @PutMapping("/knowledge/{id}")
    public ResponseEntity<KnowledgeItem> updateKnowledgeItem(@PathVariable Long id, @RequestBody KnowledgeItem item) {
        KnowledgeItem existing = knowledgeItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识条目不存在: " + id));
        applyKnowledgeItemUpdates(existing, item);
        KnowledgeItem saved = knowledgeItemRepository.save(existing);
        refreshRagIndex("更新手工知识条目");
        return ResponseEntity.ok(saved);
    }

    /**
     * 删除手工知识条目。
     *
     * 调用链：
     * 知识库管理页 -> DELETE /api/admin/knowledge/{id}
     * -> KnowledgeItemRepository.findById() -> KnowledgeItemRepository.delete()
     * -> refreshRagIndex() -> RagKnowledgeService.rebuildIndexSafely()。
     */
    @DeleteMapping("/knowledge/{id}")
    public ResponseEntity<Void> deleteKnowledgeItem(@PathVariable Long id) {
        KnowledgeItem item = knowledgeItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识条目不存在: " + id));
        knowledgeItemRepository.delete(item);
        refreshRagIndex("删除手工知识条目");
        return ResponseEntity.ok().build();
    }

    /**
     * 更新知识条目启用/停用状态。
     *
     * 调用链：
     * 知识库管理页 -> PATCH /api/admin/knowledge/{id}/status
     * -> KnowledgeItemRepository.findById() -> KnowledgeItemRepository.save()
     * -> refreshRagIndex() -> RagKnowledgeService.rebuildIndexSafely()。
     */
    @PatchMapping("/knowledge/{id}/status")
    public ResponseEntity<KnowledgeItem> updateKnowledgeStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        KnowledgeItem item = knowledgeItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识条目不存在: " + id));

        KnowledgeItem.Status status = parseKnowledgeStatus(request.get("status"));
        if (status == null) {
            throw new RuntimeException("状态参数无效");
        }

        item.setStatus(status);
        KnowledgeItem saved = knowledgeItemRepository.save(item);
        refreshRagIndex("更新手工知识条目状态");
        return ResponseEntity.ok(saved);
    }

    /**
     * 批量更新知识条目状态。
     *
     * 调用链：
     * 知识库管理页 -> PATCH /api/admin/knowledge/batch-status
     * -> 多条 KnowledgeItemRepository.save()
     * -> refreshRagIndex() -> RagKnowledgeService.rebuildIndexSafely()。
     */
    @PatchMapping("/knowledge/batch-status")
    public ResponseEntity<Map<String, Object>> batchUpdateKnowledgeStatus(@RequestBody Map<String, Object> request) {
        List<Long> ids = extractIds(request.get("ids"));
        KnowledgeItem.Status status = parseKnowledgeStatus(String.valueOf(request.get("status")));
        if (status == null) {
            throw new RuntimeException("状态参数无效");
        }
        if (ids.isEmpty()) {
            throw new RuntimeException("请选择要操作的知识条目");
        }

        int updatedCount = 0;
        for (Long id : ids) {
            KnowledgeItem item = knowledgeItemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("知识条目不存在: " + id));
            item.setStatus(status);
            knowledgeItemRepository.save(item);
            updatedCount++;
        }

        refreshRagIndex("批量更新手工知识条目状态");
        Map<String, Object> result = new HashMap<>();
        result.put("updatedCount", updatedCount);
        result.put("status", status);
        return ResponseEntity.ok(result);
    }

    /**
     * 导出知识库
     */
    @GetMapping("/knowledge/export")
    public ResponseEntity<byte[]> exportKnowledgeItems() throws Exception {
        List<KnowledgeItem> items = knowledgeItemRepository.findAll();
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(items);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=knowledge-items.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 导入知识库。
     *
     * 调用链：
     * 知识库管理页 -> POST /api/admin/knowledge/import
     * -> ObjectMapper 反序列化 -> KnowledgeItemRepository.save()
     * -> refreshRagIndex() -> RagKnowledgeService.rebuildIndexSafely()。
     */
    @PostMapping(value = "/knowledge/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importKnowledgeItems(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new RuntimeException("导入文件不能为空");
        }

        List<KnowledgeItem> items = objectMapper.readValue(
                file.getInputStream(),
                new TypeReference<List<KnowledgeItem>>() {
                }
        );

        if (items == null || items.isEmpty()) {
            throw new RuntimeException("导入文件中没有可用的知识条目");
        }

        int createdCount = 0;
        int updatedCount = 0;

        for (KnowledgeItem item : items) {
            if (item.getQuestion() == null || item.getQuestion().isBlank()
                    || item.getAnswer() == null || item.getAnswer().isBlank()
                    || item.getCategory() == null) {
                continue;
            }

            KnowledgeItem target = Optional.ofNullable(item.getItemCode())
                    .filter(code -> !code.isBlank())
                    .flatMap(knowledgeItemRepository::findByItemCode)
                    .orElse(null);

            if (target == null) {
                target = KnowledgeItem.builder()
                        .itemCode(resolveUniqueItemCode(item.getItemCode()))
                        .build();
                createdCount++;
            } else {
                updatedCount++;
            }

            target.setCategory(item.getCategory());
            target.setQuestion(item.getQuestion());
            target.setAnswer(item.getAnswer());
            target.setKeywords(item.getKeywords());
            target.setRelatedPolicy(item.getRelatedPolicy());
            target.setApplicableTarget(item.getApplicableTarget());
            target.setStatus(item.getStatus() == null ? KnowledgeItem.Status.ENABLED : item.getStatus());
            target.setViewCount(item.getViewCount() == null ? 0 : item.getViewCount());
            target.setHitCount(item.getHitCount() == null ? 0 : item.getHitCount());
            target.setCreatedBy(item.getCreatedBy());
            target.setUpdatedBy(item.getUpdatedBy());

            knowledgeItemRepository.save(target);
        }

        refreshRagIndex("导入手工知识库");
        Map<String, Object> result = new HashMap<>();
        result.put("createdCount", createdCount);
        result.put("updatedCount", updatedCount);
        result.put("totalCount", items.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取高频问题。
     *
     * 调用链：
     * 知识库管理页 -> GET /api/admin/knowledge/frequent
     * -> KnowledgeItemRepository.findTopFrequentQuestions()。
     */
    @GetMapping("/knowledge/frequent")
    public ResponseEntity<List<KnowledgeItem>> getFrequentQuestions(
            @RequestParam(defaultValue = "10") int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return ResponseEntity.ok(knowledgeItemRepository.findTopFrequentQuestions(pageable));
    }

    private void applyKnowledgeItemUpdates(KnowledgeItem target, KnowledgeItem source) {
        target.setCategory(source.getCategory());
        target.setQuestion(source.getQuestion());
        target.setAnswer(source.getAnswer());
        target.setKeywords(source.getKeywords());
        target.setRelatedPolicy(source.getRelatedPolicy());
        target.setApplicableTarget(source.getApplicableTarget());
        if (source.getStatus() != null) {
            target.setStatus(source.getStatus());
        }
        target.setUpdatedBy(source.getUpdatedBy());
    }

    private KnowledgeItem.Status parseKnowledgeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return KnowledgeItem.Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("状态参数无效: " + status);
        }
    }

    private List<Long> extractIds(Object rawIds) {
        if (rawIds == null) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>();
        if (rawIds instanceof List<?> list) {
            for (Object value : list) {
                Long id = toLong(value);
                if (id != null) {
                    ids.add(id);
                }
            }
        } else {
            Long id = toLong(rawIds);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.parseLong(text);
    }

    private String resolveUniqueItemCode(String preferredCode) {
        if (preferredCode != null && !preferredCode.isBlank()
                && knowledgeItemRepository.findByItemCode(preferredCode).isEmpty()) {
            return preferredCode;
        }

        String itemCode;
        do {
            itemCode = "KB" + System.currentTimeMillis();
        } while (knowledgeItemRepository.findByItemCode(itemCode).isPresent());
        return itemCode;
    }

    /**
     * 在知识库发生写操作后触发 RAG 索引重建，保证语义检索与当前数据保持一致。
     *
     * 调用链：
     * 知识库增删改/导入 -> refreshRagIndex()
     * -> RagKnowledgeService.rebuildIndexSafely()
     * -> 重新扫描手工知识库与文档片段。
     */
    private void refreshRagIndex(String reason) {
        log.info("触发 RAG 索引刷新: {}", reason);
        ragKnowledgeService.rebuildIndexSafely();
    }
}
