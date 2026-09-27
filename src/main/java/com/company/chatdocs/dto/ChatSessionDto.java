package com.company.chatdocs.dto;

import com.company.chatdocs.entity.ChatSession;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param scoped      true if the chat only uses {@code documentIds}; false means all of the user's documents
 * @param documentIds the chat's documents when scoped (can become empty if they were deleted)
 */
public record ChatSessionDto(@NonNull UUID id, @NonNull String title, @NonNull Instant updatedAt, boolean scoped,
		@NonNull List<@NonNull UUID> documentIds) {

	public static ChatSessionDto from(ChatSession session) {
		return new ChatSessionDto(session.getId(), session.getTitle(), session.getUpdatedAt(), session.isScoped(),
				List.copyOf(session.getDocumentIds()));
	}

}
