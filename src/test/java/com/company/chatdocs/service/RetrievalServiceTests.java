package com.company.chatdocs.service;

import com.company.chatdocs.FakeEmbeddingModelConfiguration;
import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.repository.AppUserRepository;
import com.company.chatdocs.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * demo and alice both have a document about the same topic. Retrieval must never mix them up,
 * whatever the question or document scope. Embeddings come from the fake model (no Gemini quota).
 */
@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class })
@SpringBootTest(properties = { "app.storage-dir=target/test-uploads", "app.ingestion.pause-between-batches=0s",
		"app.rag.similarity-threshold=0.1" })
@ActiveProfiles("dev")
class RetrievalServiceTests {

	@Autowired
	RetrievalService retrievalService;

	@Autowired
	IngestionService ingestionService;

	@Autowired
	DocumentRepository documents;

	@Autowired
	AppUserRepository users;

	@Autowired
	FileStorageService storage;

	@Autowired
	JdbcTemplate jdbc;

	AppUser demo;
	AppUser alice;
	UUID demoLeave;
	UUID demoTravel;
	UUID aliceLeave;

	@BeforeEach
	void createDocuments() {
		demo = users.findByUsername("demo").orElseThrow();
		alice = users.findByUsername("alice").orElseThrow();
		demoLeave = ingest(demo, "demo-leave.txt", "Leave policy: demo employees get 24 days of annual leave.");
		demoTravel = ingest(demo, "demo-travel.txt", "Travel policy: demo employees book flights two weeks ahead.");
		aliceLeave = ingest(alice, "alice-leave.txt", "Leave policy: alice employees get 30 days of annual leave.");
	}

	@AfterEach
	void cleanUp() {
		documents.findAll().forEach(document -> storage.delete(document.getStoragePath()));
		documents.deleteAll();
		jdbc.update("delete from vector_store");
	}

	@Test
	void usersOnlyRetrieveTheirOwnChunks() {
		var demoResults = retrievalService.search(demo.getId(), "How many days of annual leave?", List.of());
		var aliceResults = retrievalService.search(alice.getId(), "How many days of annual leave?", List.of());

		assertThat(demoResults).isNotEmpty()
				.allSatisfy(chunk -> assertThat(chunk.getMetadata()).containsEntry("user_id", demo.getId().toString()));
		assertThat(aliceResults).isNotEmpty()
				.allSatisfy(chunk -> assertThat(chunk.getMetadata()).containsEntry("user_id", alice.getId().toString()));
		assertThat(fileNames(demoResults)).doesNotContain("alice-leave.txt");
		assertThat(fileNames(aliceResults)).containsExactly("alice-leave.txt");
	}

	@Test
	void mostRelevantChunkComesFirstWithMetadata() {
		var results = retrievalService.search(demo.getId(), "leave policy for employees", List.of());

		assertThat(results).hasSizeGreaterThan(1);
		assertThat(fileNames(results).getFirst()).isEqualTo("demo-leave.txt");
		assertThat(results.getFirst().getMetadata()).containsEntry("document_id", demoLeave.toString())
				.containsKeys("file_name", "chunk_index");
		assertThat(results).extracting(chunk -> chunk.getScore())
				.isSortedAccordingTo(java.util.Comparator.reverseOrder());
	}

	@Test
	void scopeLimitsSearchToChosenDocuments() {
		var results = retrievalService.search(demo.getId(), "policy for employees", Set.of(demoTravel));

		assertThat(fileNames(results)).containsExactly("demo-travel.txt");
	}

	@Test
	void anotherUsersDocumentInScopeStillReturnsNothing() {
		var results = retrievalService.search(demo.getId(), "annual leave", Set.of(aliceLeave));

		assertThat(results).isEmpty();
	}

	@Test
	void unrelatedQuestionIsBelowThreshold() {
		assertThat(retrievalService.search(demo.getId(), "quantum chromodynamics lecture", List.of())).isEmpty();
	}

	private UUID ingest(AppUser owner, String fileName, String text) {
		String path = storage.save(owner.getId(), "txt", text.getBytes());
		Document document = documents.save(new Document(owner, fileName, "text/plain", text.length(), path,
				UUID.randomUUID().toString().replace("-", "").repeat(2)));
		ingestionService.ingest(document.getId());
		assertThat(documents.findById(document.getId()).orElseThrow().getStatus()).isEqualTo(DocumentStatus.READY);
		return document.getId();
	}

	private static List<String> fileNames(List<org.springframework.ai.document.Document> chunks) {
		return chunks.stream().map(chunk -> (String) chunk.getMetadata().get("file_name")).toList();
	}

}
