package com.company.chatdocs.dto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A source passage behind an answer. {@code index} matches the [n] marker in the answer text.
 *
 * @param page page number for PDFs, null for other file types
 */
public record Citation(int index, @NonNull UUID documentId, @NonNull String fileName, @Nullable Integer page,
		@NonNull String snippet) {
}
