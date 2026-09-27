package com.company.chatdocs.service;

import com.company.chatdocs.FakeEmbeddingModelConfiguration;
import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.dto.DocumentDto;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.exception.UploadRejectedException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class })
@SpringBootTest(properties = { "app.storage-dir=target/test-uploads", "app.ingestion.pause-between-batches=0s" })
@ActiveProfiles("dev")
@WithMockUser(username = "demo")
class DocumentUploadTests {

	@Autowired
	DocumentService documentService;

	@Autowired
	DocumentRepository documents;

	@Autowired
	FileStorageService storage;

	@AfterEach
	void cleanUp() {
		documents.findAll().forEach(document -> storage.delete(document.getStoragePath()));
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
		assertThat(Files.readString(storage.resolve(saved.getStoragePath()))).isEqualTo("%PDF-1.4 hello");
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
				.hasMessage("Only PDF, DOCX, TXT and MD files are supported.");
	}

	@Test
	void rejectsEmptyFile() {
		assertThatThrownBy(() -> documentService.upload(pdf("empty.pdf", "")))
				.isInstanceOf(UploadRejectedException.class)
				.hasMessage("The file is empty.");
	}

	private static MockMultipartFile pdf(String name, String content) {
		return new MockMultipartFile("file", name, "application/pdf", content.getBytes());
	}

}
