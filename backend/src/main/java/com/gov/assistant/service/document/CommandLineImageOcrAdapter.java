package com.gov.assistant.service.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class CommandLineImageOcrAdapter implements ImageOcrAdapter {

    private final String ocrCommand;
    private final long timeoutSeconds;

    public CommandLineImageOcrAdapter(
            @Value("${gov.assistant.ocr-command:tesseract}") String ocrCommand,
            @Value("${gov.assistant.ocr-timeout-seconds:30}") long timeoutSeconds) {
        this.ocrCommand = ocrCommand;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public OcrResult extractText(Path imagePath) {
        if (!StringUtils.hasText(ocrCommand)) {
            return OcrResult.failure("OCR command is not configured");
        }

        try {
            Path outputBase = Files.createTempFile("ocr-output-", "");
            Files.deleteIfExists(outputBase);

            List<String> command = List.of(
                    ocrCommand,
                    imagePath.toAbsolutePath().toString(),
                    outputBase.toAbsolutePath().toString(),
                    "-l",
                    "chi_sim+eng",
                    "txt"
            );

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                return OcrResult.failure("OCR command timed out");
            }

            int exit = process.exitValue();
            Path txtFile = Path.of(outputBase.toString() + ".txt");
            if (exit != 0) {
                return OcrResult.failure("OCR command exited with code " + exit + ": " + output);
            }

            if (!Files.exists(txtFile)) {
                return OcrResult.failure("OCR output file was not produced");
            }

            String text = Files.readString(txtFile, StandardCharsets.UTF_8).trim();
            if (!StringUtils.hasText(text)) {
                return OcrResult.failure("OCR extracted no text");
            }

            return OcrResult.success(text);
        } catch (IOException e) {
            log.warn("OCR extraction failed for {}: {}", imagePath, e.getMessage());
            return OcrResult.failure(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OcrResult.failure("OCR interrupted");
        }
    }
}
