package com.company.chatdocs.service;

import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.exception.DocumentNotFoundException;
import com.company.chatdocs.repository.AppUserRepository;
import com.company.chatdocs.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage-dir=target/test-uploads")
@ActiveProfiles("dev")
@WithMockUser(username = "demo")
class DocumentDeleteTests {

	@Autowired
	DocumentService documentService;

	@Autowired
	DocumentRepository documents;

	@Autowired
	AppUserRepository users;

	@Autowired
	FileStorageService storage;

	@AfterEach
	void cleanUp() {
		documents.findAll().forEach(document -> storage.delete(document.getStoragePath()));
		documents.deleteAll();
	}

	@Test
	void deletesOwnDocumentRowAndFile() {
		var dto = documentService.upload(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()));
		var path = storage.resolve(documents.findById(dto.id()).orElseThrow().getStoragePath());
		assertThat(path).exists();

		documentService.delete(dto.id());

		assertThat(documents.findById(dto.id())).isEmpty();
		assertThat(path).doesNotExist();
	}

	@Test
	void cannotDeleteAnotherUsersDocument() {
		var alice = users.findByUsername("alice").orElseThrow();
		String storagePath = storage.save(alice.getId(), "txt", "alice's secret".getBytes());
		Document aliceDoc = documents.save(
				new Document(alice, "secret.txt", "text/plain", 14, storagePath, "d".repeat(64)));

		assertThatThrownBy(() -> documentService.delete(aliceDoc.getId()))
				.isInstanceOf(DocumentNotFoundException.class)
				.hasMessage("Document not found.");
		assertThat(documents.findById(aliceDoc.getId())).isPresent();
		assertThat(Files.exists(storage.resolve(storagePath))).isTrue();
	}

	@Test
	void unknownIdGivesSameNotFoundMessage() {
		assertThatThrownBy(() -> documentService.delete(UUID.randomUUID()))
				.isInstanceOf(DocumentNotFoundException.class)
				.hasMessage("Document not found.");
	}

}
