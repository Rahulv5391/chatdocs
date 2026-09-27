package com.company.chatdocs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "document")
public class Document extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private AppUser owner;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "storage_path", nullable = false)
	private String storagePath;

	@Column(name = "checksum_sha256", nullable = false)
	private String checksumSha256;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DocumentStatus status = DocumentStatus.UPLOADED;

	@Column(name = "chunk_count", nullable = false)
	private int chunkCount;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Document() {
	}

	public Document(AppUser owner, String fileName, String contentType, long sizeBytes, String storagePath,
			String checksumSha256) {
		this.owner = owner;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storagePath = storagePath;
		this.checksumSha256 = checksumSha256;
	}

	@PrePersist
	@PreUpdate
	protected void onUpdate() {
		updatedAt = Instant.now();
	}

	public AppUser getOwner() {
		return owner;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getStoragePath() {
		return storagePath;
	}

	public String getChecksumSha256() {
		return checksumSha256;
	}

	public DocumentStatus getStatus() {
		return status;
	}

	public int getChunkCount() {
		return chunkCount;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
