package com.company.chatdocs;

import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.entity.DocumentStatus;
import com.company.chatdocs.repository.DocumentRepository;
import com.company.chatdocs.service.FileStorageService;

import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.fail;

/** Test helpers for creating documents and waiting for background ingestion. */
public final class TestDocuments {

	private TestDocuments() {
	}

	/** Stores a text file and its document row directly, skipping upload validation and ingestion. */
	public static Document saveText(FileStorageService storage, DocumentRepository documents, AppUser owner,
			String fileName, String text) {
		String path = storage.save(owner.getId(), "txt", text.getBytes());
		return documents.save(new Document(owner, fileName, "text/plain", text.length(), path, randomChecksum()));
	}

	/** Waits up to 15 seconds for background ingestion to end in READY or FAILED. */
	public static Document awaitIngestion(DocumentRepository documents, UUID id) throws InterruptedException {
		for (int i = 0; i < 150; i++) {
			Document document = documents.findById(id).orElseThrow();
			if (document.getStatus() == DocumentStatus.READY || document.getStatus() == DocumentStatus.FAILED) {
				return document;
			}
			Thread.sleep(100);
		}
		return fail("Ingestion did not finish within 15 seconds");
	}

	private static String randomChecksum() {
		byte[] bytes = new byte[32];
		ThreadLocalRandom.current().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

}
