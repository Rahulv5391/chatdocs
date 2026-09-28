package com.company.chatdocs.service;

import com.company.chatdocs.FakeChatModelConfiguration;
import com.company.chatdocs.FakeChatModelConfiguration.FakeChatModel;
import com.company.chatdocs.FakeEmbeddingModelConfiguration;
import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.dto.ChatMessageDto;
import com.company.chatdocs.dto.ChatSessionDto;
import com.company.chatdocs.dto.Citation;
import com.company.chatdocs.entity.AppUser;
import com.company.chatdocs.entity.ChatSession;
import com.company.chatdocs.entity.Document;
import com.company.chatdocs.entity.MessageRole;
import com.company.chatdocs.exception.AiServiceException;
import com.company.chatdocs.exception.ChatSessionNotFoundException;
import com.company.chatdocs.exception.InvalidInputException;
import com.company.chatdocs.repository.AppUserRepository;
import com.company.chatdocs.repository.ChatMessageRepository;
import com.company.chatdocs.repository.ChatSessionRepository;
import com.company.chatdocs.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/** Uses fake embedding and chat models, so no Gemini quota is used. */
@Import({ TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class, FakeChatModelConfiguration.class })
@SpringBootTest(properties = { "app.storage-dir=target/test-uploads", "app.rag.similarity-threshold=0.1" })
@ActiveProfiles("dev")
@WithMockUser(username = "demo")
class ChatServiceTests {

	@Autowired
	ChatService chatService;

	@Autowired
	IngestionService ingestionService;

	@Autowired
	ChatSessionRepository sessions;

	@Autowired
	ChatMessageRepository messages;

	@Autowired
	DocumentRepository documents;

	@Autowired
	AppUserRepository users;

	@Autowired
	FileStorageService storage;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	FakeChatModel chatModel;

	@Autowired
	FakeEmbeddingModelConfiguration.FakeEmbeddingModel embeddingModel;

	@BeforeEach
	void resetFakes() {
		chatModel.reset();
		embeddingModel.failure = null;
	}

	@AfterEach
	void cleanUp() {
		sessions.deleteAll();
		documents.findAll().forEach(document -> storage.delete(document.getStoragePath()));
		documents.deleteAll();
		jdbc.update("delete from vector_store");
	}

	@Test
	void answersFromDocumentsWithCitations() {
		UUID docId = ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		UUID sessionId = chatService.createSession(List.of()).id();

		var saved = askAndWait(sessionId, "How many days of annual leave do employees get?");

		assertThat(saved).extracting(ChatMessageDto::role).containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
		ChatMessageDto answer = saved.get(1);
		assertThat(answer.content()).isEqualTo(FakeChatModel.DEFAULT_REPLY);
		assertThat(answer.citations()).extracting(Citation::index, Citation::documentId, Citation::fileName)
				.containsExactly(tuple(1, docId, "leave.txt"));
		assertThat(answer.citations().getFirst().snippet()).contains("24 days of annual leave");

		// The prompt had the rules, the numbered context and the question.
		String system = chatModel.lastPrompt.getSystemMessage().getText();
		assertThat(system).contains("ONLY the numbered context", "[1] (leave.txt)", "24 days of annual leave");
		assertThat(chatModel.lastPrompt.getUserMessage().getText()).contains("How many days");

		// Citations survive a reload.
		assertThat(chatService.getMessages(sessionId).get(1).citations()).isEqualTo(answer.citations());
	}

	@Test
	void answerIsStreamedInPiecesAndSavedWhenComplete() {
		ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		UUID sessionId = chatService.createSession(List.of()).id();

		List<String> tokens = chatService.ask(sessionId, "annual leave days").collectList().block(Duration.ofSeconds(10));

		assertThat(tokens).hasSizeGreaterThan(1);
		assertThat(String.join("", tokens)).isEqualTo(FakeChatModel.DEFAULT_REPLY);
		ChatMessageDto saved = chatService.getMessages(sessionId).getLast();
		assertThat(saved.content()).isEqualTo(FakeChatModel.DEFAULT_REPLY);
		assertThat(saved.citations()).extracting(Citation::index).containsExactly(1);
	}

