## 1. Data Model and Storage

- [x] 1.1 Add document and knowledge-chunk entities, repositories, and statuses for uploaded files, parsing state, and published chunks
- [x] 1.2 Add database migration or schema update for document metadata, chunk provenance, and OCR source references
- [x] 1.3 Add storage handling for original PDF/Word files and extracted image assets

## 2. Document Parsing Pipeline

- [x] 2.1 Implement a document ingestion service for PDF and Word uploads with validation and file-type detection
- [x] 2.2 Implement PDF text extraction with section, page, and table metadata preservation
- [x] 2.3 Implement Word text extraction with heading, table, and FAQ structure preservation
- [x] 2.4 Implement chunk generation and draft review state creation after parsing

## 3. Image OCR Support

- [x] 3.1 Detect embedded images in uploaded documents and extract image assets during parsing
- [x] 3.2 Add an OCR adapter interface and default implementation for image text extraction
- [x] 3.3 Attach OCR output to knowledge chunks with page, image, and source-file traceability
- [x] 3.4 Mark image OCR failures with retryable error details and preserve the original upload

## 4. Admin APIs and UI

- [x] 4.1 Add admin endpoints for document upload, parse status, chunk preview, publish, retry, and rejection
- [x] 4.2 Replace the current knowledge list UI with a document-centric management view
- [x] 4.3 Add upload, preview, publish, and retry controls for parsed documents and chunks
- [x] 4.4 Keep a small manual knowledge entry path for emergency or fallback content

## 5. Retrieval and Verification

- [x] 5.1 Update chat retrieval to search published chunks before falling back to manual entries or AI generation
- [x] 5.2 Surface original document and source metadata in retrieval results for auditing
- [x] 5.3 Add tests for PDF, Word, and image OCR flows, including failure and retry cases
