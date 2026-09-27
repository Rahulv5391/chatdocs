package com.company.chatdocs.service;

import com.company.chatdocs.TestcontainersConfiguration;
import com.company.chatdocs.dto.ChatMessageDto;
import com.company.chatdocs.dto.ChatSessionDto;
import com.company.chatdocs.entity.ChatSession;
import com.company.chatdocs.entity.MessageRole;
import com.company.chatdocs.exception.ChatSessionNotFoundException;
import com.company.chatdocs.exception.InvalidInputException;
import com.company.chatdocs.repository.AppUserRepository;
import com.company.chatdocs.repository.ChatMessageRepository;
import com.company.chatdocs.repository.ChatSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@WithMockUser(username = "demo")
class ChatServiceTests {

	@Autowired
	ChatService chatService;

	@Autowired
	ChatSessionRepository sessions;

	@Autowired
	ChatMessageRepository messages;

	@Autowired
	AppUserRepository users;

	@AfterEach
	void cleanUp() {
		sessions.deleteAll();
	}

	@Test
	void createRenameAndDeleteSession() {
		ChatSessionDto created = chatService.createSession();
		assertThat(created.title()).isEqualTo("New chat");
		assertThat(chatService.listSessions()).extracting(ChatSessionDto::id).containsExactly(created.id());

		ChatSessionDto renamed = chatService.renameSession(created.id(), "  Leave questions  ");
		assertThat(renamed.title()).isEqualTo("Leave questions");
		assertThat(chatService.getSession(created.id()).title()).isEqualTo("Leave questions");

		chatService.sendMessage(created.id(), "hello");
		chatService.deleteSession(created.id());
		assertThat(chatService.listSessions()).isEmpty();
		assertThat(messages.count()).isZero();
	}

	@Test
	void messagesArePersistedInOrderWithPlaceholderReply() {
		UUID id = chatService.createSession().id();

		chatService.sendMessage(id, "What is the leave policy?");
		chatService.sendMessage(id, "And for interns?");

		assertThat(chatService.getMessages(id))
				.extracting(ChatMessageDto::role, ChatMessageDto::content)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(MessageRole.USER, "What is the leave policy?"),
						org.assertj.core.groups.Tuple.tuple(MessageRole.ASSISTANT, ChatService.PLACEHOLDER_REPLY),
						org.assertj.core.groups.Tuple.tuple(MessageRole.USER, "And for interns?"),
						org.assertj.core.groups.Tuple.tuple(MessageRole.ASSISTANT, ChatService.PLACEHOLDER_REPLY));
	}

	@Test
	void sendingAMessageMovesSessionToTop() throws Exception {
		UUID older = chatService.createSession().id();
		Thread.sleep(5);
		UUID newer = chatService.createSession().id();
		assertThat(chatService.listSessions()).extracting(ChatSessionDto::id).containsExactly(newer, older);

		Thread.sleep(5);
		chatService.sendMessage(older, "bump");

		assertThat(chatService.listSessions()).extracting(ChatSessionDto::id).containsExactly(older, newer);
	}

	@Test
	void rejectsBlankMessageAndTitle() {
		UUID id = chatService.createSession().id();

		assertThatThrownBy(() -> chatService.sendMessage(id, "   ")).isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> chatService.renameSession(id, "")).isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> chatService.renameSession(id, "x".repeat(201)))
				.isInstanceOf(InvalidInputException.class);
	}

	@Test
	@WithMockUser(username = "alice")
	void cannotSeeOrChangeAnotherUsersChat() {
		ChatSession demoChat = sessions.save(new ChatSession(users.findByUsername("demo").orElseThrow()));
		UUID id = demoChat.getId();

		assertThat(chatService.listSessions()).isEmpty();
		assertThatThrownBy(() -> chatService.getMessages(id)).isInstanceOf(ChatSessionNotFoundException.class);
		assertThatThrownBy(() -> chatService.sendMessage(id, "hi")).isInstanceOf(ChatSessionNotFoundException.class);
		assertThatThrownBy(() -> chatService.renameSession(id, "mine")).isInstanceOf(ChatSessionNotFoundException.class);
		assertThatThrownBy(() -> chatService.deleteSession(id)).isInstanceOf(ChatSessionNotFoundException.class);
		assertThat(sessions.findById(id)).isPresent();
	}

}
