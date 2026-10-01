package com.company.chatdocs.controller;

import com.company.chatdocs.TestDocuments;
import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.entity.DocumentFile;
import com.company.chatdocs.repository.AppUserRepository;
import com.company.chatdocs.repository.DocumentFileRepository;
import com.company.chatdocs.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
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
	DocumentFileRepository files;

	@AfterEach
	void cleanUp() {
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
		assertThat(response.getBody()).asString().isEqualTo("hello from demo");
	}

	@Test
	void nonPdfFilesAreServedInASandboxSoScriptsCannotRun() {
		AppUser demo = users.findByUsername("demo").orElseThrow();
		UUID page = store("demo", "page.html", "<script>alert(1)</script>");
		Document pdf = documents.save(new Document(demo, "a.pdf", "application/pdf", 4, "a".repeat(64)));
		files.save(new DocumentFile(pdf.getId(), "%PDF".getBytes()));

		assertThat(controller.file(page).getHeaders().getFirst("Content-Security-Policy")).isEqualTo("sandbox");
		assertThat(controller.file(pdf.getId()).getHeaders().getFirst("Content-Security-Policy")).isNull();
	}

	@Test
	void anotherUsersFileLooksNotFound() {
		UUID aliceFile = store("alice", "secret.txt", "alice only");

		assertThat(controller.file(aliceFile).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(controller.file(UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private UUID store(String username, String fileName, String text) {
		AppUser owner = users.findByUsername(username).orElseThrow();
		return TestDocuments.saveText(documents, files, owner, fileName, text).getId();
	}

}
