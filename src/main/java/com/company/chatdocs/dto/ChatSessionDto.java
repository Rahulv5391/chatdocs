package com.company.chatdocs.dto;

import com.company.chatdocs.entity.ChatSession;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionDto(@NonNull UUID id, @NonNull String title, @NonNull Instant updatedAt) {

	public static ChatSessionDto from(ChatSession session) {
		return new ChatSessionDto(session.getId(), session.getTitle(), session.getUpdatedAt());
	}

}
