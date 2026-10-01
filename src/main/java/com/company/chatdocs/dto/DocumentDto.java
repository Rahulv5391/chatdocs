package com.company.chatdocs.dto;

import com.company.chatdocs.entity.Document;
import com.company.chatdocs.entity.DocumentStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record DocumentDto(
		@NonNull UUID id,
		@NonNull String fileName,
		@NonNull String contentType,
		long sizeBytes,
		@NonNull DocumentStatus status,
		int chunkCount,
		@Nullable String errorMessage,
		@Nullable String summary,
		@Nullable String sourceUrl,
		@NonNull Instant createdAt) {

	public static DocumentDto from(Document document) {
		return new DocumentDto(document.getId(), document.getFileName(), document.getContentType(),
				document.getSizeBytes(), document.getStatus(), document.getChunkCount(), document.getErrorMessage(),
				document.getSummary(), document.getSourceUrl(), document.getCreatedAt());
	}

}
