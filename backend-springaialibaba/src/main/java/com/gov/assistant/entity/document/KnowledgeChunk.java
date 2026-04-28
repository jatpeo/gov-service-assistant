package com.gov.assistant.entity.document;

import com.gov.assistant.entity.document.KnowledgeItem;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_chunks")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    @JsonIgnore
    private KnowledgeDocument document;

    @Column(name = "chunk_code", unique = true, nullable = false)
    private String chunkCode;

    @Column(name = "chunk_title", length = 500)
    private String chunkTitle;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private KnowledgeItem.KnowledgeCategory category;

    @Column(name = "page_no")
    private Integer pageNo;

    @Column(name = "image_index")
    private Integer imageIndex;

    @Column(name = "section_path", length = 1000)
    private String sectionPath;

    @Column(name = "source_image_path", length = 1000)
    private String sourceImagePath;

    @Column(name = "source_file_name")
    private String sourceFileName;

    @Column(name = "chunk_order")
    private Integer chunkOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChunkStatus status = ChunkStatus.DRAFT;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "keywords", length = 1000)
    private String keywords;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ChunkStatus {
        DRAFT,
        REVIEWED,
        PUBLISHED,
        SKIPPED,
        REJECTED,
        FAILED
    }
}
