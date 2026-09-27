package com.company.chatdocs.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Picks the right Spring AI reader for a stored file. PDFs are read page by page so each chunk keeps its page
 * number (metadata key {@code page_number}); DOCX, TXT and MD go through Apache Tika.
 */
@Component
public class DocumentReaderFactory {

	private final FileStorageService storage;

	DocumentReaderFactory(FileStorageService storage) {
		this.storage = storage;
	}

	public List<Document> read(com.company.chatdocs.entity.Document document) {
		Resource resource = new FileSystemResource(storage.resolve(document.getStoragePath()));
		DocumentReader reader = "application/pdf".equals(document.getContentType())
				? new PagePdfDocumentReader(resource)
				: new TikaDocumentReader(resource);
		return reader.get();
	}

}
