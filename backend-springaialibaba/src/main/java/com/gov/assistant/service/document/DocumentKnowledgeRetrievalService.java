package com.gov.assistant.service.document;

import com.gov.assistant.entity.document.KnowledgeItem;
import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.repository.document.KnowledgeChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 文档知识片段检索与提示词辅助服务。
 *
 * 调用链：
 * ChatService.processMessage() 在 RAG 未命中时
 * -> DocumentKnowledgeRetrievalService
 * -> KnowledgeChunkRepository 查询已发布片段。
 */
public class DocumentKnowledgeRetrievalService {

    private final KnowledgeChunkRepository chunkRepository;

    /**
     * 使用关键词检索已发布的文档片段。
     *
     * 调用链：
     * ChatService.searchDocumentKnowledgeBase()
     * -> searchPublishedChunks()
     * -> KnowledgeChunkRepository.searchPublishedChunks()。
     */
    public List<KnowledgeChunk> searchPublishedChunks(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        // 只检索已发布的片段，避免未审核内容直接进入客服回答。
        return chunkRepository.searchPublishedChunks(keyword, KnowledgeChunk.ChunkStatus.PUBLISHED);
    }

    /**
     * 将命中的文档片段格式化成可回显给前端或写入消息来源的文本。
     *
     * 调用链：
     * ChatService.processMessage() 文档命中分支
     * -> formatChunkSources() -> Message.knowledgeSources。
     */
    public List<String> formatChunkSources(List<KnowledgeChunk> chunks) {
        // 把来源信息压缩成前端可展示的简短文本。
        return chunks.stream()
                .map(chunk -> {
                    StringBuilder builder = new StringBuilder();
                    builder.append(StringUtils.hasText(chunk.getSourceFileName())
                            ? chunk.getSourceFileName()
                            : "document");
                    if (chunk.getPageNo() != null) {
                        builder.append(" | page ").append(chunk.getPageNo());
                    }
                    if (chunk.getImageIndex() != null) {
                        builder.append(" | image ").append(chunk.getImageIndex() + 1);
                    }
                    if (StringUtils.hasText(chunk.getSectionPath())) {
                        builder.append(" | ").append(chunk.getSectionPath());
                    }
                    return builder.toString();
                })
                .toList();
    }

    /**
     * 构造文档片段提示词片段。
     *
     * 调用链：
     * ChatService 文档命中分支可复用
     * -> buildChunkPrompt() -> ChatClient prompt。
     */
    public String buildChunkPrompt(List<KnowledgeChunk> chunks) {
        // 将检索到的文档片段整理成提示词片段，供 ChatService 进一步拼接系统提示。
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            builder.append(i + 1).append(". ");
            if (StringUtils.hasText(chunk.getChunkTitle())) {
                builder.append(chunk.getChunkTitle()).append("\n");
            }
            builder.append(chunk.getContent()).append("\n");
            if (StringUtils.hasText(chunk.getOcrText())) {
                builder.append("   OCR: ").append(chunk.getOcrText()).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    public KnowledgeItem toFallbackKnowledgeItem(KnowledgeChunk chunk) {
        return KnowledgeItem.builder()
                .question(chunk.getChunkTitle())
                .answer(chunk.getContent())
                .keywords(chunk.getKeywords())
                .category(chunk.getCategory())
                .build();
    }
}