	@Test
	void stoppingSavesThePartialAnswer() {
		ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		chatModel.streamDelay = Duration.ofMillis(50);
		UUID sessionId = chatService.createSession(List.of()).id();

		// take(2) cancels the stream after two pieces, like the Stop button.
		List<String> tokens = chatService.ask(sessionId, "annual leave days").take(2).collectList()
				.block(Duration.ofSeconds(10));

		ChatMessageDto saved = chatService.getMessages(sessionId).getLast();
		assertThat(saved.role()).isEqualTo(MessageRole.ASSISTANT);
		assertThat(saved.content()).startsWith(String.join("", tokens).strip()).endsWith(ChatService.STOPPED_NOTE);
		assertThat(chatService.getMessages(sessionId)).hasSize(2);
	}

	@Test
	void failedGenerationSavesANoteAndReportsAFriendlyError() {
		ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		chatModel.failure = new IllegalStateException("Gemini is down");
		UUID sessionId = chatService.createSession(List.of()).id();

		assertThatThrownBy(() -> chatService.ask(sessionId, "annual leave days").blockLast(Duration.ofSeconds(10)))
				.hasMessage(AiServiceException.UNAVAILABLE_MESSAGE);

		assertThat(chatService.getMessages(sessionId).getLast().content())
				.endsWith(ChatService.note(com.company.chatdocs.exception.AiServiceException.UNAVAILABLE_MESSAGE));
	}

	@Test
	void followUpIsRewrittenBeforeRetrievalAndHistoryIsSent() {
		ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		ingest(user("demo"), "interns.txt", "Intern handbook: interns get 10 days of annual leave.");
		UUID sessionId = chatService.createSession(List.of()).id();
		askAndWait(sessionId, "What is the leave policy?");
		assertThat(chatModel.rewriteCalls.get()).as("first question is not rewritten").isZero();

		// "what about them?" shares no words with the documents, so without the rewrite nothing would be found.
		chatModel.rewriteReply = "How many days of annual leave do interns get?";
		ChatMessageDto answer = askAndWait(sessionId, "what about them?").get(1);

		assertThat(chatModel.rewriteCalls.get()).isEqualTo(1);
		assertThat(chatModel.lastCallPrompt.getUserMessage().getText())
				.contains("User: What is the leave policy?", "Last message: what about them?");
		assertThat(answer.content()).isNotEqualTo(GenerationService.NO_ANSWER);
		assertThat(answer.citations()).extracting(Citation::fileName).containsExactly("interns.txt");

		// The answer prompt: system rules, the earlier question and answer, then the follow-up in its own words.
		var instructions = chatModel.lastStreamPrompt.getInstructions();
		assertThat(instructions).extracting(message -> message.getMessageType().name())
				.containsExactly("SYSTEM", "USER", "ASSISTANT", "USER");
		assertThat(instructions.get(1).getText()).isEqualTo("What is the leave policy?");
		assertThat(instructions.get(2).getText()).as("old [n] markers are removed")
				.isEqualTo("Employees get 24 days of annual leave.");
		assertThat(instructions.get(3).getText()).isEqualTo("what about them?");
	}

	@Test
	void historyIsLimitedToTheLastMessages() {
		UUID sessionId = chatService.createSession(List.of()).id();
		ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		for (int i = 1; i <= 4; i++) {
			askAndWait(sessionId, "annual leave question " + i);
		}

		// 8 earlier messages exist; only the last 6 (app.rag.history-messages) are sent, plus system + question.
		askAndWait(sessionId, "annual leave question 5");
		var instructions = chatModel.lastStreamPrompt.getInstructions();
		assertThat(instructions).hasSize(1 + 6 + 1);
		assertThat(instructions.get(1).getText()).isEqualTo("annual leave question 2");
	}

