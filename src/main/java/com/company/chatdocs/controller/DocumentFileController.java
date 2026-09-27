package com.company.chatdocs.controller;

import com.company.chatdocs.entity.Document;
import com.company.chatdocs.repository.DocumentRepository;
import com.company.chatdocs.service.CurrentUser;
import com.company.chatdocs.service.FileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Serves the original uploaded file, so a citation chip can open it (PDFs at the cited page via #page=N).
 * Hilla endpoints are POST-only, so a file download needs this plain GET controller. Spring Security already
 * requires a login for it; the ownership check here makes other users' files look like they don't exist.
 */
@RestController
public class DocumentFileController {

	private final DocumentRepository documents;
	private final CurrentUser currentUser;
	private final FileStorageService storage;

	DocumentFileController(DocumentRepository documents, CurrentUser currentUser, FileStorageService storage) {
		this.documents = documents;
		this.currentUser = currentUser;
		this.storage = storage;
	}

	@GetMapping("/api/documents/{id}/file")
	public ResponseEntity<Resource> file(@PathVariable UUID id) {
		Document document = documents.findByIdAndOwnerId(id, currentUser.get().getId()).orElse(null);
		if (document == null) {
			return ResponseEntity.notFound().build();
		}
		Path path = storage.resolve(document.getStoragePath());
		if (!Files.exists(path)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(document.getContentType()))
				// inline = show in the browser tab (PDF viewer) instead of downloading
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
						.filename(document.getFileName(), StandardCharsets.UTF_8)
						.build()
						.toString())
				.body(new FileSystemResource(path));
	}

}
