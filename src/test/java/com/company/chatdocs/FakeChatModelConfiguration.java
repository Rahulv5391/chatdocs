package com.company.chatdocs;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces Gemini chat in tests. Remembers the last prompt, and answers with {@link FakeChatModel#reply}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeChatModelConfiguration {

	@Bean
	@Primary
	FakeChatModel fakeChatModel() {
		return new FakeChatModel();
	}

	public static class FakeChatModel implements ChatModel {

		public static final String DEFAULT_REPLY = "Employees get 24 days of annual leave [1].";

		public final AtomicInteger calls = new AtomicInteger();

		public volatile String reply = DEFAULT_REPLY;

		public volatile Prompt lastPrompt;

		@Override
		public ChatResponse call(Prompt prompt) {
			calls.incrementAndGet();
			lastPrompt = prompt;
			return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
		}

		public void reset() {
			calls.set(0);
			reply = DEFAULT_REPLY;
			lastPrompt = null;
		}

	}

}
