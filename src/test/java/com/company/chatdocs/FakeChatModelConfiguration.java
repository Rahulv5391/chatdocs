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
 * Replaces Gemini chat in tests. Streamed answers use {@link FakeChatModel#reply}; the non-streaming call (used to
 * rewrite follow-up questions) returns {@link FakeChatModel#rewriteReply}, or the follow-up unchanged if that is null.
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

		/** Prompt of the last non-streaming call (the follow-up rewrite). */
		public volatile Prompt lastCallPrompt;

		/** Prompt of the last streamed answer. */
		public volatile Prompt lastStreamPrompt;

		public final AtomicInteger rewriteCalls = new AtomicInteger();

		public volatile String rewriteReply;

		/** Delay between streamed words, to test Stop while an answer is still coming in. */
		public volatile Duration streamDelay = Duration.ZERO;

		/** If set, the stream fails with this after the first word. */
		public volatile RuntimeException failure;

		@Override
		public ChatResponse call(Prompt prompt) {
			calls.incrementAndGet();
			rewriteCalls.incrementAndGet();
			lastCallPrompt = prompt;
			if (rewriteReply != null) {
				return response(rewriteReply);
			}
			String text = prompt.getUserMessage().getText();
			int marker = text.lastIndexOf("Last message:");
			return response(marker < 0 ? text : text.substring(marker + "Last message:".length()).strip());
		}

		/** Streams the reply word by word, like Gemini sends small pieces of text. */
		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			calls.incrementAndGet();
			lastStreamPrompt = prompt;
			Flux<ChatResponse> words = Flux.fromArray(reply.split("(?<= )")).map(FakeChatModel::response);
			if (failure != null) {
				words = words.take(1).concatWith(Flux.error(failure));
			}
			return streamDelay.isZero() ? words : words.delayElements(streamDelay);
		}

		public void reset() {
			calls.set(0);
			rewriteCalls.set(0);
			reply = DEFAULT_REPLY;
			rewriteReply = null;
			lastCallPrompt = null;
			lastStreamPrompt = null;
			streamDelay = Duration.ZERO;
			failure = null;
		}

		private static ChatResponse response(String text) {
			return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
		}

	}

}
