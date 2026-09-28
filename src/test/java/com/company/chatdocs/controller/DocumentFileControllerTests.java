package com.company.chatdocs.controller;

import com.company.chatdocs.TestDocuments;
import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.repository.AppUserRepository;
import com.company.chatdocs.repository.DocumentRepository;
import com.company.chatdocs.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage-dir=target/test-uploads")
@ActiveProfiles("dev")
@WithMockUser(username = "demo")
class DocumentFileControllerTests {

	@Autowired
	DocumentFileController controller;

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
	void servesOwnFileInline() throws Exception {
		UUID id = store("demo", "notes.txt", "hello from demo");

		var response = controller.file(id);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getContentType()).hasToString("text/plain");
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
				.startsWith("inline").contains("notes.txt");
		assertThat(response.getBody().getContentAsString(StandardCharsets.UTF_8))
				.isEqualTo("hello from demo");
	}

	@Test
	void anotherUsersFileLooksNotFound() {
		UUID aliceFile = store("alice", "secret.txt", "alice only");

		assertThat(controller.file(aliceFile).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(controller.file(UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private UUID store(String username, String fileName, String text) {
		AppUser owner = users.findByUsername(username).orElseThrow();
		return TestDocuments.saveText(storage, documents, owner, fileName, text).getId();
	}

}
