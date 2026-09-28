package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import com.company.chatdocs.entity.DocumentFile;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.event.DocumentUploadedEvent;
import com.company.chatdocs.exception.AiServiceException;
import com.company.chatdocs.repository.DocumentFileRepository;
import com.company.chatdocs.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG step 1 (ingestion): read an uploaded file, split it into chunks, embed the chunks and store them in pgvector.
 */
@Service
public class IngestionService {

	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	private static final int MAX_ERROR_LENGTH = 500;

	private final DocumentRepository documents;
	private final DocumentFileRepository files;
	private final DocumentReaderFactory readerFactory;
	private final EmbeddingThrottler embeddingThrottler;
	private final VectorStore vectorStore;
	private final TokenTextSplitter splitter;

	IngestionService(DocumentRepository documents, DocumentFileRepository files, DocumentReaderFactory readerFactory,
			EmbeddingThrottler embeddingThrottler, VectorStore vectorStore, AppProperties properties) {
		this.documents = documents;
		this.files = files;
		this.readerFactory = readerFactory;
		this.embeddingThrottler = embeddingThrottler;
		this.vectorStore = vectorStore;
		this.splitter = TokenTextSplitter.builder().withChunkSize(properties.ingestion().chunkSize()).build();
	}

	/** Runs on a background (virtual) thread, and only after the upload's transaction has committed. */
	@Async
	@TransactionalEventListener
	public void onDocumentUploaded(DocumentUploadedEvent event) {
		ingest(event.documentId());
	}

	/**
	 * Ingestion runs inside this app, so after a restart nothing is still working on UPLOADED or PROCESSING rows.
	 * Mark them FAILED so the user sees what happened and can press Reprocess.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void failInterruptedDocuments() {
		int count = documents.failUnfinished("Processing was interrupted by a restart. Click Reprocess to try again.",
				Instant.now());
		if (count > 0) {
			log.warn("Marked {} interrupted document(s) as FAILED", count);
		}
	}

	/** Removes all stored chunks of a document. Safe to call when there are none. */
	public void deleteChunks(UUID documentId) {
		vectorStore.delete(new FilterExpressionBuilder().eq(ChunkMetadata.DOCUMENT_ID, documentId.toString()).build());
	}

	/** Processes one document, recording READY with the chunk count, or FAILED with a readable reason. */
	public void ingest(UUID documentId) {
		var document = documents.findById(documentId).orElse(null);
		if (document == null) {
			log.info("Document {} was deleted before ingestion started", documentId);
			return;
		}
		documents.updateStatus(documentId, DocumentStatus.PROCESSING, 0, null, Instant.now());
		// Idempotent: a reprocessed document never ends up with old and new chunks side by side.
		deleteChunks(documentId);

		try {
			List<Document> chunks = withMetadata(document, splitter.apply(read(document)));
			if (chunks.isEmpty()) {
				throw new IngestionException("No extractable text. The file has too little text to index.");
			}
			embed(documentId, chunks);
			log.info("Ingested '{}': {} chunks embedded and stored", document.getFileName(), chunks.size());
			documents.updateStatus(documentId, DocumentStatus.READY, chunks.size(), null, Instant.now());
		}
		catch (IngestionException e) {
			log.warn("Ingestion failed for '{}' ({}): {}", document.getFileName(), documentId, e.getMessage(), e);
			documents.updateStatus(documentId, DocumentStatus.FAILED, 0, truncate(e.getMessage()), Instant.now());
		}
	}

	private List<Document> read(com.company.chatdocs.entity.Document document) {
		DocumentFile file = files.findById(document.getId())
				.orElseThrow(() -> new IngestionException("The original file is missing. Please upload it again."));
		List<Document> pages;
		try {
			pages = readerFactory.read(document, file.getContent());
		}
		catch (RuntimeException e) {
			throw new IngestionException("Could not read the file: " + rootMessage(e), e);
		}
		if (pages.stream().allMatch(page -> page.getText() == null || page.getText().isBlank())) {
			throw new IngestionException("No extractable text. Scanned PDFs without a text layer aren't supported.");
		}
		return pages;
	}

	/** Replaces the reader's metadata with the {@link ChunkMetadata} keys. */
	private static List<Document> withMetadata(com.company.chatdocs.entity.Document document, List<Document> chunks) {
		List<Document> result = new ArrayList<>(chunks.size());
		for (int i = 0; i < chunks.size(); i++) {
			Document chunk = chunks.get(i);
			Map<String, Object> metadata = new HashMap<>();
			metadata.put(ChunkMetadata.DOCUMENT_ID, document.getId().toString());
			metadata.put(ChunkMetadata.USER_ID, document.getOwnerId().toString());
			metadata.put(ChunkMetadata.FILE_NAME, document.getFileName());
			metadata.put(ChunkMetadata.CHUNK_INDEX, i);
			Object page = chunk.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
			if (page != null) {
				metadata.put(ChunkMetadata.PAGE, page);
			}
			result.add(new Document(chunk.getText(), metadata));
		}
		return result;
	}

	private void embed(UUID documentId, List<Document> chunks) {
		try {
			embeddingThrottler.addInBatches(chunks);
		}
		catch (RuntimeException e) {
			// Don't leave half a document searchable.
			deleteChunks(documentId);
			String reason = AiServiceException.isRateLimited(e)
					? "Gemini quota exceeded. Try again later."
					: "Could not create embeddings: " + rootMessage(e);
			throw new IngestionException(reason, e);
		}
	}

	private static String rootMessage(Throwable e) {
		Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
		return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
	}

	private static String truncate(String message) {
		return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
	}

	/** A failure with a message that is safe and useful to show to the user. */
	private static class IngestionException extends RuntimeException {

		IngestionException(String message) {
			super(message);
		}

		IngestionException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
