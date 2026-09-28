package com.company.chatdocs.service;

import com.company.chatdocs.FakeEmbeddingModelConfiguration;
import com.company.chatdocs.TestDocuments;
import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.exception.DocumentNotFoundException;
import com.company.chatdocs.repository.AppUserRepository;
import com.company.chatdocs.repository.DocumentFileRepository;
import com.company.chatdocs.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class })
@SpringBootTest(properties = { "app.ingestion.pause-between-batches=0s" })
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
	DocumentFileRepository files;

	@AfterEach
	void cleanUp() {
		documents.deleteAll();
	}

	@Test
	void deletesOwnDocumentRowAndFile() {
		var dto = documentService.upload(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()));
		assertThat(files.findById(dto.id())).isPresent();

		documentService.delete(dto.id());

		assertThat(documents.findById(dto.id())).isEmpty();
		assertThat(files.findById(dto.id())).isEmpty();
	}

	@Test
	void cannotDeleteAnotherUsersDocument() {
		var alice = users.findByUsername("alice").orElseThrow();
		Document aliceDoc = TestDocuments.saveText(documents, files, alice, "secret.txt", "alice's secret");

		assertThatThrownBy(() -> documentService.delete(aliceDoc.getId()))
				.isInstanceOf(DocumentNotFoundException.class)
				.hasMessage("Document not found.");
		assertThat(documents.findById(aliceDoc.getId())).isPresent();
		assertThat(files.findById(aliceDoc.getId())).isPresent();
	}

	@Test
	void unknownIdGivesSameNotFoundMessage() {
		assertThatThrownBy(() -> documentService.delete(UUID.randomUUID()))
				.isInstanceOf(DocumentNotFoundException.class)
				.hasMessage("Document not found.");
	}

}
