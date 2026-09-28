package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import com.company.chatdocs.dto.DocumentDto;
import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.entity.DocumentFile;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.event.DocumentUploadedEvent;
import com.company.chatdocs.exception.DocumentNotFoundException;
import com.company.chatdocs.exception.InvalidDocumentStateException;
import com.company.chatdocs.exception.UploadRejectedException;
import com.company.chatdocs.repository.DocumentFileRepository;
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
	private final DocumentFileRepository files;
	private final AppProperties properties;
	private final ApplicationEventPublisher events;
	private final IngestionService ingestion;

	DocumentService(DocumentRepository documents, DocumentFileRepository files, CurrentUser currentUser,
			AppProperties properties, ApplicationEventPublisher events, IngestionService ingestion) {
		this.documents = documents;
		this.files = files;
		this.currentUser = currentUser;
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
	 * Validates the file and saves it, together with a document row with status UPLOADED, in one transaction.
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

		Document document = documents.saveAndFlush(
				new Document(owner, fileName, ALLOWED_TYPES.get(extension), content.length, checksum));
		files.save(new DocumentFile(document.getId(), content));
		// IngestionService picks this up after the transaction commits.
		events.publishEvent(new DocumentUploadedEvent(document.getId()));
		return DocumentDto.from(document);
	}

	/**
	 * Deletes one of the logged-in user's documents: the row (the database deletes its file with it) and its chunks
	 * in the vector store, all in one transaction.
	 *
	 * @throws DocumentNotFoundException if the id doesn't exist or belongs to another user
	 */
	@Transactional
	public void delete(@NonNull UUID id) {
		Document document = ownDocument(id);
		documents.delete(document);
		// The vector store uses the same database connection, so this is part of the same transaction.
		ingestion.deleteChunks(id);
	}

	/**
	 * Runs ingestion again for a FAILED document. Old chunks are cleared when ingestion starts.
	 *
	 * @throws DocumentNotFoundException      if the id doesn't exist or belongs to another user
	 * @throws InvalidDocumentStateException if the document isn't FAILED
	 */
	@Transactional
	public @NonNull DocumentDto reprocess(@NonNull UUID id) {
		Document document = ownDocument(id);
		if (document.getStatus() != DocumentStatus.FAILED) {
			throw new InvalidDocumentStateException("Only failed documents can be reprocessed.");
		}
		document.requeue();
		events.publishEvent(new DocumentUploadedEvent(id));
		return DocumentDto.from(document);
	}

	private Document ownDocument(UUID id) {
		return documents.findByIdAndOwnerId(id, currentUser.get().getId()).orElseThrow(DocumentNotFoundException::new);
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
