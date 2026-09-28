package com.company.chatdocs.service;

import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.dto.DocumentDto;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.repository.AppUserRepository;
import com.company.chatdocs.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
class DocumentServiceTests {

	@Autowired
	DocumentService documentService;

	@Autowired
	DocumentRepository documents;

	@Autowired
	AppUserRepository users;

	@BeforeEach
	void createDocuments() {
		var demo = users.findByUsername("demo").orElseThrow();
		var alice = users.findByUsername("alice").orElseThrow();
		documents.save(new Document(demo, "demo-notes.pdf", "application/pdf", 1234, "a".repeat(64)));
		documents.save(new Document(alice, "alice-plan.txt", "text/plain", 99, "b".repeat(64)));
	}

	@AfterEach
	void cleanUp() {
		documents.deleteAll();
	}

	@Test
	@WithMockUser(username = "demo")
	void demoSeesOnlyOwnDocuments() {
		assertThat(documentService.list()).extracting(DocumentDto::fileName).containsExactly("demo-notes.pdf");
	}

	@Test
	@WithMockUser(username = "alice")
	void aliceSeesOnlyOwnDocuments() {
		assertThat(documentService.list()).extracting(DocumentDto::fileName).containsExactly("alice-plan.txt");
	}

}
