package com.company.chatdocs.service;

import com.company.chatdocs.dto.DocumentDto;
import com.company.chatdocs.repository.DocumentRepository;
import com.vaadin.hilla.BrowserCallable;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.NonNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@BrowserCallable
@PermitAll
public class DocumentService {

	private final DocumentRepository documents;
	private final CurrentUser currentUser;

	DocumentService(DocumentRepository documents, CurrentUser currentUser) {
		this.documents = documents;
		this.currentUser = currentUser;
	}

	/** Returns only the logged-in user's documents, newest first. */
	@Transactional(readOnly = true)
	public @NonNull List<@NonNull DocumentDto> list() {
		return documents.findByOwnerIdOrderByCreatedAtDesc(currentUser.get().getId()).stream()
				.map(DocumentDto::from)
				.toList();
	}

}