	@Test
	void scopedChatOnlyUsesChosenDocuments() {
		UUID staff = ingest(user("demo"), "staff-leave.txt", "Leave policy: staff get 24 days of annual leave.");
		ingest(user("demo"), "intern-leave.txt", "Leave policy: interns get 10 days of annual leave.");

		ChatSessionDto session = chatService.createSession(List.of(staff));
		assertThat(session.scoped()).isTrue();
		assertThat(session.documentIds()).containsExactly(staff);

		ChatMessageDto answer = askAndWait(session.id(), "How many days of annual leave do interns get?").get(1);

		assertThat(chatModel.lastStreamPrompt.getSystemMessage().getText())
				.contains("staff-leave.txt").doesNotContain("intern-leave.txt");
		assertThat(answer.citations()).extracting(Citation::fileName).containsOnly("staff-leave.txt");
	}

	@Test
	void scopedChatWhoseDocumentsWereDeletedSearchesNothing() {
		UUID only = ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		ingest(user("demo"), "other.txt", "Leave policy: everyone else gets 20 days of annual leave.");
		UUID sessionId = chatService.createSession(List.of(only)).id();

		documents.deleteById(only);

		assertThat(chatService.getSession(sessionId).documentIds()).isEmpty();
		ChatMessageDto answer = askAndWait(sessionId, "annual leave days").get(1);
		assertThat(answer.content()).isEqualTo(GenerationService.NO_ANSWER);
		assertThat(chatModel.calls.get()).isZero();
	}

	@Test
	void cannotScopeAChatToAnotherUsersDocument() {
		UUID aliceDoc = ingest(user("alice"), "alice.txt", "Alice's notes about annual leave.");

		assertThatThrownBy(() -> chatService.createSession(List.of(aliceDoc)))
				.isInstanceOf(com.company.chatdocs.exception.DocumentNotFoundException.class);
		assertThat(sessions.count()).isZero();
	}

	@Test
	void quotaErrorWhileAnsweringGivesAFriendlyMessage() {
		ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		chatModel.failure = new RuntimeException("429 . Resource has been exhausted (e.g. check quota).");
		UUID sessionId = chatService.createSession(List.of()).id();

		assertThatThrownBy(() -> chatService.ask(sessionId, "annual leave days").blockLast(Duration.ofSeconds(10)))
				.isInstanceOf(AiServiceException.class)
				.hasMessage(AiServiceException.QUOTA_MESSAGE);
		assertThat(chatService.getMessages(sessionId).getLast().content())
				.endsWith(ChatService.note(AiServiceException.QUOTA_MESSAGE));
	}

	@Test
	void embeddingFailureDuringRetrievalStillAnswersTheQuestion() {
		UUID sessionId = chatService.createSession(List.of()).id();
		embeddingModel.failure = new IllegalStateException("503 Service Unavailable");

		assertThatThrownBy(() -> chatService.ask(sessionId, "annual leave days"))
				.isInstanceOf(AiServiceException.class)
				.hasMessage(AiServiceException.UNAVAILABLE_MESSAGE);
		// The question isn't left without a reply.
		assertThat(chatService.getMessages(sessionId)).extracting(ChatMessageDto::role)
				.containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
	}

	@Test
	void tooLongQuestionIsRejected() {
		UUID sessionId = chatService.createSession(List.of()).id();

		assertThatThrownBy(() -> chatService.ask(sessionId, "x".repeat(2001)))
				.isInstanceOf(InvalidInputException.class)
				.hasMessageContaining("2000 characters");
		assertThat(chatService.getMessages(sessionId)).isEmpty();
	}

	@Test
	void offTopicQuestionSkipsTheModel() {
		ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		UUID sessionId = chatService.createSession(List.of()).id();

		ChatMessageDto answer = askAndWait(sessionId, "Explain quantum chromodynamics").get(1);

		assertThat(answer.content()).isEqualTo(GenerationService.NO_ANSWER);
		assertThat(answer.citations()).isEmpty();
		assertThat(chatModel.calls.get()).isZero();
	}

	@Test
	void onlyCitedPassagesAreKept() {
		ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		ingest(user("demo"), "leave-interns.txt", "Leave policy for interns: interns get 10 days of annual leave.");
		chatModel.reply = "Interns get 10 days [2].";
		UUID sessionId = chatService.createSession(List.of()).id();

		ChatMessageDto answer = askAndWait(sessionId, "annual leave policy days").get(1);

		assertThat(answer.citations()).extracting(Citation::index).containsExactly(2);
	}

