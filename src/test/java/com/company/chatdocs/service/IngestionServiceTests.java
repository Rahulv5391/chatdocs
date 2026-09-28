package com.company.chatdocs.service;

import com.company.chatdocs.FakeEmbeddingModelConfiguration;
import com.company.chatdocs.FakeEmbeddingModelConfiguration.FakeEmbeddingModel;
import com.company.chatdocs.TestDocuments;
import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.repository.AppUserRepository;
import com.company.chatdocs.repository.DocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uploads real files and waits for the background ingestion (after commit, on another thread) to finish.
 * Embeddings come from the fake model; batch size 3 makes batching visible with small files.
 */
@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class })
@SpringBootTest(properties = { "app.ingestion.pause-between-batches=0s",
		"app.ingestion.batch-size=3", "app.ingestion.chunk-size=100" })
@ActiveProfiles("dev")
@WithMockUser(username = "demo")
class IngestionServiceTests {

	@Autowired
	DocumentService documentService;

	@Autowired
	DocumentRepository documents;

	@Autowired
	IngestionService ingestionService;

	@Autowired
	AppUserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	FakeEmbeddingModel embeddingModel;

	@AfterEach
	void cleanUp() {
		documents.deleteAll();
		jdbc.update("delete from vector_store");
	}

	@Test
	void pdfWithTextBecomesReadyWithChunks() throws Exception {
		byte[] pdf = pdf(List.of(
				"Leave policy. Every employee gets 24 days of paid annual leave per year.",
				"Travel policy. Book flights at least two weeks in advance."));

		Document document = uploadAndWait("policies.pdf", "application/pdf", pdf);

		assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
		assertThat(document.getChunkCount()).isGreaterThanOrEqualTo(2);
		assertThat(document.getErrorMessage()).isNull();

		// Every chunk is stored with the metadata retrieval and citations need.
		List<Map<String, Object>> rows = chunkRows(document);
		assertThat(rows).hasSize(document.getChunkCount());
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.get("user_id")).isEqualTo(document.getOwnerId().toString());
			assertThat(row.get("file_name")).isEqualTo("policies.pdf");
			assertThat(row.get("page")).isNotNull();
		});
		assertThat(rows).extracting(row -> row.get("page")).contains("1", "2");
		assertThat(rows).extracting(row -> row.get("chunk_index")).doesNotHaveDuplicates();
	}

	@Test
	void embedsLongDocumentInBatches() throws Exception {
		String text = "Section about vacation, travel, expenses and remote work rules for employees. ".repeat(60);
		int callsBefore = embeddingModel.calls.get();

		Document document = uploadAndWait("long.txt", "text/plain", text.getBytes());

		assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
		assertThat(document.getChunkCount()).isGreaterThan(3);
		assertThat(chunkRows(document)).hasSize(document.getChunkCount());
		// batch-size=3, so e.g. 7 chunks need 3 embedding calls.
		int expectedBatches = (document.getChunkCount() + 2) / 3;
		assertThat(embeddingModel.calls.get() - callsBefore).isEqualTo(expectedBatches);
	}

	@Test
	void textFileBecomesReady() throws Exception {
		Document document = uploadAndWait("notes.txt", "text/plain",
				"Meeting notes: the demo is on Friday and everyone should bring questions.".getBytes());

		assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
		assertThat(document.getChunkCount()).isEqualTo(1);
	}

	@Test
	void corruptPdfFailsWithReason() throws Exception {
		Document document = uploadAndWait("broken.pdf", "application/pdf", "%PDF-1.4 this is not a pdf".getBytes());

		assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
		assertThat(document.getErrorMessage()).startsWith("Could not read the file");
	}

	@Test
	void pdfWithoutTextFailsWithNoExtractableText() throws Exception {
		Document document = uploadAndWait("scan.pdf", "application/pdf", pdf(List.of("")));

		assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
		assertThat(document.getErrorMessage()).startsWith("No extractable text");
	}

	@Test
	void missingOriginalFileFailsWithClearMessage() {
		var demo = users.findByUsername("demo").orElseThrow();
		Document document = documents.save(new Document(demo, "gone.txt", "text/plain", 4, "0".repeat(64)));

		ingestionService.ingest(document.getId());

		Document after = documents.findById(document.getId()).orElseThrow();
		assertThat(after.getStatus()).isEqualTo(DocumentStatus.FAILED);
		assertThat(after.getErrorMessage()).isEqualTo("The original file is missing. Please upload it again.");
	}

	private List<Map<String, Object>> chunkRows(Document document) {
		return jdbc.queryForList("""
				select metadata->>'user_id' as user_id, metadata->>'file_name' as file_name,
				       metadata->>'page' as page, metadata->>'chunk_index' as chunk_index
				from vector_store where metadata->>'document_id' = ?""", document.getId().toString());
	}

	private Document uploadAndWait(String name, String contentType, byte[] content) throws InterruptedException {
		UUID id = documentService.upload(new MockMultipartFile("file", name, contentType, content)).id();
		return TestDocuments.awaitIngestion(documents, id);
	}

	/** Builds a PDF with one page per string (an empty string gives a blank page). */
	private static byte[] pdf(List<String> pages) throws IOException {
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (String text : pages) {
				PDPage page = new PDPage();
				pdf.addPage(page);
				if (!text.isEmpty()) {
					try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
						stream.beginText();
						stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
						stream.newLineAtOffset(50, 700);
						stream.showText(text);
						stream.endText();
					}
				}
			}
			pdf.save(out);
			return out.toByteArray();
		}
	}

}
