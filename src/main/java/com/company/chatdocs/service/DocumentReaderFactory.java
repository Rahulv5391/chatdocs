package com.company.chatdocs.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Picks the right Spring AI reader for an uploaded file. PDFs are read page by page so each chunk keeps its page
 * number (metadata key {@code page_number}); DOCX, TXT and MD go through Apache Tika.
 */
@Component
public class DocumentReaderFactory {

	public List<Document> read(com.company.chatdocs.entity.Document document, byte[] content) {
		Resource resource = new NamedByteArrayResource(content, document.getFileName());
		DocumentReader reader = "application/pdf".equals(document.getContentType())
				? new PagePdfDocumentReader(resource)
				: new TikaDocumentReader(resource);
		return reader.get();
	}

	/** The readers put the file name into their metadata, which must not be null. */
	private static class NamedByteArrayResource extends ByteArrayResource {

		private final String fileName;

		NamedByteArrayResource(byte[] content, String fileName) {
			super(content);
			this.fileName = fileName;
		}

		@Override
		public String getFilename() {
			return fileName;
		}

	}

}
