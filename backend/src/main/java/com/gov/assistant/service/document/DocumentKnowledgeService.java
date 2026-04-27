package com.gov.assistant.service.document;

import com.gov.assistant.entity.KnowledgeItem;
import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.entity.document.KnowledgeDocument;
import com.gov.assistant.repository.document.KnowledgeChunkRepository;
import com.gov.assistant.repository.document.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentKnowledgeService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeDocumentStorageService storageService;
    private final ImageOcrAdapter imageOcrAdapter;

    @Transactional
    public KnowledgeDocument ingestDocument(MultipartFile file,
                                            KnowledgeDocument.DocumentType documentType,
                                            String uploadedBy) {
        String documentCode = "DOC" + System.currentTimeMillis();
        KnowledgeDocument document = null;
        try {
            KnowledgeDocumentStorageService.StoredFile storedFile = storageService.storeOriginalFile(
                    documentType, file, documentCode);

            document = KnowledgeDocument.builder()
                    .documentCode(documentCode)
                    .fileName(resolveFileName(file))
                    .originalFileName(resolveFileName(file))
                    .filePath(storedFile.path().toString())
                    .documentType(documentType)
                    .status(KnowledgeDocument.Status.UPLOADED)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .uploadedBy(uploadedBy)
                    .totalChunks(0)
                    .publishedChunks(0)
                    .build();
            document = documentRepository.save(document);

            document.setStatus(KnowledgeDocument.Status.PARSING);
            documentRepository.save(document);

            ParseResult result = parseStoredFile(document);
            document.setStatus(KnowledgeDocument.Status.PENDING_REVIEW);
            document.setParseMessage(result.message());
            document.setTotalChunks(result.totalChunks());
            document.setPublishedChunks(0);
            return documentRepository.save(document);
        } catch (Exception e) {
            log.warn("Document ingest failed for {}: {}", documentCode, e.getMessage(), e);
            if (document != null) {
                document.setStatus(KnowledgeDocument.Status.FAILED);
                document.setParseMessage(e.getMessage());
                return documentRepository.save(document);
            }
            throw new RuntimeException("文档上传失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public KnowledgeDocument retryDocument(Long documentId) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + documentId));
        if (!StringUtils.hasText(document.getFilePath())) {
            throw new RuntimeException("文档缺少原始文件路径，无法重试");
        }
        try {
            chunkRepository.deleteAll(chunkRepository.findByDocumentIdOrderByChunkOrderAsc(documentId));
            document.setStatus(KnowledgeDocument.Status.PARSING);
            document.setParseMessage(null);
            documentRepository.save(document);

            ParseResult result = parseStoredFile(document);
            document.setStatus(KnowledgeDocument.Status.PENDING_REVIEW);
            document.setParseMessage(result.message());
            document.setTotalChunks(result.totalChunks());
            document.setPublishedChunks(0);
            return documentRepository.save(document);
        } catch (Exception e) {
            log.warn("Retry document failed for {}: {}", documentId, e.getMessage(), e);
            document.setStatus(KnowledgeDocument.Status.FAILED);
            document.setParseMessage(e.getMessage());
            return documentRepository.save(document);
        }
    }

    @Transactional
    public KnowledgeDocument publishDocument(Long documentId) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + documentId));
        List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkOrderAsc(documentId);
        int published = 0;
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getStatus() == KnowledgeChunk.ChunkStatus.DRAFT
                    || chunk.getStatus() == KnowledgeChunk.ChunkStatus.REVIEWED) {
                chunk.setStatus(KnowledgeChunk.ChunkStatus.PUBLISHED);
                chunkRepository.save(chunk);
                published++;
            }
        }
        document.setStatus(KnowledgeDocument.Status.PUBLISHED);
        document.setPublishedChunks(published);
        return documentRepository.save(document);
    }

    @Transactional
    public KnowledgeDocument rejectDocument(Long documentId, String reason) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + documentId));
        document.setStatus(KnowledgeDocument.Status.FAILED);
        document.setParseMessage(reason);
        return documentRepository.save(document);
    }

    public List<KnowledgeChunk> getDocumentChunks(Long documentId) {
        return chunkRepository.findByDocumentIdOrderByChunkOrderAsc(documentId);
    }

    public java.util.Optional<KnowledgeChunk> getChunkById(Long chunkId) {
        return chunkRepository.findById(chunkId);
    }

    private ParseResult parseStoredFile(KnowledgeDocument document) throws Exception {
        Path source = Path.of(document.getFilePath());
        if (document.getDocumentType() == KnowledgeDocument.DocumentType.PDF_OPERATION_MANUAL) {
            return parsePdf(document, source);
        }
        return parseWord(document, source);
    }

    private ParseResult parsePdf(KnowledgeDocument document, Path sourcePath) throws Exception {
        AtomicInteger chunkOrder = new AtomicInteger(0);
        int ocrFailures = 0;
        int ocrSkipped = 0;
        List<KnowledgeChunk> created = new ArrayList<>();

        try (PDDocument pdf = PDDocument.load(sourcePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int pageCount = pdf.getNumberOfPages();

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                int pageNo = pageIndex + 1;
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                String pageText = Optional.ofNullable(stripper.getText(pdf)).orElse("").trim();
                String sectionPath = "PDF Page " + pageNo;

                for (String paragraph : splitByParagraphs(pageText)) {
                    boolean tableLike = isLikelyTableBlock(paragraph);
                    String chunkTitle = tableLike ? "第" + pageNo + "页表格" : "第" + pageNo + "页正文";
                    String chunkContent = tableLike ? normalizeTableBlock(paragraph) : paragraph;
                    KnowledgeChunk chunk = buildChunk(
                            document,
                            chunkOrder.getAndIncrement(),
                            chunkTitle,
                            chunkContent,
                            sectionPath,
                            pageNo,
                            null,
                            null,
                            null,
                            KnowledgeItem.KnowledgeCategory.OPERATION_PROCESS);
                    created.add(chunkRepository.save(chunk));
                }

                PDPage page = pdf.getPage(pageIndex);
                int imageIndex = 0;
                for (PDImageXObject image : collectPageImages(page)) {
                    String suffix = determineImageSuffix(image);
                    byte[] bytes = toImageBytes(image);
                    if (bytes.length == 0) {
                        continue;
                    }
                    KnowledgeDocumentStorageService.StoredFile imageFile = storageService.storeExtractedImage(
                            document.getDocumentCode(),
                            "page-" + pageNo + "-img-" + imageIndex,
                            bytes,
                            suffix);
                    ImageOcrAdapter.OcrResult ocr = imageOcrAdapter.extractText(imageFile.path());
                    if (ocr.success()) {
                        KnowledgeChunk chunk = buildChunk(
                                document,
                                chunkOrder.getAndIncrement(),
                                "图片识别内容",
                                ocr.text(),
                                sectionPath,
                                pageNo,
                                imageIndex,
                                imageFile.path().toString(),
                                ocr.text(),
                                KnowledgeItem.KnowledgeCategory.OPERATION_PROCESS);
                        created.add(chunkRepository.save(chunk));
                    } else if (isOcrUnavailable(ocr.errorMessage())) {
                        ocrSkipped++;
                        KnowledgeChunk skippedChunk = buildChunk(
                                document,
                                chunkOrder.getAndIncrement(),
                                "图片已提取",
                                "当前环境未配置 OCR，已保留原始图片文件，未执行文字识别。",
                                sectionPath,
                                pageNo,
                                imageIndex,
                                imageFile.path().toString(),
                                null,
                                KnowledgeItem.KnowledgeCategory.OPERATION_PROCESS);
                        skippedChunk.setStatus(KnowledgeChunk.ChunkStatus.SKIPPED);
                        created.add(chunkRepository.save(skippedChunk));
                    } else {
                        ocrFailures++;
                        KnowledgeChunk failedChunk = buildChunk(
                                document,
                                chunkOrder.getAndIncrement(),
                                "图片识别失败",
                                "",
                                sectionPath,
                                pageNo,
                                imageIndex,
                                imageFile.path().toString(),
                                null,
                                KnowledgeItem.KnowledgeCategory.OPERATION_PROCESS);
                        failedChunk.setStatus(KnowledgeChunk.ChunkStatus.FAILED);
                        failedChunk.setFailureReason(ocr.errorMessage());
                        created.add(chunkRepository.save(failedChunk));
                    }
                    imageIndex++;
                }
            }
        }

        String message = "PDF解析完成，生成片段 " + created.size() + " 个";
        if (ocrSkipped > 0) {
            message += "，其中 " + ocrSkipped + " 个图片OCR跳过";
        }
        if (ocrFailures > 0) {
            message += "，其中 " + ocrFailures + " 个图片OCR失败";
        }
        return new ParseResult(message, created.size(), sourcePath);
    }

    private ParseResult parseWord(KnowledgeDocument document, Path sourcePath) throws Exception {
        AtomicInteger chunkOrder = new AtomicInteger(0);
        int ocrFailures = 0;
        int ocrSkipped = 0;
        List<KnowledgeChunk> created = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(sourcePath);
             XWPFDocument word = new XWPFDocument(inputStream)) {

            String currentSection = "Word Document";
            List<String> bufferedParagraphs = new ArrayList<>();
            int paragraphIndex = 0;

            for (IBodyElement element : word.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = Optional.ofNullable(paragraph.getText()).orElse("").trim();
                    if (isHeadingParagraph(paragraph, text)) {
                        paragraphIndex = flushWordParagraphBuffer(document, created, bufferedParagraphs, currentSection,
                                chunkOrder, paragraphIndex);
                        currentSection = text;
                        continue;
                    }
                    if (StringUtils.hasText(text)) {
                        bufferedParagraphs.add(text);
                    }
                    paragraphIndex++;
                    continue;
                }

                if (element instanceof XWPFTable table) {
                    paragraphIndex = flushWordParagraphBuffer(document, created, bufferedParagraphs, currentSection,
                            chunkOrder, paragraphIndex);

                    for (var row : table.getRows()) {
                        List<String> cells = row.getTableCells().stream()
                                .map(cell -> Optional.ofNullable(cell.getText()).orElse("").trim())
                                .filter(StringUtils::hasText)
                                .toList();
                        if (cells.isEmpty()) {
                            continue;
                        }
                        String question = cells.get(0);
                        String answer = cells.size() > 1 ? String.join(" ", cells.subList(1, cells.size())) : "";
                        KnowledgeChunk chunk = buildChunk(
                                document,
                                chunkOrder.getAndIncrement(),
                                question,
                                answer.isBlank() ? question : answer,
                                currentSection,
                                null,
                                null,
                                null,
                                null,
                                KnowledgeItem.KnowledgeCategory.FAQ);
                        created.add(chunkRepository.save(chunk));
                    }
                }
            }

            paragraphIndex = flushWordParagraphBuffer(document, created, bufferedParagraphs, currentSection,
                    chunkOrder, paragraphIndex);

            int imageIndex = 0;
            for (XWPFPictureData pictureData : word.getAllPictures()) {
                String suffix = pictureSuffix(pictureData.getFileName());
                byte[] bytes = pictureData.getData();
                if (bytes == null || bytes.length == 0) {
                    continue;
                }
                KnowledgeDocumentStorageService.StoredFile imageFile = storageService.storeExtractedImage(
                        document.getDocumentCode(),
                        "word-img-" + imageIndex,
                        bytes,
                        suffix);
                ImageOcrAdapter.OcrResult ocr = imageOcrAdapter.extractText(imageFile.path());
                if (ocr.success()) {
                    KnowledgeChunk chunk = buildChunk(
                            document,
                            chunkOrder.getAndIncrement(),
                            "图片识别内容",
                            ocr.text(),
                            "Word Image " + (imageIndex + 1),
                            null,
                            imageIndex,
                            imageFile.path().toString(),
                            ocr.text(),
                            KnowledgeItem.KnowledgeCategory.FAQ);
                    created.add(chunkRepository.save(chunk));
                } else if (isOcrUnavailable(ocr.errorMessage())) {
                    ocrSkipped++;
                    KnowledgeChunk skippedChunk = buildChunk(
                            document,
                            chunkOrder.getAndIncrement(),
                            "图片已提取",
                            "当前环境未配置 OCR，已保留原始图片文件，未执行文字识别。",
                            "Word Image " + (imageIndex + 1),
                            null,
                            imageIndex,
                            imageFile.path().toString(),
                            null,
                            KnowledgeItem.KnowledgeCategory.FAQ);
                    skippedChunk.setStatus(KnowledgeChunk.ChunkStatus.SKIPPED);
                    created.add(chunkRepository.save(skippedChunk));
                } else {
                    ocrFailures++;
                    KnowledgeChunk failedChunk = buildChunk(
                            document,
                            chunkOrder.getAndIncrement(),
                            "图片识别失败",
                            "",
                            "Word Image " + (imageIndex + 1),
                            null,
                            imageIndex,
                            imageFile.path().toString(),
                            null,
                            KnowledgeItem.KnowledgeCategory.FAQ);
                    failedChunk.setStatus(KnowledgeChunk.ChunkStatus.FAILED);
                    failedChunk.setFailureReason(ocr.errorMessage());
                    created.add(chunkRepository.save(failedChunk));
                }
                imageIndex++;
            }
        }

        String message = "Word解析完成，生成片段 " + created.size() + " 个";
        if (ocrSkipped > 0) {
            message += "，其中 " + ocrSkipped + " 个图片OCR跳过";
        }
        if (ocrFailures > 0) {
            message += "，其中 " + ocrFailures + " 个图片OCR失败";
        }
        return new ParseResult(message, created.size(), sourcePath);
    }

    private int flushWordParagraphBuffer(KnowledgeDocument document,
                                         List<KnowledgeChunk> created,
                                         List<String> bufferedParagraphs,
                                         String currentSection,
                                         AtomicInteger chunkOrder,
                                         int paragraphIndex) {
        if (bufferedParagraphs.isEmpty()) {
            return paragraphIndex;
        }
        String content = String.join("\n\n", bufferedParagraphs).trim();
        if (StringUtils.hasText(content)) {
            KnowledgeChunk chunk = buildChunk(
                    document,
                    chunkOrder.getAndIncrement(),
                    currentSection,
                    content,
                    currentSection,
                    null,
                    null,
                    null,
                    null,
                    KnowledgeItem.KnowledgeCategory.FAQ);
            created.add(chunkRepository.save(chunk));
        }
        bufferedParagraphs.clear();
        return paragraphIndex;
    }

    private KnowledgeChunk buildChunk(KnowledgeDocument document,
                                      int chunkOrder,
                                      String title,
                                      String content,
                                      String sectionPath,
                                      Integer pageNo,
                                      Integer imageIndex,
                                      String sourceImagePath,
                                      String ocrText,
                                      KnowledgeItem.KnowledgeCategory category) {
        return KnowledgeChunk.builder()
                .document(document)
                .chunkCode("CHK" + UUID.randomUUID().toString().replace("-", ""))
                .chunkTitle(safeText(title, 500))
                .content(StringUtils.hasText(content) ? content : "")
                .ocrText(ocrText)
                .category(category)
                .pageNo(pageNo)
                .imageIndex(imageIndex)
                .sectionPath(safeText(sectionPath, 1000))
                .sourceImagePath(safeText(sourceImagePath, 1000))
                .sourceFileName(safeText(document.getOriginalFileName(), 255))
                .chunkOrder(chunkOrder)
                .status(KnowledgeChunk.ChunkStatus.DRAFT)
                .keywords(extractKeywords(title + " " + content))
                .build();
    }

    private List<PDImageXObject> collectPageImages(PDPage page) {
        List<PDImageXObject> images = new ArrayList<>();
        try {
            PDResources resources = page.getResources();
            if (resources != null) {
                collectImages(resources, images);
            }
        } catch (Exception e) {
            log.debug("Failed to collect page images: {}", e.getMessage());
        }
        return images;
    }

    private void collectImages(PDResources resources, List<PDImageXObject> images) throws IOException {
        for (var name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject image) {
                images.add(image);
            } else if (xObject instanceof PDFormXObject form && form.getResources() != null) {
                collectImages(form.getResources(), images);
            }
        }
    }

    private byte[] toImageBytes(PDImageXObject image) {
        try {
            BufferedImage bufferedImage = image.getImage();
            if (bufferedImage == null) {
                return new byte[0];
            }
            String format = imageFormat(image.getSuffix());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean written = ImageIO.write(bufferedImage, format, outputStream);
            if (!written) {
                outputStream.reset();
                ImageIO.write(bufferedImage, "png", outputStream);
            }
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.debug("Failed to convert pdf image: {}", e.getMessage());
            return new byte[0];
        }
    }

    private String imageFormat(String suffix) {
        String normalized = Optional.ofNullable(suffix).orElse("").toLowerCase(Locale.ROOT);
        if (normalized.contains("jpg") || normalized.contains("jpeg")) {
            return "jpg";
        }
        if (normalized.contains("png")) {
            return "png";
        }
        if (normalized.contains("tif")) {
            return "tiff";
        }
        return "png";
    }

    private String determineImageSuffix(PDImageXObject image) {
        String suffix = Optional.ofNullable(image.getSuffix()).orElse("").toLowerCase(Locale.ROOT);
        if (suffix.contains("jpg") || suffix.contains("jpeg")) {
            return ".jpg";
        }
        if (suffix.contains("png")) {
            return ".png";
        }
        if (suffix.contains("tif")) {
            return ".tiff";
        }
        return ".png";
    }

    private String pictureSuffix(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return ".png";
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private List<String> splitByParagraphs(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return Arrays.stream(text.split("\\n\\s*\\n+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private boolean isLikelyTableBlock(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String[] lines = text.split("\\R");
        long structuredLines = Arrays.stream(lines)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(line -> line.contains("\t") || line.matches(".*\\s{2,}.*"))
                .count();
        return structuredLines > 0 && lines.length >= 2;
    }

    private String normalizeTableBlock(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(line -> line.replaceAll("\\s{2,}", " | "))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(text.trim());
    }

    private boolean isHeadingParagraph(XWPFParagraph paragraph, String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String style = Optional.ofNullable(paragraph.getStyleID()).orElse("");
        if (style.toLowerCase(Locale.ROOT).contains("heading")) {
            return true;
        }
        return text.length() <= 48
                && (text.endsWith("：")
                || text.endsWith(":")
                || text.matches("^[一二三四五六七八九十0-9]+[、\\.].*"));
    }

    private String extractKeywords(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String result = text;
        String[] stopWords = {"的", "了", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很",
                "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这"};
        for (String stopWord : stopWords) {
            result = result.replace(stopWord, "");
        }
        result = result.replaceAll("\\s+", " ").trim();
        if (result.length() > 200) {
            result = result.substring(0, 200);
        }
        return result;
    }

    private boolean isOcrUnavailable(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return false;
        }
        String lower = errorMessage.toLowerCase(Locale.ROOT);
        return lower.contains("no such file or directory")
                || lower.contains("cannot run program")
                || lower.contains("not found")
                || lower.contains("ocr command is not configured");
    }

    private String safeText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String resolveFileName(MultipartFile file) {
        return Optional.ofNullable(file.getOriginalFilename())
                .filter(StringUtils::hasText)
                .orElse("document-" + UUID.randomUUID());
    }

    private record ParseResult(String message, int totalChunks, Path storedPath) {
    }
}
