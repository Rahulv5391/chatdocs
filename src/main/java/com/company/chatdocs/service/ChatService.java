package com.company.chatdocs.service;

import com.company.chatdocs.dto.ChatMessageDto;
import com.company.chatdocs.dto.ChatSessionDto;
import com.company.chatdocs.dto.Citation;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

/**
 * Chat sessions and their messages. Every method only sees the logged-in user's sessions.
 * {@link #ask} runs the RAG flow: save question → retrieve → generate → save answer with citations.
 */
@BrowserCallable
@PermitAll
public class ChatService {

	static final int MAX_TITLE_LENGTH = 200;

	private static final int AUTO_TITLE_LENGTH = 60;

	private static final TypeReference<List<Citation>> CITATION_LIST = new TypeReference<>() {
	};

	private final ChatSessionRepository sessions;
	private final ChatMessageRepository messages;
	private final CurrentUser currentUser;
	private final RetrievalService retrieval;
	private final GenerationService generation;
	private final JsonMapper json;
	private final TransactionTemplate transaction;

	ChatService(ChatSessionRepository sessions, ChatMessageRepository messages, CurrentUser currentUser,
			RetrievalService retrieval, GenerationService generation, JsonMapper json,
			PlatformTransactionManager transactionManager) {
		this.sessions = sessions;
		this.messages = messages;
		this.currentUser = currentUser;
		this.retrieval = retrieval;
		this.generation = generation;
		this.json = json;
		this.transaction = new TransactionTemplate(transactionManager);
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
		return messages.findBySessionIdOrderBySeqAsc(sessionId).stream().map(this::toDto).toList();
	}

	/**
	 * Answers a question from the user's documents and returns the saved question and answer.
	 * Not one big transaction: the slow Gemini call happens between two short ones, so no database
	 * connection is held while waiting for the model.
	 */
	public @NonNull List<@NonNull ChatMessageDto> ask(@NonNull UUID sessionId, @NonNull String question) {
		String text = question.strip();
		if (text.isEmpty()) {
			throw new InvalidInputException("The message can't be empty.");
		}
		UUID userId = currentUser.get().getId();

		ChatMessage userMessage = transaction.execute(status -> {
			ChatSession session = ownSession(sessionId);
			if (ChatSession.DEFAULT_TITLE.equals(session.getTitle())
					&& messages.findBySessionIdOrderBySeqAsc(sessionId).isEmpty()) {
				session.rename(autoTitle(text));
			}
			session.touch();
			return messages.save(new ChatMessage(session, MessageRole.USER, text));
		});

		var chunks = retrieval.search(userId, text, List.of());
		GenerationService.Answer answer = generation.answer(text, chunks);

		ChatMessage reply = transaction.execute(status -> {
			ChatSession session = ownSession(sessionId);
			session.touch();
			return messages.save(new ChatMessage(session, MessageRole.ASSISTANT, answer.text(),
					json.writeValueAsString(answer.citations())));
		});

		return List.of(toDto(userMessage), toDto(reply));
	}

	private ChatSession ownSession(UUID sessionId) {
		return sessions.findByIdAndOwnerId(sessionId, currentUser.get().getId())
				.orElseThrow(ChatSessionNotFoundException::new);
	}

	private ChatMessageDto toDto(ChatMessage message) {
		List<Citation> citations = message.getCitationsJson() == null
				? List.of()
				: json.readValue(message.getCitationsJson(), CITATION_LIST);
		return new ChatMessageDto(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt(),
				citations);
	}

	private static String autoTitle(String question) {
		String singleLine = question.replaceAll("\\s+", " ");
		return singleLine.length() <= AUTO_TITLE_LENGTH ? singleLine
				: singleLine.substring(0, AUTO_TITLE_LENGTH - 1).strip() + "…";
	}

}
