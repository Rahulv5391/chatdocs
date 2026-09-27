package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import com.company.chatdocs.dto.DocumentDto;
import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.event.DocumentUploadedEvent;
import com.company.chatdocs.exception.DocumentNotFoundException;
import com.company.chatdocs.exception.InvalidDocumentStateException;
import com.company.chatdocs.exception.UploadRejectedException;
import com.company.chatdocs.repository.DocumentRepository;
import com.vaadin.hilla.BrowserCallable;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@BrowserCallable
@PermitAll
public class DocumentService {

	/** Allowed file extensions and the content type we store for each. */
	private static final Map<String, String> ALLOWED_TYPES = Map.of(
			"pdf", "application/pdf",
			"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"txt", "text/plain",
			"md", "text/markdown");

	private final DocumentRepository documents;
	private final CurrentUser currentUser;
	private final FileStorageService storage;
	private final AppProperties properties;
	private final ApplicationEventPublisher events;
	private final IngestionService ingestion;

	DocumentService(DocumentRepository documents, CurrentUser currentUser, FileStorageService storage,
			AppProperties properties, ApplicationEventPublisher events, IngestionService ingestion) {
		this.documents = documents;
		this.currentUser = currentUser;
		this.storage = storage;
		this.properties = properties;
		this.events = events;
		this.ingestion = ingestion;
	}

	/** Returns only the logged-in user's documents, newest first. */
	@Transactional(readOnly = true)
	public @NonNull List<@NonNull DocumentDto> list() {
		return documents.findByOwnerIdOrderByCreatedAtDesc(currentUser.get().getId()).stream()
				.map(DocumentDto::from)
				.toList();
	}

	/**
	 * Validates the file, saves it to disk and creates a document row with status UPLOADED.
	 * Ingestion then runs in the background.
	 *
	 * @throws UploadRejectedException with a user-friendly message if the file is not accepted
	 */
	@Transactional
	public @NonNull DocumentDto upload(@NonNull MultipartFile file) {
		String fileName = StringUtils.getFilename(StringUtils.cleanPath(String.valueOf(file.getOriginalFilename())));
		String extension = String.valueOf(StringUtils.getFilenameExtension(fileName)).toLowerCase(Locale.ROOT);

		if (!ALLOWED_TYPES.containsKey(extension)) {
			throw new UploadRejectedException("Only PDF, DOCX, TXT and MD files are supported.");
		}
		if (file.isEmpty()) {
			throw new UploadRejectedException("The file is empty.");
		}
		if (file.getSize() > properties.maxUploadSize().toBytes()) {
			throw new UploadRejectedException(
					"The file is too large. The limit is " + properties.maxUploadSize().toMegabytes() + " MB.");
		}

		byte[] content = readBytes(file);
		String checksum = sha256(content);
		AppUser owner = currentUser.get();
		if (documents.existsByOwnerIdAndChecksumSha256(owner.getId(), checksum)) {
			throw new UploadRejectedException("You have already uploaded this file.");
		}

		String storagePath = storage.save(owner.getId(), extension, content);
		try {
			Document document = documents.saveAndFlush(new Document(owner, fileName, ALLOWED_TYPES.get(extension),
					content.length, storagePath, checksum));
			// IngestionService picks this up after the transaction commits.
			events.publishEvent(new DocumentUploadedEvent(document.getId()));
			return DocumentDto.from(document);
		}
		catch (RuntimeException e) {
			// Don't leave a file on disk without a matching row.
			storage.delete(storagePath);
			throw e;
		}
	}

	/**
	 * Deletes one of the logged-in user's documents: the row, its chunks in the vector store, then the file.
	 *
	 * @throws DocumentNotFoundException if the id doesn't exist or belongs to another user
	 */
	@Transactional
	public void delete(@NonNull UUID id) {
		Document document = documents.findByIdAndOwnerId(id, currentUser.get().getId())
				.orElseThrow(DocumentNotFoundException::new);
		documents.delete(document);
		// Flush so a database error happens before the file is gone; a failed file delete rolls the row back.
		documents.flush();
		// The vector store uses the same database connection, so this is part of the same transaction.
		ingestion.deleteChunks(id);
		storage.delete(document.getStoragePath());
	}

	/**
	 * Runs ingestion again for a FAILED document. Old chunks are cleared when ingestion starts.
	 *
	 * @throws DocumentNotFoundException      if the id doesn't exist or belongs to another user
	 * @throws InvalidDocumentStateException if the document isn't FAILED
	 */
	@Transactional
	public @NonNull DocumentDto reprocess(@NonNull UUID id) {
		Document document = documents.findByIdAndOwnerId(id, currentUser.get().getId())
				.orElseThrow(DocumentNotFoundException::new);
		if (document.getStatus() != DocumentStatus.FAILED) {
			throw new InvalidDocumentStateException("Only failed documents can be reprocessed.");
		}
		document.requeue();
		events.publishEvent(new DocumentUploadedEvent(id));
		return DocumentDto.from(document);
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not read the uploaded file", e);
		}
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

}
