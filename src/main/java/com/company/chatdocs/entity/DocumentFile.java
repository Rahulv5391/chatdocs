package com.company.chatdocs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * The original uploaded file of a {@link Document}, stored as bytes (bytea). Kept apart from {@code Document} so
 * loading documents never loads file contents. The database deletes it together with its document.
 */
@Entity
@Table(name = "document_file")
public class DocumentFile {

	@Id
	@Column(name = "document_id")
	private UUID documentId;

	@Column(nullable = false)
	private byte[] content;

	protected DocumentFile() {
	}

	public DocumentFile(UUID documentId, byte[] content) {
		this.documentId = documentId;
		this.content = content;
	}

	public byte[] getContent() {
		return content;
	}

}
