package com.gov.assistant.repository.document;

import com.gov.assistant.entity.document.KnowledgeItem;
import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.entity.document.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByDocumentIdOrderByChunkOrderAsc(Long documentId);

    List<KnowledgeChunk> findByDocumentIdAndStatusOrderByChunkOrderAsc(Long documentId, KnowledgeChunk.ChunkStatus status);

    List<KnowledgeChunk> findByStatusOrderByUpdatedAtDesc(KnowledgeChunk.ChunkStatus status);

    @Query("""
            SELECT c FROM KnowledgeChunk c
            WHERE c.status = :status
            AND (
                LOWER(c.chunkTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(c.ocrText, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(c.keywords, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            ORDER BY c.updatedAt DESC
            """)
    List<KnowledgeChunk> searchPublishedChunks(@Param("keyword") String keyword,
                                               @Param("status") KnowledgeChunk.ChunkStatus status);

    @Query("""
            SELECT c FROM KnowledgeChunk c
            WHERE c.status = 'PUBLISHED'
            AND c.category = :category
            ORDER BY c.updatedAt DESC
            """)
    List<KnowledgeChunk> findPublishedByCategory(@Param("category") KnowledgeItem.KnowledgeCategory category);

    Long countByDocumentAndStatus(KnowledgeDocument document, KnowledgeChunk.ChunkStatus status);
}
