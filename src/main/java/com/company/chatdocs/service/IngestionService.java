package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.event.DocumentUploadedEvent;
import com.company.chatdocs.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RAG step 1 (ingestion): read an uploaded file and split it into chunks.
 * Phase 10 stops after splitting; Phase 11 adds embedding and storing the chunks.
 */
@Service
public class IngestionService {

	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	private static final int MAX_ERROR_LENGTH = 500;

	private final DocumentRepository documents;
	private final DocumentReaderFactory readerFactory;
	private final TokenTextSplitter splitter;

	IngestionService(DocumentRepository documents, DocumentReaderFactory readerFactory, AppProperties properties) {
		this.documents = documents;
		this.readerFactory = readerFactory;
		this.splitter = TokenTextSplitter.builder().withChunkSize(properties.ingestion().chunkSize()).build();
	}

	/** Runs on a background (virtual) thread, and only after the upload's transaction has committed. */
	@Async
	@TransactionalEventListener
	public void onDocumentUploaded(DocumentUploadedEvent event) {
		ingest(event.documentId());
	}

	/** Reads and splits one document, recording READY with the chunk count, or FAILED with the reason. */
	public void ingest(UUID documentId) {
		var document = documents.findById(documentId).orElse(null);
		if (document == null) {
			log.info("Document {} was deleted before ingestion started", documentId);
			return;
		}
		documents.updateStatus(documentId, DocumentStatus.PROCESSING, 0, null, Instant.now());

		try {
			List<Document> pages = readerFactory.read(document);
			List<Document> chunks = splitter.apply(pages);
			if (chunks.isEmpty()) {
				throw new IllegalStateException("No extractable text. Scanned PDFs without a text layer aren't supported.");
			}
			log.info("Split '{}' into {} chunks from {} page(s)", document.getFileName(), chunks.size(), pages.size());
			documents.updateStatus(documentId, DocumentStatus.READY, chunks.size(), null, Instant.now());
		}
		catch (Exception e) {
			log.warn("Ingestion failed for '{}' ({})", document.getFileName(), documentId, e);
			documents.updateStatus(documentId, DocumentStatus.FAILED, 0, errorMessage(e), Instant.now());
		}
	}

	private static String errorMessage(Exception e) {
		Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
		String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
		if (!(cause instanceof IllegalStateException)) {
			message = "Could not read the file: " + message;
		}
		return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
	}

}
