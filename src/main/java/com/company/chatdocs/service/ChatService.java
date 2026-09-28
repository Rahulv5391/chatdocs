package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import com.company.chatdocs.dto.ChatMessageDto;
import com.company.chatdocs.dto.ChatSessionDto;
import com.company.chatdocs.dto.Citation;
import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.entity.ChatMessage;
import com.company.chatdocs.entity.ChatSession;
import com.company.chatdocs.entity.MessageRole;
import com.company.chatdocs.exception.AiServiceException;
import com.company.chatdocs.exception.ChatSessionNotFoundException;
import com.company.chatdocs.exception.DocumentNotFoundException;
import com.company.chatdocs.exception.InvalidInputException;
import com.company.chatdocs.repository.ChatMessageRepository;
import com.company.chatdocs.repository.ChatSessionRepository;
import com.company.chatdocs.repository.DocumentRepository;
import com.vaadin.hilla.BrowserCallable;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat sessions and their messages. Every method only sees the logged-in user's sessions.
 * {@link #ask} runs the RAG flow: save question → rewrite follow-ups → retrieve → stream the answer (with recent
 * history) → save it with citations.
 */
@BrowserCallable
@PermitAll
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	static final int MAX_TITLE_LENGTH = 200;

	static final String STOPPED_NOTE = "_(Stopped)_";

	private static final int AUTO_TITLE_LENGTH = 60;

	private static final TypeReference<List<Citation>> CITATION_LIST = new TypeReference<>() {
	};

	private final ChatSessionRepository sessions;
	private final ChatMessageRepository messages;
	private final DocumentRepository documents;
	private final CurrentUser currentUser;
	private final RetrievalService retrieval;
	private final GenerationService generation;
	private final JsonMapper json;
	private final TransactionTemplate transaction;
	private final int historyMessages;
	private final int maxQuestionLength;

	ChatService(ChatSessionRepository sessions, ChatMessageRepository messages, DocumentRepository documents,
			CurrentUser currentUser,
			RetrievalService retrieval, GenerationService generation, JsonMapper json,
			PlatformTransactionManager transactionManager, AppProperties properties) {
		this.sessions = sessions;
		this.messages = messages;
		this.documents = documents;
		this.currentUser = currentUser;
		this.retrieval = retrieval;
		this.generation = generation;
		this.json = json;
		this.transaction = new TransactionTemplate(transactionManager);
		this.historyMessages = properties.rag().historyMessages();
		this.maxQuestionLength = properties.rag().maxQuestionLength();
	}

	/** The user's chats, most recently used first. */
	@Transactional(readOnly = true)
	public @NonNull List<@NonNull ChatSessionDto> listSessions() {
		return sessions.findByOwnerIdOrderByUpdatedAtDesc(currentUser.get().getId()).stream()
				.map(ChatSessionDto::from)
				.toList();
	}

	/**
	 * @param documentIds documents the chat may use; empty means all of the user's documents
	 * @throws DocumentNotFoundException if any id isn't one of the user's documents
	 */
	@Transactional
	public @NonNull ChatSessionDto createSession(@NonNull List<@NonNull UUID> documentIds) {
		AppUser owner = currentUser.get();
		if (documentIds.isEmpty()) {
			return ChatSessionDto.from(sessions.save(new ChatSession(owner)));
		}
		Set<UUID> scope = Set.copyOf(documentIds);
		if (documents.countByOwnerIdAndIdIn(owner.getId(), scope) != scope.size()) {
			throw new DocumentNotFoundException();
		}
		return ChatSessionDto.from(sessions.save(new ChatSession(owner, scope)));
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
		if (text.length() > maxQuestionLength) {
			throw new InvalidInputException("Please keep your question under " + maxQuestionLength + " characters.");
		}
		UUID userId = currentUser.get().getId();

		// Read the scope and recent history before saving the new question, so history only holds earlier messages.
		AskContext context = transaction.execute(status -> {
			ChatSession session = ownSession(sessionId);
			List<GenerationService.HistoryMessage> recent = recentHistory(sessionId);
			if (ChatSession.DEFAULT_TITLE.equals(session.getTitle()) && recent.isEmpty()) {
				session.rename(autoTitle(text));
			}
			session.touch();
			messages.save(new ChatMessage(session, MessageRole.USER, text));
			return new AskContext(recent, session.isScoped(), session.getDocumentIds());
		});
		List<GenerationService.HistoryMessage> history = context.history();

		// A follow-up ("and for interns?") is searched as a standalone question; the answer still sees the
		// original wording plus the history.
		String searchQuery = generation.standaloneQuestion(history, text);
		// A scoped chat whose documents were all deleted searches nothing (not everything).
		List<Document> chunks;
		try {
			chunks = context.scoped() && context.documentIds().isEmpty()
					? List.of()
					: retrieval.search(userId, searchQuery, context.documentIds());
		}
		catch (RuntimeException e) {
			// Embedding the question failed (e.g. quota). Keep the chat consistent: the question gets a reply.
			log.warn("Retrieval failed for session {}", sessionId, e);
			AiServiceException friendly = AiServiceException.from(e);
			saveReply(sessionId, note(friendly.getMessage()), List.of(), new AtomicBoolean());
			throw friendly;
		}
		GenerationService.StreamingAnswer answer = generation.stream(text, history, chunks);

		StringBuilder fullText = new StringBuilder();
		AtomicBoolean saved = new AtomicBoolean();
		return answer.tokens()
				.doOnNext(fullText::append)
				// Save on a worker thread (JPA blocks), and before the browser sees onComplete.
				.concatWith(Mono.<String>fromRunnable(() -> saveReply(sessionId, fullText.toString(), answer.citations(), saved))
					.subscribeOn(Schedulers.boundedElastic()))
				.doOnCancel(() -> saveReply(sessionId, withNote(fullText, STOPPED_NOTE), answer.citations(), saved))
				.doOnError(error -> {
					log.warn("Answer generation failed for session {}", sessionId, error);
					String reason = note(AiServiceException.from(error).getMessage());
					saveReply(sessionId, withNote(fullText, reason), answer.citations(), saved);
				})
				// The browser gets the friendly message, not Google's raw error.
				.onErrorMap(AiServiceException::from);
	}

	/** Saves the assistant's message once, whichever of complete / cancel / error happens first. */
	private void saveReply(UUID sessionId, String text, List<Citation> allCitations, AtomicBoolean saved) {
		if (!saved.compareAndSet(false, true)) {
			return;
		}
		String content = text.isBlank() ? GenerationService.NO_ANSWER : text.strip();
		String citations = json.writeValueAsString(GenerationService.citedOnly(content, allCitations));
		// Runs outside the request thread, so no currentUser here; ask() already checked ownership.
		transaction.executeWithoutResult(status -> sessions.findById(sessionId).ifPresent(session -> {
			session.touch();
			messages.save(new ChatMessage(session, MessageRole.ASSISTANT, content, citations));
		}));
	}

	/** Formats a status note the way the chat shows it (italic, in parentheses). */
	static String note(String message) {
		return "_(" + message + ")_";
	}

	private static String withNote(StringBuilder partial, String note) {
		return partial.isEmpty() ? note : partial.toString().strip() + "\n\n" + note;
	}

	private record AskContext(List<GenerationService.HistoryMessage> history, boolean scoped, Set<UUID> documentIds) {
	}

	/** The last {@code app.rag.history-messages} messages, oldest first. */
	private List<GenerationService.HistoryMessage> recentHistory(UUID sessionId) {
		List<GenerationService.HistoryMessage> recent = new ArrayList<>(messages
				.findBySessionIdOrderBySeqDesc(sessionId, Limit.of(historyMessages)).stream()
				.map(message -> new GenerationService.HistoryMessage(message.getRole(), message.getContent()))
				.toList());
		Collections.reverse(recent);
		return recent;
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
