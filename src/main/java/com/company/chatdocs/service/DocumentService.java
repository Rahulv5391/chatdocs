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
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@BrowserCallable
@PermitAll
public class DocumentService {

	/** Allowed file extensions and the content type we store for each. */
	private static final Map<String, String> ALLOWED_TYPES = Map.of(
			"pdf", "application/pdf",
			"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
			"html", "text/html",
			"htm", "text/html",
			"epub", "application/epub+zip",
			"txt", "text/plain",
			"md", "text/markdown");

	static final String UNSUPPORTED_TYPE_MESSAGE =
			"Only PDF, DOCX, XLSX, PPTX, HTML, EPUB, TXT and MD files are supported.";

	/** Content types a web address may return, and the extension the document is stored under. */
	private static final Map<String, String> URL_CONTENT_TYPES = Map.of(
			"text/html", "html",
			"application/xhtml+xml", "html",
			"application/pdf", "pdf",
			"text/plain", "txt",
			"text/markdown", "md");

	private static final Pattern HTML_TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

	/** Characters that aren't allowed in file names on common systems. */
	private static final Pattern UNSAFE_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

	private static final int MAX_IMPORTED_NAME_LENGTH = 100;

	private final DocumentRepository documents;
	private final CurrentUser currentUser;
	private final DocumentFileRepository files;
	private final AppProperties properties;
	private final ApplicationEventPublisher events;
	private final IngestionService ingestion;
	private final UrlFetcher urlFetcher;
	private final TransactionTemplate transaction;

	DocumentService(DocumentRepository documents, DocumentFileRepository files, CurrentUser currentUser,
			AppProperties properties, ApplicationEventPublisher events, IngestionService ingestion,
			UrlFetcher urlFetcher, PlatformTransactionManager transactionManager) {
		this.documents = documents;
		this.files = files;
		this.currentUser = currentUser;
		this.properties = properties;
		this.events = events;
		this.ingestion = ingestion;
		this.urlFetcher = urlFetcher;
		this.transaction = new TransactionTemplate(transactionManager);
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
			throw new UploadRejectedException(UNSUPPORTED_TYPE_MESSAGE);
		}
		if (file.isEmpty()) {
			throw new UploadRejectedException("The file is empty.");
		}
		if (file.getSize() > properties.maxUploadSize().toBytes()) {
			throw new UploadRejectedException(
					"The file is too large. The limit is " + properties.maxUploadSize().toMegabytes() + " MB.");
		}

		return save(fileName, ALLOWED_TYPES.get(extension), readBytes(file), null);
	}

	/**
	 * Downloads a web page (or a PDF or text file) and adds it like an upload. A web page is named after its title.
	 * The download happens before any database transaction is opened.
	 *
	 * @throws UploadRejectedException with a user-friendly message if the address can't be imported
	 */
	public @NonNull DocumentDto importUrl(@NonNull String url) {
		UrlFetcher.Page page = urlFetcher.fetch(url);
		String mediaType = mediaType(page.contentType());
		String extension = URL_CONTENT_TYPES.get(mediaType);
		if (extension == null) {
			throw new UploadRejectedException("That address returned " + (mediaType.isEmpty() ? "an unknown type"
					: mediaType) + ", which isn't supported. Web pages, PDFs and plain text files are.");
		}
		if (page.content().length == 0) {
			throw new UploadRejectedException("The page is empty.");
		}
		String fileName = importedFileName(page, extension);
		return transaction.execute(
				status -> save(fileName, ALLOWED_TYPES.get(extension), page.content(), page.uri().toString()));
	}

	/** Saves the file and its document row (status UPLOADED); ingestion starts after the transaction commits. */
	private DocumentDto save(String fileName, String contentType, byte[] content, @Nullable String sourceUrl) {
		String checksum = sha256(content);
		AppUser owner = currentUser.get();
		if (documents.existsByOwnerIdAndChecksumSha256(owner.getId(), checksum)) {
			throw new UploadRejectedException(sourceUrl == null
					? "You have already uploaded this file."
					: "You already have a document with exactly this content.");
		}

		Document document = documents.saveAndFlush(
				new Document(owner, fileName, contentType, content.length, checksum, sourceUrl));
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
	 * Runs ingestion again for a FAILED document, or for a READY one (e.g. to pick up improved chunking).
	 * Old chunks are cleared when ingestion starts.
	 *
	 * @throws DocumentNotFoundException      if the id doesn't exist or belongs to another user
	 * @throws InvalidDocumentStateException if the document is still queued or processing
	 */
	@Transactional
	public @NonNull DocumentDto reprocess(@NonNull UUID id) {
		Document document = ownDocument(id);
		if (document.getStatus() != DocumentStatus.FAILED && document.getStatus() != DocumentStatus.READY) {
			throw new InvalidDocumentStateException("This document is still being processed.");
		}
		document.requeue();
		events.publishEvent(new DocumentUploadedEvent(id));
		return DocumentDto.from(document);
	}

	private Document ownDocument(UUID id) {
		return documents.findByIdAndOwnerId(id, currentUser.get().getId()).orElseThrow(DocumentNotFoundException::new);
	}

	/** "text/html; charset=utf-8" becomes "text/html"; a missing or unparseable header becomes "". */
	private static String mediaType(String contentType) {
		try {
			MimeType type = MimeType.valueOf(contentType);
			return (type.getType() + "/" + type.getSubtype()).toLowerCase(Locale.ROOT);
		}
		catch (IllegalArgumentException e) {
			return "";
		}
	}

	/** The page title for HTML, otherwise the last part of the address, made safe as a file name. */
	private static String importedFileName(UrlFetcher.Page page, String extension) {
		String name = null;
		if (extension.equals("html")) {
			String head = new String(page.content(), 0, Math.min(page.content().length, 65_536), StandardCharsets.UTF_8);
			Matcher title = HTML_TITLE.matcher(head);
			if (title.find()) {
				name = HtmlUtils.htmlUnescape(title.group(1));
			}
		}
		if (name == null || name.isBlank()) {
			String path = page.uri().getPath() == null ? "" : page.uri().getPath();
			name = StringUtils.getFilename(StringUtils.trimTrailingCharacter(path, '/'));
		}
		name = name == null ? "" : UNSAFE_NAME_CHARS.matcher(name).replaceAll(" ").replaceAll("\\s+", " ").strip();
		if (name.length() > MAX_IMPORTED_NAME_LENGTH) {
			name = name.substring(0, MAX_IMPORTED_NAME_LENGTH).strip();
		}
		if (name.isEmpty()) {
			name = page.uri().getHost();
		}
		return name.toLowerCase(Locale.ROOT).endsWith("." + extension) ? name : name + "." + extension;
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
