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

	@BeforeEach
	void resetFakes() {
		chatModel.reset();
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
		UUID sessionId = chatService.createSession().id();

		var saved = chatService.ask(sessionId, "How many days of annual leave do employees get?");

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
	void offTopicQuestionSkipsTheModel() {
		ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		UUID sessionId = chatService.createSession().id();

		ChatMessageDto answer = chatService.ask(sessionId, "Explain quantum chromodynamics").get(1);

		assertThat(answer.content()).isEqualTo(GenerationService.NO_ANSWER);
		assertThat(answer.citations()).isEmpty();
		assertThat(chatModel.calls.get()).isZero();
	}

	@Test
	void onlyCitedPassagesAreKept() {
		ingest(user("demo"), "leave.txt", "Leave policy: employees get 24 days of annual leave.");
		ingest(user("demo"), "leave-interns.txt", "Leave policy for interns: interns get 10 days of annual leave.");
		chatModel.reply = "Interns get 10 days [2].";
		UUID sessionId = chatService.createSession().id();

		ChatMessageDto answer = chatService.ask(sessionId, "annual leave policy days").get(1);

		assertThat(answer.citations()).extracting(Citation::index).containsExactly(2);
	}

	@Test
	void neverUsesAnotherUsersDocuments() {
		ingest(user("alice"), "alice-leave.txt", "Leave policy: alice staff get 30 days of annual leave.");
		UUID sessionId = chatService.createSession().id();

		ChatMessageDto answer = chatService.ask(sessionId, "How many days of annual leave?").get(1);

		assertThat(answer.content()).isEqualTo(GenerationService.NO_ANSWER);
		assertThat(chatModel.calls.get()).isZero();
	}

	@Test
	void firstQuestionNamesTheChat() {
		UUID sessionId = chatService.createSession().id();

		chatService.ask(sessionId, "What is the travel policy for international trips and conferences abroad?");
		chatService.ask(sessionId, "Second question");

		assertThat(chatService.getSession(sessionId).title())
				.isEqualTo("What is the travel policy for international trips and confe…");
	}

	@Test
	void createRenameAndDeleteSession() {
		ChatSessionDto created = chatService.createSession();
		assertThat(created.title()).isEqualTo("New chat");

		assertThat(chatService.renameSession(created.id(), "  Leave questions  ").title()).isEqualTo("Leave questions");

		chatService.ask(created.id(), "hello");
		chatService.deleteSession(created.id());
		assertThat(chatService.listSessions()).isEmpty();
		assertThat(messages.count()).isZero();
	}

	@Test
	void messagesArePersistedInOrder() {
		UUID id = chatService.createSession().id();

		chatService.ask(id, "First?");
		chatService.ask(id, "Second?");

		assertThat(chatService.getMessages(id)).extracting(ChatMessageDto::role, ChatMessageDto::content)
				.containsExactly(
						tuple(MessageRole.USER, "First?"), tuple(MessageRole.ASSISTANT, GenerationService.NO_ANSWER),
						tuple(MessageRole.USER, "Second?"), tuple(MessageRole.ASSISTANT, GenerationService.NO_ANSWER));
	}

	@Test
	void rejectsBlankMessageAndTitle() {
		UUID id = chatService.createSession().id();

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
