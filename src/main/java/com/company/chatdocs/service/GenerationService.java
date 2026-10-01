package com.company.chatdocs.service;

import com.company.chatdocs.dto.Citation;
import com.company.chatdocs.entity.MessageRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG step 3 (generation): the only place that calls the chat LLM to answer questions (ingestion uses it too, in
 * {@link OcrService} and {@link SummaryService}). Given a question, the recent conversation
 * and the retrieved chunks, streams Gemini's answer, grounded in those chunks, with [n] citation markers.
 */
@Service
public class GenerationService {

	private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

	/** Also used when retrieval finds nothing, so the model isn't called at all. */
	public static final String NO_ANSWER = "I couldn't find that in your documents.";

	private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d+)]");

	private final ChatClient chatClient;
	private final PromptBuilder promptBuilder;
	private final Resource systemPrompt;
	private final Resource rewritePrompt;

	GenerationService(ChatClient chatClient, PromptBuilder promptBuilder,
			@Value("classpath:prompts/rag-system.st") Resource systemPrompt,
			@Value("classpath:prompts/rewrite-question.st") Resource rewritePrompt) {
		this.chatClient = chatClient;
		this.promptBuilder = promptBuilder;
		this.systemPrompt = systemPrompt;
		this.rewritePrompt = rewritePrompt;
	}

	/** One earlier message of the conversation, oldest first. */
	public record HistoryMessage(MessageRole role, String content) {
	}

	/**
	 * @param tokens    the answer, piece by piece as Gemini produces it (nothing happens until subscribed)
	 * @param citations one per retrieved chunk; use {@link #citedOnly} on the full text to keep the used ones
	 */
	public record StreamingAnswer(Flux<String> tokens, List<Citation> citations) {
	}

	/**
	 * Turns a follow-up like "and for interns?" into a standalone question ("What is the leave policy for
	 * interns?"), so retrieval searches for the right thing. Without history, or if the model fails, the
	 * question is returned unchanged.
	 */
	public String standaloneQuestion(List<HistoryMessage> history, String question) {
		if (history.isEmpty()) {
			return question;
		}
		try {
			String rewritten = chatClient.prompt()
					.user(user -> user.text(rewritePrompt)
							.param("history", transcript(history))
							.param("question", question))
					.call()
					.content();
			if (rewritten == null || rewritten.isBlank()) {
				return question;
			}
			log.info("Rewrote follow-up \"{}\" as \"{}\"", question, rewritten.strip());
			return rewritten.strip();
		}
		catch (RuntimeException e) {
			log.warn("Could not rewrite the follow-up question, searching with it as-is", e);
			return question;
		}
	}

	public StreamingAnswer stream(String question, List<HistoryMessage> history, List<Document> chunks) {
		if (chunks.isEmpty()) {
			return new StreamingAnswer(Flux.just(NO_ANSWER), List.of());
		}
		PromptBuilder.Context context = promptBuilder.build(chunks);

		Flux<String> tokens = Flux.defer(() -> {
			long start = System.currentTimeMillis();
			AtomicLong firstPieceAt = new AtomicLong();
			AtomicReference<Usage> usage = new AtomicReference<>();
			return chatClient.prompt()
					.system(system -> system.text(systemPrompt).param("context", context.text()))
					.messages(toMessages(history))
					.user(question)
					.stream()
					.chatResponse()
					.doOnNext(response -> {
						firstPieceAt.compareAndSet(0, System.currentTimeMillis());
						// Gemini reports token counts on the last piece(s); keep the latest non-empty one.
						Usage pieceUsage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
						if (pieceUsage != null && pieceUsage.getTotalTokens() != null && pieceUsage.getTotalTokens() > 0) {
							usage.set(pieceUsage);
						}
					})
					.mapNotNull(GenerationService::text)
					.doOnComplete(() -> logAnswer(start, firstPieceAt.get(), usage.get(), chunks.size(), history.size()));
		});
		return new StreamingAnswer(tokens, context.citations());
	}

	private static String text(ChatResponse response) {
		return response.getResult() == null ? null : response.getResult().getOutput().getText();
	}

	/** One line per answer: how long it took and how many tokens it used (to watch the free-tier budget). */
	private static void logAnswer(long start, long firstPieceAt, Usage usage, int chunks, int historyMessages) {
		long now = System.currentTimeMillis();
		log.info("Answer: {} ms total, first text after {} ms | tokens: prompt={}, completion={}, total={} "
				+ "| {} chunk(s), {} history message(s)",
				now - start, firstPieceAt == 0 ? -1 : firstPieceAt - start,
				usage == null ? "?" : usage.getPromptTokens(), usage == null ? "?" : usage.getCompletionTokens(),
				usage == null ? "?" : usage.getTotalTokens(), chunks, historyMessages);
	}

	/** Keeps only the passages the model actually cited, so the sources shown match the text. */
	public static List<Citation> citedOnly(String answer, List<Citation> citations) {
		Set<Integer> used = CITATION_MARKER.matcher(answer).results()
				.map(match -> Integer.parseInt(match.group(1)))
				.collect(Collectors.toSet());
		return citations.stream().filter(citation -> used.contains(citation.index())).toList();
	}

	/**
	 * Earlier answers' [n] markers pointed at earlier passages; this turn's passages are numbered afresh,
	 * so the old markers are removed to avoid confusing the model.
	 */
	private static List<Message> toMessages(List<HistoryMessage> history) {
		return history.stream()
				.map(message -> message.role() == MessageRole.USER
						? (Message) new UserMessage(message.content())
						: new AssistantMessage(withoutMarkers(message.content())))
				.toList();
	}

	private static String transcript(List<HistoryMessage> history) {
		return history.stream()
				.map(message -> (message.role() == MessageRole.USER ? "User: " : "Assistant: ")
						+ withoutMarkers(message.content()))
				.collect(Collectors.joining("\n"));
	}

	private static String withoutMarkers(String text) {
		return CITATION_MARKER.matcher(text).replaceAll("").replaceAll(" +([.,;:])", "$1");
	}

}
