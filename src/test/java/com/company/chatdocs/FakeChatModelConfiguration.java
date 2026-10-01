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
 * Replaces Gemini chat in tests. Streamed answers use {@link FakeChatModel#reply}. Non-streaming calls: a page image
 * (OCR) gets {@link FakeChatModel#ocrReply}, a summary request gets {@link FakeChatModel#summaryReply}, and the
 * follow-up rewrite gets {@link FakeChatModel#rewriteReply}, or the follow-up unchanged if that is null.
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

		/** Calls made while chatting (answers and follow-up rewrites); OCR and summaries have their own counters. */
		public final AtomicInteger calls = new AtomicInteger();

		public volatile String reply = DEFAULT_REPLY;

		/** Prompt of the last non-streaming call (the follow-up rewrite). */
		public volatile Prompt lastCallPrompt;

		/** Prompt of the last streamed answer. */
		public volatile Prompt lastStreamPrompt;

		public final AtomicInteger rewriteCalls = new AtomicInteger();

		public volatile String rewriteReply;

		public static final String DEFAULT_OCR_REPLY = "Remote work policy. Employees may work from home on Fridays.";

		public volatile String ocrReply = DEFAULT_OCR_REPLY;

		public final AtomicInteger ocrCalls = new AtomicInteger();

		public static final String DEFAULT_SUMMARY = "A short summary of the document.";

		public volatile String summaryReply = DEFAULT_SUMMARY;

		public final AtomicInteger summaryCalls = new AtomicInteger();

		/** Delay between streamed words, to test Stop while an answer is still coming in. */
		public volatile Duration streamDelay = Duration.ZERO;

		/** If set, the stream fails with this after the first word. */
		public volatile RuntimeException failure;

		@Override
		public ChatResponse call(Prompt prompt) {
			if (!prompt.getUserMessage().getMedia().isEmpty()) {
				ocrCalls.incrementAndGet();
				return response(ocrReply);
			}
			if (prompt.getUserMessage().getText().contains("Document to summarize")) {
				summaryCalls.incrementAndGet();
				return response(summaryReply);
			}
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
			ocrCalls.set(0);
			ocrReply = DEFAULT_OCR_REPLY;
			summaryCalls.set(0);
			summaryReply = DEFAULT_SUMMARY;
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
