package com.company.chatdocs;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.time.Duration;
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

		/** Delay between streamed words, to test Stop while an answer is still coming in. */
		public volatile Duration streamDelay = Duration.ZERO;

		/** If set, the stream fails with this after the first word. */
		public volatile RuntimeException failure;

		@Override
		public ChatResponse call(Prompt prompt) {
			calls.incrementAndGet();
			lastPrompt = prompt;
			return response(reply);
		}

		/** Streams the reply word by word, like Gemini sends small pieces of text. */
		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			calls.incrementAndGet();
			lastPrompt = prompt;
			Flux<ChatResponse> words = Flux.fromArray(reply.split("(?<= )")).map(FakeChatModel::response);
			if (failure != null) {
				words = words.take(1).concatWith(Flux.error(failure));
			}
			return streamDelay.isZero() ? words : words.delayElements(streamDelay);
		}

		public void reset() {
			calls.set(0);
			reply = DEFAULT_REPLY;
			lastPrompt = null;
			streamDelay = Duration.ZERO;
			failure = null;
		}

		private static ChatResponse response(String text) {
			return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
		}

	}

}
