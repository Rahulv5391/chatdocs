package com.company.chatdocs.service;

import com.company.chatdocs.FakeChatModelConfiguration;
import com.company.chatdocs.FakeEmbeddingModelConfiguration;
import com.company.chatdocs.TestDocuments;
import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.dto.DocumentDto;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.exception.UploadRejectedException;
import com.company.chatdocs.repository.DocumentRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Imports from a small HTTP server running inside the test. It listens on localhost, so private hosts are allowed
 * here; {@link UrlFetcherTests} checks that they are blocked by default.
 */
@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class, FakeChatModelConfiguration.class })
@SpringBootTest(properties = { "app.ingestion.pause-between-batches=0s", "app.url-import.allow-private-hosts=true" })
@ActiveProfiles("dev")
@WithMockUser(username = "demo")
class DocumentUrlImportTests {

	private static final String GUIDE = """
			<html><head><title>Employee Guide &amp; FAQ</title></head>
			<body><h1>Employee Guide</h1><p>The office opens at 9 am on weekdays.</p></body></html>""";

	@Autowired
	DocumentService documentService;

	@Autowired
	DocumentRepository documents;

	HttpServer server;

	String base;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/guide", exchange -> respond(exchange, 200, "text/html; charset=utf-8", GUIDE));
		server.createContext("/old-guide", exchange -> {
			exchange.getResponseHeaders().add("Location", "/guide");
			respond(exchange, 302, "text/plain", "");
		});
		server.createContext("/files/policy.txt",
				exchange -> respond(exchange, 200, "text/plain", "Remote work is allowed on Fridays."));
		server.createContext("/logo.png", exchange -> respond(exchange, 200, "image/png", "not really a png"));
		server.createContext("/missing", exchange -> respond(exchange, 404, "text/html", "Not found"));
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void cleanUp() {
		server.stop(0);
		documents.deleteAll();
	}

	@Test
	void importsWebPageNamedAfterItsTitleAndIndexesIt() throws Exception {
		DocumentDto dto = documentService.importUrl(base + "/guide");

		assertThat(dto.fileName()).isEqualTo("Employee Guide & FAQ.html");
		assertThat(dto.contentType()).isEqualTo("text/html");
		assertThat(dto.sourceUrl()).isEqualTo(base + "/guide");
		assertThat(TestDocuments.awaitIngestion(documents, dto.id()).getStatus()).isEqualTo(DocumentStatus.READY);
	}

	@Test
	void followsRedirectsAndKeepsTheFinalAddress() {
		DocumentDto dto = documentService.importUrl(base + "/old-guide");

		assertThat(dto.sourceUrl()).isEqualTo(base + "/guide");
	}

	@Test
	void namesNonHtmlFilesAfterTheAddress() {
		DocumentDto dto = documentService.importUrl(base + "/files/policy.txt");

		assertThat(dto.fileName()).isEqualTo("policy.txt");
		assertThat(dto.contentType()).isEqualTo("text/plain");
	}

	@Test
	void rejectsUnsupportedContent() {
		assertThatThrownBy(() -> documentService.importUrl(base + "/logo.png"))
				.isInstanceOf(UploadRejectedException.class)
				.hasMessageStartingWith("That address returned image/png, which isn't supported.");
	}

	@Test
	void reportsHttpErrors() {
		assertThatThrownBy(() -> documentService.importUrl(base + "/missing"))
				.isInstanceOf(UploadRejectedException.class)
				.hasMessage("The page could not be downloaded (HTTP 404).");
	}

	@Test
	void rejectsTheSameContentTwice() {
		documentService.importUrl(base + "/guide");

		assertThatThrownBy(() -> documentService.importUrl(base + "/old-guide"))
				.isInstanceOf(UploadRejectedException.class)
				.hasMessage("You already have a document with exactly this content.");
		assertThat(documents.count()).isEqualTo(1);
	}

	@Test
	void rejectsOtherSchemes() {
		assertThatThrownBy(() -> documentService.importUrl("file:///etc/passwd"))
				.isInstanceOf(UploadRejectedException.class)
				.hasMessage("Only http:// and https:// web addresses are supported.");
		assertThatThrownBy(() -> documentService.importUrl("  "))
				.isInstanceOf(UploadRejectedException.class)
				.hasMessage("Please enter a web address.");
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String contentType,
			String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", contentType);
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

}
