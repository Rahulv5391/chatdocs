package com.company.chatdocs.service;

import com.company.chatdocs.FakeChatModelConfiguration;
import com.company.chatdocs.FakeEmbeddingModelConfiguration;
import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.dto.DocumentDto;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.exception.UploadRejectedException;
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


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class, FakeChatModelConfiguration.class })
@SpringBootTest(properties = { "app.ingestion.pause-between-batches=0s" })
@ActiveProfiles("dev")
@WithMockUser(username = "demo")
class DocumentUploadTests {

	@Autowired
	DocumentService documentService;

	@Autowired
	DocumentRepository documents;

	@Autowired
	DocumentFileRepository files;

	@AfterEach
	void cleanUp() {
		documents.deleteAll();
	}

	@Test
	void savesFileAndCreatesUploadedRow() throws Exception {
		DocumentDto dto = documentService.upload(pdf("report.pdf", "%PDF-1.4 hello"));

		assertThat(dto.fileName()).isEqualTo("report.pdf");
		assertThat(dto.status()).isEqualTo(DocumentStatus.UPLOADED);
		assertThat(dto.contentType()).isEqualTo("application/pdf");

		var saved = documents.findById(dto.id()).orElseThrow();
		assertThat(saved.getChecksumSha256()).hasSize(64);
		assertThat(files.findById(saved.getId()).orElseThrow().getContent()).asString().isEqualTo("%PDF-1.4 hello");
	}

	@Test
	void rejectsDuplicateContentEvenWithDifferentName() {
		documentService.upload(pdf("a.pdf", "same bytes"));

		assertThatThrownBy(() -> documentService.upload(pdf("b.pdf", "same bytes")))
				.isInstanceOf(UploadRejectedException.class)
				.hasMessage("You have already uploaded this file.");
		assertThat(documents.count()).isEqualTo(1);
	}

	@Test
	void rejectsUnsupportedType() {
		assertThatThrownBy(() -> documentService.upload(
				new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] { 1, 2, 3 })))
				.isInstanceOf(UploadRejectedException.class)
				.hasMessage("Only PDF, DOCX, XLSX, PPTX, HTML, EPUB, TXT and MD files are supported.");
	}

	@Test
	void acceptsOfficeWebAndEbookFormats() {
		assertThat(documentService.upload(file("sheet.xlsx", "x")).contentType())
				.isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		assertThat(documentService.upload(file("slides.PPTX", "p")).contentType())
				.isEqualTo("application/vnd.openxmlformats-officedocument.presentationml.presentation");
		assertThat(documentService.upload(file("page.htm", "h")).contentType()).isEqualTo("text/html");
		assertThat(documentService.upload(file("book.epub", "e")).contentType()).isEqualTo("application/epub+zip");
	}

	@Test
	void rejectsEmptyFile() {
		assertThatThrownBy(() -> documentService.upload(pdf("empty.pdf", "")))
				.isInstanceOf(UploadRejectedException.class)
				.hasMessage("The file is empty.");
	}

	private static MockMultipartFile file(String name, String content) {
		return new MockMultipartFile("file", name, "application/octet-stream", content.getBytes());
	}

	private static MockMultipartFile pdf(String name, String content) {
		return new MockMultipartFile("file", name, "application/pdf", content.getBytes());
	}

}
