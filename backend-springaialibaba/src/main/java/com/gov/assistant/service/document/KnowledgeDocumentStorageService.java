package com.gov.assistant.service.document;

import com.gov.assistant.entity.document.KnowledgeDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class KnowledgeDocumentStorageService {

    private final Path storageRoot;

    public KnowledgeDocumentStorageService(
            @Value("${gov.assistant.document-storage-path:./data/document-knowledge}") String storagePath) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public StoredFile storeOriginalFile(KnowledgeDocument.DocumentType documentType,
                                       MultipartFile file,
                                       String documentCode) throws IOException {
        Files.createDirectories(storageRoot);

        String suffix = determineSuffix(file.getOriginalFilename());
        String safeName = documentCode + "-" + UUID.randomUUID().toString().replace("-", "") + suffix;
        Path target = storageRoot.resolve("documents").resolve(safeName);
        Files.createDirectories(target.getParent());
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return new StoredFile(target, safeName);
    }

    public StoredFile storeExtractedImage(String documentCode, String sourceName, byte[] bytes, String suffix) throws IOException {
        Files.createDirectories(storageRoot);

        String cleanSuffix = StringUtils.hasText(suffix) ? suffix : ".png";
        String safeName = documentCode + "-" + sourceName + "-" + UUID.randomUUID().toString().replace("-", "") + cleanSuffix;
        Path target = storageRoot.resolve("images").resolve(safeName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        return new StoredFile(target, safeName);
    }

    public Path getStorageRoot() {
        return storageRoot;
    }

    private String determineSuffix(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String suffix = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        return suffix.toLowerCase();
    }

    public record StoredFile(Path path, String fileName) {
    }
}
