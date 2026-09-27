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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat sessions and their messages. Every method only sees the logged-in user's sessions.
 * {@link #ask} runs the RAG flow: save question → retrieve → stream the generated answer → save it with citations.
 */
@BrowserCallable
@PermitAll
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	static final int MAX_TITLE_LENGTH = 200;

	static final String STOPPED_NOTE = "_(Stopped)_";

	static final String ERROR_NOTE = "_(Something went wrong while generating the answer. Please try again.)_";

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
	 * Answers a question from the user's documents, streaming the answer as it is generated.
	 * <p>
	 * The question is saved and retrieval runs right away (on the request thread, where the logged-in user is
	 * known). The returned Flux then streams Gemini's tokens; when it completes, the full answer and its citations
	 * are saved before the browser gets onComplete. If the browser cancels (Stop button), the partial answer is
	 * saved with a note. No database transaction is open while tokens stream.
	 */
	public @NonNull Flux<@NonNull String> ask(@NonNull UUID sessionId, @NonNull String question) {
		String text = question.strip();
		if (text.isEmpty()) {
			throw new InvalidInputException("The message can't be empty.");
		}
		UUID userId = currentUser.get().getId();

		transaction.executeWithoutResult(status -> {
			ChatSession session = ownSession(sessionId);
			if (ChatSession.DEFAULT_TITLE.equals(session.getTitle())
					&& messages.findBySessionIdOrderBySeqAsc(sessionId).isEmpty()) {
				session.rename(autoTitle(text));
			}
			session.touch();
			messages.save(new ChatMessage(session, MessageRole.USER, text));
		});

		var chunks = retrieval.search(userId, text, List.of());
		GenerationService.StreamingAnswer answer = generation.stream(text, chunks);

		StringBuilder fullText = new StringBuilder();
		AtomicBoolean saved = new AtomicBoolean();
		return answer.tokens()
				.doOnNext(fullText::append)
				// Save on a worker thread (JPA blocks), and before the browser sees onComplete.
				.concatWith(Mono.<String>fromRunnable(() -> saveReply(sessionId, fullText.toString(), answer, saved))
					.subscribeOn(Schedulers.boundedElastic()))
				.doOnCancel(() -> saveReply(sessionId, withNote(fullText, STOPPED_NOTE), answer, saved))
				.doOnError(error -> {
					log.warn("Answer generation failed for session {}", sessionId, error);
					saveReply(sessionId, withNote(fullText, ERROR_NOTE), answer, saved);
				});
	}

	/** Saves the assistant's message once, whichever of complete / cancel / error happens first. */
	private void saveReply(UUID sessionId, String text, GenerationService.StreamingAnswer answer, AtomicBoolean saved) {
		if (!saved.compareAndSet(false, true)) {
			return;
		}
		String content = text.isBlank() ? GenerationService.NO_ANSWER : text.strip();
		String citations = json.writeValueAsString(GenerationService.citedOnly(content, answer.citations()));
		// Runs outside the request thread, so no currentUser here; ask() already checked ownership.
		transaction.executeWithoutResult(status -> sessions.findById(sessionId).ifPresent(session -> {
			session.touch();
			messages.save(new ChatMessage(session, MessageRole.ASSISTANT, content, citations));
		}));
	}

	private static String withNote(StringBuilder partial, String note) {
		return partial.isEmpty() ? note : partial.toString().strip() + "\n\n" + note;
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
