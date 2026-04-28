package com.gov.assistant.service.document;

import java.nio.file.Path;

public interface ImageOcrAdapter {

    OcrResult extractText(Path imagePath);

    record OcrResult(boolean success, String text, String errorMessage) {
        public static OcrResult success(String text) {
            return new OcrResult(true, text, null);
        }

        public static OcrResult failure(String errorMessage) {
            return new OcrResult(false, "", errorMessage);
        }
    }
}
