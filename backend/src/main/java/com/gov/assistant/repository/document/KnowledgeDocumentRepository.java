package com.gov.assistant.repository.document;

import com.gov.assistant.entity.document.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Optional<KnowledgeDocument> findByDocumentCode(String documentCode);

    List<KnowledgeDocument> findByStatusOrderByUpdatedAtDesc(KnowledgeDocument.Status status);

    List<KnowledgeDocument> findByStatusInOrderByUpdatedAtDesc(List<KnowledgeDocument.Status> status);
}
