package com.company.chatdocs.dto;

import com.company.chatdocs.entity.MessageRole;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param citations sources for an assistant answer, matching its [n] markers; empty for user messages
 */
public record ChatMessageDto(@NonNull UUID id, @NonNull MessageRole role, @NonNull String content,
		@NonNull Instant createdAt, @NonNull List<@NonNull Citation> citations) {
}
