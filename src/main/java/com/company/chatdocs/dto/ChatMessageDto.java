package com.company.chatdocs.dto;

import com.company.chatdocs.entity.ChatMessage;
import com.company.chatdocs.entity.MessageRole;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageDto(@NonNull UUID id, @NonNull MessageRole role, @NonNull String content,
		@NonNull Instant createdAt) {

	public static ChatMessageDto from(ChatMessage message) {
		return new ChatMessageDto(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
	}

}
