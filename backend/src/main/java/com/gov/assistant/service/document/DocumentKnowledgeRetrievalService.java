package com.gov.assistant.service.document;

import com.gov.assistant.entity.KnowledgeItem;
import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.repository.document.KnowledgeChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentKnowledgeRetrievalService {

    private final KnowledgeChunkRepository chunkRepository;

    public List<KnowledgeChunk> searchPublishedChunks(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        return chunkRepository.searchPublishedChunks(keyword, KnowledgeChunk.ChunkStatus.PUBLISHED);
    }

    public List<String> formatChunkSources(List<KnowledgeChunk> chunks) {
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

    public String buildChunkPrompt(List<KnowledgeChunk> chunks) {
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
