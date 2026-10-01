package com.company.chatdocs.service;

import com.company.chatdocs.FakeChatModelConfiguration;
import com.company.chatdocs.FakeEmbeddingModelConfiguration;
import com.company.chatdocs.TestDocuments;
import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.exception.DocumentNotFoundException;
import com.company.chatdocs.exception.InvalidDocumentStateException;
import com.company.chatdocs.repository.AppUserRepository;
import com.company.chatdocs.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class, FakeChatModelConfiguration.class })
@SpringBootTest(properties = { "app.ingestion.pause-between-batches=0s" })
@ActiveProfiles("dev")
@WithMockUser(username = "demo")
class DocumentReprocessTests {

	private static final String TEXT = "Onboarding guide. New employees receive a laptop on day one. ".repeat(80);

	@Autowired
	DocumentService documentService;

	@Autowired
	IngestionService ingestionService;

	@Autowired
	DocumentRepository documents;

	@Autowired
	AppUserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		documents.deleteAll();
		jdbc.update("delete from vector_store");
	}

	@Test
	void deleteAlsoRemovesChunks() throws Exception {
		UUID id = uploadAndWaitForReady("guide.txt");
		assertThat(chunkRows(id)).isPositive();

		documentService.delete(id);

		assertThat(chunkRows(id)).isZero();
	}

	@Test
	void reprocessRebuildsChunksWithoutDuplicates() throws Exception {
		UUID id = uploadAndWaitForReady("guide.txt");
		int chunkCount = documents.findById(id).orElseThrow().getChunkCount();
		documents.updateStatus(id, DocumentStatus.FAILED, 0, "Simulated failure", Instant.now());

		assertThat(documentService.reprocess(id).status()).isEqualTo(DocumentStatus.UPLOADED);
		Document document = TestDocuments.awaitIngestion(documents, id);

		assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
		assertThat(document.getErrorMessage()).isNull();
		assertThat(document.getChunkCount()).isEqualTo(chunkCount);
		assertThat(chunkRows(id)).isEqualTo(chunkCount);
	}

	@Test
	void readyDocumentCanBeReprocessed() throws Exception {
		UUID id = uploadAndWaitForReady("guide.txt");
		int chunkCount = documents.findById(id).orElseThrow().getChunkCount();

		assertThat(documentService.reprocess(id).status()).isEqualTo(DocumentStatus.UPLOADED);
		Document document = TestDocuments.awaitIngestion(documents, id);

		assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
		assertThat(chunkRows(id)).isEqualTo(chunkCount);
	}

	@Test
	void documentStillBeingProcessedCannotBeReprocessed() {
		var demo = users.findByUsername("demo").orElseThrow();
		Document queued = documents.save(new Document(demo, "q.txt", "text/plain", 1, "d".repeat(64)));
		documents.updateStatus(queued.getId(), DocumentStatus.PROCESSING, 0, null, Instant.now());

		assertThatThrownBy(() -> documentService.reprocess(queued.getId()))
				.isInstanceOf(InvalidDocumentStateException.class)
				.hasMessage("This document is still being processed.");
	}

	@Test
	void cannotReprocessAnotherUsersDocument() {
		var alice = users.findByUsername("alice").orElseThrow();
		Document aliceDoc = documents.save(new Document(alice, "a.txt", "text/plain", 1, "e".repeat(64)));
		documents.updateStatus(aliceDoc.getId(), DocumentStatus.FAILED, 0, "x", Instant.now());

		assertThatThrownBy(() -> documentService.reprocess(aliceDoc.getId()))
				.isInstanceOf(DocumentNotFoundException.class);
	}

	@Test
	void startupMarksInterruptedDocumentsAsFailed() {
		var demo = users.findByUsername("demo").orElseThrow();
		Document stuck = documents.save(new Document(demo, "stuck.txt", "text/plain", 1, "f".repeat(64)));
		documents.updateStatus(stuck.getId(), DocumentStatus.PROCESSING, 0, null, Instant.now());

		ingestionService.failInterruptedDocuments();

		Document after = documents.findById(stuck.getId()).orElseThrow();
		assertThat(after.getStatus()).isEqualTo(DocumentStatus.FAILED);
		assertThat(after.getErrorMessage()).contains("interrupted by a restart");
	}

	private UUID uploadAndWaitForReady(String name) throws InterruptedException {
		UUID id = documentService.upload(new MockMultipartFile("file", name, "text/plain", TEXT.getBytes())).id();
		assertThat(TestDocuments.awaitIngestion(documents, id).getStatus()).isEqualTo(DocumentStatus.READY);
		return id;
	}

	private int chunkRows(UUID documentId) {
		Integer count = jdbc.queryForObject("select count(*) from vector_store where metadata->>'document_id' = ?",
				Integer.class, documentId.toString());
		return count == null ? 0 : count;
	}

}
