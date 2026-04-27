## ADDED Requirements

### Requirement: Document upload and classification
The system MUST allow administrators to upload knowledge source documents and classify each document as a PDF operation manual or a Word FAQ document.

#### Scenario: Upload a PDF operation manual
- **WHEN** an administrator uploads a PDF file and selects the operation manual type
- **THEN** the system SHALL create a document record in processing state and store the source file metadata

#### Scenario: Upload a Word FAQ document
- **WHEN** an administrator uploads a Word file and selects the FAQ type
- **THEN** the system SHALL create a document record in processing state and store the source file metadata

### Requirement: Text and structure extraction
The system MUST extract text, headings, tables, lists, and paragraph structure from uploaded documents and preserve source location metadata when available.

#### Scenario: Extract sections from a PDF manual
- **WHEN** a PDF document is processed
- **THEN** the system SHALL extract section text and preserve page number or section path metadata for each extracted chunk

#### Scenario: Extract FAQ entries from Word
- **WHEN** a Word FAQ document is processed
- **THEN** the system SHALL extract question-answer content and preserve source paragraph or table location metadata for each extracted chunk

### Requirement: Image text extraction
The system MUST detect images embedded in uploaded documents and extract text from those images using OCR or an equivalent image text extraction mechanism.

#### Scenario: OCR a screenshot in a PDF manual
- **WHEN** a PDF page contains an embedded screenshot or flowchart image with text
- **THEN** the system SHALL extract the image text and attach it to the corresponding document chunk with page and image references

#### Scenario: OCR an image in a Word document
- **WHEN** a Word document contains an embedded image with text
- **THEN** the system SHALL extract the image text and attach it to the corresponding knowledge chunk with image references

### Requirement: Review and publishing workflow
The system MUST create extracted knowledge chunks in a reviewable state and only allow published chunks to participate in chat retrieval.

#### Scenario: Review extracted content
- **WHEN** document parsing completes successfully
- **THEN** the system SHALL make the extracted chunks available for preview and editing before publication

#### Scenario: Use only published chunks for chat
- **WHEN** a user asks a question in chat
- **THEN** the system SHALL search only published knowledge chunks and ignore draft or failed chunks

### Requirement: Source traceability
The system MUST retain the original document, chunk, page, section, and image source references for every published knowledge chunk.

#### Scenario: Show source of an answer
- **WHEN** a published knowledge chunk is returned during retrieval
- **THEN** the system SHALL provide the original document name and source reference metadata for audit and review

#### Scenario: Trace OCR content back to image
- **WHEN** a chunk includes text extracted from a document image
- **THEN** the system SHALL retain the page number and image index for traceability

### Requirement: Parsing failure handling
The system MUST mark documents or chunks that cannot be parsed or OCR processed as failed and allow administrators to retry or manually correct them.

#### Scenario: OCR fails on a scanned page
- **WHEN** image text extraction fails for a scanned page
- **THEN** the system SHALL mark the affected chunk or document as failed and preserve the failure reason

#### Scenario: Retry a failed document
- **WHEN** an administrator retries a failed document
- **THEN** the system SHALL re-run the parsing pipeline without losing the original uploaded file