	@Test
	void neverUsesAnotherUsersDocuments() {
		ingest(user("alice"), "alice-leave.txt", "Leave policy: alice staff get 30 days of annual leave.");
		UUID sessionId = chatService.createSession(List.of()).id();

		ChatMessageDto answer = askAndWait(sessionId, "How many days of annual leave?").get(1);

		assertThat(answer.content()).isEqualTo(GenerationService.NO_ANSWER);
		assertThat(chatModel.calls.get()).isZero();
	}

	@Test
	void firstQuestionNamesTheChat() {
		UUID sessionId = chatService.createSession(List.of()).id();

		askAndWait(sessionId, "What is the travel policy for international trips and conferences abroad?");
		askAndWait(sessionId, "Second question");

		assertThat(chatService.getSession(sessionId).title())
				.isEqualTo("What is the travel policy for international trips and confe…");
	}

	@Test
	void createRenameAndDeleteSession() {
		ChatSessionDto created = chatService.createSession(List.of());
		assertThat(created.title()).isEqualTo("New chat");

		assertThat(chatService.renameSession(created.id(), "  Leave questions  ").title()).isEqualTo("Leave questions");

		askAndWait(created.id(), "hello");
		chatService.deleteSession(created.id());
		assertThat(chatService.listSessions()).isEmpty();
		assertThat(messages.count()).isZero();
	}

	@Test
	void messagesArePersistedInOrder() {
		UUID id = chatService.createSession(List.of()).id();

		askAndWait(id, "First?");
		askAndWait(id, "Second?");

		assertThat(chatService.getMessages(id)).extracting(ChatMessageDto::role, ChatMessageDto::content)
				.containsExactly(
						tuple(MessageRole.USER, "First?"), tuple(MessageRole.ASSISTANT, GenerationService.NO_ANSWER),
						tuple(MessageRole.USER, "Second?"), tuple(MessageRole.ASSISTANT, GenerationService.NO_ANSWER));
	}

	@Test
	void rejectsBlankMessageAndTitle() {
		UUID id = chatService.createSession(List.of()).id();

		assertThatThrownBy(() -> chatService.ask(id, "   ")).isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> chatService.renameSession(id, "")).isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> chatService.renameSession(id, "x".repeat(201)))
				.isInstanceOf(InvalidInputException.class);
	}

	@Test
	@WithMockUser(username = "alice")
	void cannotSeeOrChangeAnotherUsersChat() {
		UUID id = sessions.save(new ChatSession(user("demo"))).getId();

		assertThat(chatService.listSessions()).isEmpty();
		assertThatThrownBy(() -> chatService.getMessages(id)).isInstanceOf(ChatSessionNotFoundException.class);
		assertThatThrownBy(() -> chatService.ask(id, "hi")).isInstanceOf(ChatSessionNotFoundException.class);
		assertThatThrownBy(() -> chatService.renameSession(id, "mine")).isInstanceOf(ChatSessionNotFoundException.class);
		assertThatThrownBy(() -> chatService.deleteSession(id)).isInstanceOf(ChatSessionNotFoundException.class);
		assertThat(sessions.findById(id)).isPresent();
		assertThat(messages.count()).isZero();
	}

	/** Runs the streaming ask to the end and returns the saved question and answer. */
	private List<ChatMessageDto> askAndWait(UUID sessionId, String question) {
		chatService.ask(sessionId, question).blockLast(Duration.ofSeconds(10));
		var all = chatService.getMessages(sessionId);
		return all.subList(all.size() - 2, all.size());
	}

	private AppUser user(String username) {
		return users.findByUsername(username).orElseThrow();
	}

	private UUID ingest(AppUser owner, String fileName, String text) {
		String path = storage.save(owner.getId(), "txt", text.getBytes());
		Document document = documents.save(new Document(owner, fileName, "text/plain", text.length(), path,
				UUID.randomUUID().toString().replace("-", "").repeat(2)));
		ingestionService.ingest(document.getId());
		return document.getId();
	}

}
