package com.gov.assistant.service.rag;

import com.gov.assistant.entity.document.KnowledgeItem;
import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.repository.document.KnowledgeItemRepository;
import com.gov.assistant.repository.document.KnowledgeChunkRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * RAG 语义检索服务。
 *
 * 调用链：
 * ChatService.processMessage() -> search()
 * -> SimpleVectorStore.similaritySearch()；
 * 管理后台 -> RagAdminController.rebuild() -> rebuildIndex()。
 */
public class RagKnowledgeService {

    private final EmbeddingModel embeddingModel;
    private final KnowledgeItemRepository knowledgeItemRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;

    @Value("${app.rag.top-k:5}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.35}")
    private double similarityThreshold;

    private volatile VectorStore vectorStore;
    private final AtomicBoolean indexed = new AtomicBoolean(false);
    private volatile int documentCount = 0;
    private volatile LocalDateTime lastRebuiltAt;
    private volatile boolean lastRebuildSuccess;
    private volatile String lastRebuildMessage;

    /**
     * 应用启动后尝试构建内存向量索引。
     *
     * 调用链：
     * Spring 容器初始化 -> init() -> rebuildIndexSafely()。
     */
    @PostConstruct
    public void init() {
        rebuildIndexSafely();
    }

    /**
     * 安全重建索引，失败时保留系统可用性并让聊天链路回退到关键词检索。
     *
     * 调用链：
     * init()/search() 惰性重建 -> rebuildIndexSafely() -> rebuildIndex()。
     */
    public boolean rebuildIndexSafely() {
        try {
            rebuildIndex();
            return true;
        } catch (Exception ex) {
            indexed.set(false);
            lastRebuiltAt = LocalDateTime.now();
            lastRebuildSuccess = false;
            lastRebuildMessage = ex.getMessage();
            log.warn("RAG 向量索引初始化失败，将临时回退到关键词检索: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 重建 RAG 向量索引。
     *
     * 调用链：
     * RagAdminController.rebuild()/rebuildIndexSafely()
     * -> KnowledgeItemRepository + KnowledgeChunkRepository
     * -> EmbeddingModel -> SimpleVectorStore.add()。
     */
    public synchronized void rebuildIndex() {
        // 先把启用的手工知识库和已发布的文档片段统一转成 Spring AI Document，
        // 然后一次性灌入内存向量库。
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> documents = new ArrayList<>();

        knowledgeItemRepository.findAll()
                .stream()
                .filter(item -> item.getStatus() == KnowledgeItem.Status.ENABLED)
                .map(this::toKnowledgeDocument)
                .forEach(documents::add);

        knowledgeChunkRepository.findByStatusOrderByUpdatedAtDesc(KnowledgeChunk.ChunkStatus.PUBLISHED)
                .stream()
                .map(this::toChunkDocument)
                .forEach(documents::add);

        if (!documents.isEmpty()) {
            store.add(documents);
        }
        this.vectorStore = store;
        this.documentCount = documents.size();
        indexed.set(true);
        lastRebuiltAt = LocalDateTime.now();
        lastRebuildSuccess = true;
        lastRebuildMessage = "索引构建完成，共载入 " + documents.size() + " 条知识片段";
        log.info("RAG 向量索引构建完成: documents={}", documents.size());
    }

    public boolean isReady() {
        return indexed.get() && vectorStore != null;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public LocalDateTime getLastRebuiltAt() {
        return lastRebuiltAt;
    }

    public boolean isLastRebuildSuccess() {
        return lastRebuildSuccess;
    }

    public String getLastRebuildMessage() {
        return lastRebuildMessage;
    }

    /**
     * 使用用户问题做语义召回。
     *
     * 调用链：
     * ChatService.processMessage() -> search()
     * -> SearchRequest -> VectorStore.similaritySearch() -> RagHit。
     */
    public List<RagHit> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // 索引不存在时先尝试自动重建，避免启动初期直接返回空结果。
        if (!indexed.get() || vectorStore == null) {
            rebuildIndexSafely();
        }
        if (!indexed.get() || vectorStore == null) {
            return List.of();
        }

        try {
            // 召回阈值和 topK 共同控制返回内容数量，减少噪音。
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .build();
            return vectorStore.similaritySearch(request)
                    .stream()
                    .map(RagHit::from)
                    .toList();
        } catch (Exception ex) {
            log.warn("RAG 检索失败，将回退到关键词检索: {}", ex.getMessage());
            return List.of();
        }
    }

    private Document toKnowledgeDocument(KnowledgeItem item) {
        // 手工知识库转成可检索文本，保留问题、答案、关键词和适用对象。
        String text = """
                【知识类型】手工知识库
                【问题】%s
                【答案】%s
                【关键词】%s
                【适用对象】%s
                【相关政策】%s
                """.formatted(
                item.getQuestion(),
                item.getAnswer(),
                Objects.toString(item.getKeywords(), ""),
                Objects.toString(item.getApplicableTarget(), ""),
                Objects.toString(item.getRelatedPolicy(), "")
        );

        return Document.builder()
                .id("knowledge-" + item.getId())
                .text(text)
                .metadata(Map.of(
                        "sourceType", "KNOWLEDGE_ITEM",
                        "sourceId", item.getId(),
                        "sourceName", Objects.toString(item.getQuestion(), "手工知识库"),
                        "itemCode", Objects.toString(item.getItemCode(), "")
                ))
                .build();
    }

    private Document toChunkDocument(KnowledgeChunk chunk) {
        // 文档片段转成可检索文本，同时把来源文件、页码等元数据带上。
        String text = """
                【知识类型】文档知识片段
                【标题】%s
                【来源文件】%s
                【正文】%s
                【OCR】%s
                【关键词】%s
                """.formatted(
                Objects.toString(chunk.getChunkTitle(), "知识片段"),
                Objects.toString(chunk.getSourceFileName(), ""),
                chunk.getContent(),
                Objects.toString(chunk.getOcrText(), ""),
                Objects.toString(chunk.getKeywords(), "")
        );

        return Document.builder()
                .id("chunk-" + chunk.getId())
                .text(text)
                .metadata(Map.of(
                        "sourceType", "DOCUMENT_CHUNK",
                        "sourceId", chunk.getId(),
                        "sourceName", Objects.toString(chunk.getSourceFileName(), "文档知识片段"),
                        "chunkTitle", Objects.toString(chunk.getChunkTitle(), "知识片段")
                ))
                .build();
    }

    public record RagHit(
            String content,
            String sourceType,
            Long sourceId,
            String sourceName,
            Double score
    ) {
        static RagHit from(Document document) {
            Map<String, Object> metadata = document.getMetadata();
            return new RagHit(
                    document.getText(),
                    Objects.toString(metadata.get("sourceType"), ""),
                    toLong(metadata.get("sourceId")),
                    Objects.toString(metadata.get("sourceName"), ""),
                    document.getScore()
            );
        }

        private static Long toLong(Object value) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value == null) {
                return null;
            }
            return Long.parseLong(String.valueOf(value));
        }
    }
}
