package com.gov.assistant.service.document;

import com.gov.assistant.entity.document.KnowledgeChunk;
import com.gov.assistant.entity.document.KnowledgeDocument;
import com.gov.assistant.repository.document.KnowledgeChunkRepository;
import com.gov.assistant.repository.document.KnowledgeDocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentKnowledgeServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private KnowledgeChunkRepository chunkRepository;

    @Mock
    private ImageOcrAdapter imageOcrAdapter;

    @TempDir
    Path tempDir;

    private KnowledgeDocumentStorageService storageService;
    private DocumentKnowledgeService documentKnowledgeService;

    @BeforeEach
    void setUp() {
        storageService = new KnowledgeDocumentStorageService(tempDir.toString());
        documentKnowledgeService = new DocumentKnowledgeService(
                documentRepository,
                chunkRepository,
                storageService,
                imageOcrAdapter);

        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldIngestPdfAndRecordOcrFailure() throws Exception {
        when(imageOcrAdapter.extractText(any()))
                .thenReturn(ImageOcrAdapter.OcrResult.failure("ocr failed"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.pdf",
                "application/pdf",
                createPdfBytes(true));

        KnowledgeDocument document = documentKnowledgeService.ingestDocument(
                file,
                KnowledgeDocument.DocumentType.PDF_OPERATION_MANUAL,
                "admin");

        assertThat(document.getStatus()).isEqualTo(KnowledgeDocument.Status.PENDING_REVIEW);
        assertThat(document.getTotalChunks()).isGreaterThan(0);
        assertThat(document.getParseMessage()).contains("OCR失败");

        ArgumentCaptor<KnowledgeChunk> captor = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkRepository, atLeastOnce()).save(captor.capture());
        List<KnowledgeChunk> chunks = captor.getAllValues();
        assertThat(chunks).anyMatch(chunk -> chunk.getStatus() == KnowledgeChunk.ChunkStatus.FAILED);
        assertThat(chunks).anyMatch(chunk -> "第1页正文".equals(chunk.getChunkTitle()));
    }

    @Test
    void shouldIngestWordAndExtractTableAndImage() throws Exception {
        when(imageOcrAdapter.extractText(any()))
                .thenReturn(ImageOcrAdapter.OcrResult.success("图中文字"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "faq.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                createWordBytes(true));

        KnowledgeDocument document = documentKnowledgeService.ingestDocument(
                file,
                KnowledgeDocument.DocumentType.WORD_FAQ,
                "admin");

        assertThat(document.getStatus()).isEqualTo(KnowledgeDocument.Status.PENDING_REVIEW);
        assertThat(document.getTotalChunks()).isGreaterThanOrEqualTo(2);
        assertThat(document.getParseMessage()).contains("解析完成");

        ArgumentCaptor<KnowledgeChunk> captor = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkRepository, atLeastOnce()).save(captor.capture());
        List<KnowledgeChunk> chunks = captor.getAllValues();
        assertThat(chunks).anyMatch(chunk -> "如何登录".equals(chunk.getChunkTitle()));
        assertThat(chunks).anyMatch(chunk -> "图中文字".equals(chunk.getOcrText()));
    }

    @Test
    void shouldRetryExistingDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.pdf",
                "application/pdf",
                createPdfBytes(false));

        KnowledgeDocument uploaded = documentKnowledgeService.ingestDocument(
                file,
                KnowledgeDocument.DocumentType.PDF_OPERATION_MANUAL,
                "admin");

        KnowledgeDocument existing = new KnowledgeDocument();
        existing.setId(uploaded.getId());
        existing.setDocumentCode(uploaded.getDocumentCode());
        existing.setFilePath(uploaded.getFilePath());
        existing.setDocumentType(KnowledgeDocument.DocumentType.PDF_OPERATION_MANUAL);
        existing.setStatus(KnowledgeDocument.Status.FAILED);

        when(documentRepository.findById(99L)).thenReturn(java.util.Optional.of(existing));
        when(chunkRepository.findByDocumentIdOrderByChunkOrderAsc(99L)).thenReturn(List.of());

        KnowledgeDocument retried = documentKnowledgeService.retryDocument(99L);

        assertThat(retried.getStatus()).isEqualTo(KnowledgeDocument.Status.PENDING_REVIEW);
        assertThat(retried.getTotalChunks()).isGreaterThan(0);
        assertThat(retried.getParseMessage()).contains("PDF解析完成");
    }

    private byte[] createPdfBytes(boolean withImage) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 760);
                contentStream.showText("Apply flow");
                contentStream.endText();

                if (withImage) {
                    BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics();
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, 40, 40);
                    graphics.setColor(Color.BLACK);
                    graphics.drawLine(0, 0, 39, 39);
                    graphics.dispose();

                    ByteArrayOutputStream imageOutput = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", imageOutput);
                    PDImageXObject xImage = PDImageXObject.createFromByteArray(document, imageOutput.toByteArray(), "test");
                    contentStream.drawImage(xImage, 50, 680, 40, 40);
                }
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createWordBytes(boolean withImage) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("常见问题");

            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("如何登录");
            table.getRow(0).getCell(1).setText("请使用手机号登录");

            if (withImage) {
                BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, 40, 40);
                graphics.setColor(Color.BLACK);
                graphics.drawOval(6, 6, 28, 28);
                graphics.dispose();

                ByteArrayOutputStream imageOutput = new ByteArrayOutputStream();
                ImageIO.write(image, "png", imageOutput);

                XWPFRun run = document.createParagraph().createRun();
                run.addPicture(
                        new ByteArrayInputStream(imageOutput.toByteArray()),
                        org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,
                        "test.png",
                        Units.toEMU(40),
                        Units.toEMU(40));
            }

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
