package com.company.chatdocs.service;

import com.company.chatdocs.dto.ChatMessageDto;
import com.company.chatdocs.dto.ChatSessionDto;
import com.company.chatdocs.entity.ChatMessage;
import com.company.chatdocs.entity.ChatSession;
import com.company.chatdocs.entity.MessageRole;
import com.company.chatdocs.exception.ChatSessionNotFoundException;
import com.company.chatdocs.exception.InvalidInputException;
import com.company.chatdocs.repository.ChatMessageRepository;
import com.company.chatdocs.repository.ChatSessionRepository;
import com.vaadin.hilla.BrowserCallable;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.NonNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Chat sessions and their messages. Every method only sees the logged-in user's sessions.
 * Phase 14 has no AI yet: sending a message stores it and a placeholder reply.
 */
@BrowserCallable
@PermitAll
public class ChatService {

	static final int MAX_TITLE_LENGTH = 200;

	static final String PLACEHOLDER_REPLY = "(AI coming soon)";

	private final ChatSessionRepository sessions;
	private final ChatMessageRepository messages;
	private final CurrentUser currentUser;

	ChatService(ChatSessionRepository sessions, ChatMessageRepository messages, CurrentUser currentUser) {
		this.sessions = sessions;
		this.messages = messages;
		this.currentUser = currentUser;
	}

	/** The user's chats, most recently used first. */
	@Transactional(readOnly = true)
	public @NonNull List<@NonNull ChatSessionDto> listSessions() {
		return sessions.findByOwnerIdOrderByUpdatedAtDesc(currentUser.get().getId()).stream()
				.map(ChatSessionDto::from)
				.toList();
	}

	@Transactional
	public @NonNull ChatSessionDto createSession() {
		return ChatSessionDto.from(sessions.save(new ChatSession(currentUser.get())));
	}

	@Transactional(readOnly = true)
	public @NonNull ChatSessionDto getSession(@NonNull UUID sessionId) {
		return ChatSessionDto.from(ownSession(sessionId));
	}

	@Transactional
	public @NonNull ChatSessionDto renameSession(@NonNull UUID sessionId, @NonNull String title) {
		String trimmed = title.strip();
		if (trimmed.isEmpty()) {
			throw new InvalidInputException("The title can't be empty.");
		}
		if (trimmed.length() > MAX_TITLE_LENGTH) {
			throw new InvalidInputException("The title can be at most " + MAX_TITLE_LENGTH + " characters.");
		}
		ChatSession session = ownSession(sessionId);
		session.rename(trimmed);
		return ChatSessionDto.from(sessions.saveAndFlush(session));
	}

	/** Deletes the chat; the database deletes its messages too (ON DELETE CASCADE). */
	@Transactional
	public void deleteSession(@NonNull UUID sessionId) {
		sessions.delete(ownSession(sessionId));
	}

	/** Messages in the order they were sent. */
	@Transactional(readOnly = true)
	public @NonNull List<@NonNull ChatMessageDto> getMessages(@NonNull UUID sessionId) {
		ownSession(sessionId);
		return messages.findBySessionIdOrderBySeqAsc(sessionId).stream().map(ChatMessageDto::from).toList();
	}

	/** Saves the user's message and a placeholder reply, and returns both. */
	@Transactional
	public @NonNull List<@NonNull ChatMessageDto> sendMessage(@NonNull UUID sessionId, @NonNull String content) {
		String question = content.strip();
		if (question.isEmpty()) {
			throw new InvalidInputException("The message can't be empty.");
		}
		ChatSession session = ownSession(sessionId);
		ChatMessage userMessage = messages.save(new ChatMessage(session, MessageRole.USER, question));
		ChatMessage reply = messages.save(new ChatMessage(session, MessageRole.ASSISTANT, PLACEHOLDER_REPLY));
		session.touch();
		return List.of(ChatMessageDto.from(userMessage), ChatMessageDto.from(reply));
	}

	private ChatSession ownSession(UUID sessionId) {
		return sessions.findByIdAndOwnerId(sessionId, currentUser.get().getId())
				.orElseThrow(ChatSessionNotFoundException::new);
	}

}
