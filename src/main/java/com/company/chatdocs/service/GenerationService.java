package com.company.chatdocs.service;

import com.company.chatdocs.dto.Citation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG step 3 (generation): the only place that calls the chat LLM. Given a question and the retrieved chunks,
 * asks Gemini for an answer grounded in those chunks, with [n] citation markers.
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

	GenerationService(ChatClient chatClient, PromptBuilder promptBuilder,
			@Value("classpath:prompts/rag-system.st") Resource systemPrompt) {
		this.chatClient = chatClient;
		this.promptBuilder = promptBuilder;
		this.systemPrompt = systemPrompt;
	}

	public record Answer(String text, List<Citation> citations) {
	}

	/**
	 * @return the answer, and the citations whose [n] marker appears in it
	 */
	public Answer answer(String question, List<Document> chunks) {
		if (chunks.isEmpty()) {
			return new Answer(NO_ANSWER, List.of());
		}
		PromptBuilder.Context context = promptBuilder.build(chunks);

		long start = System.currentTimeMillis();
		String text = chatClient.prompt()
				.system(system -> system.text(systemPrompt).param("context", context.text()))
				.user(question)
				.call()
				.content();
		log.info("Generated answer from {} chunk(s) in {} ms", chunks.size(), System.currentTimeMillis() - start);

		String answer = text == null || text.isBlank() ? NO_ANSWER : text.strip();
		return new Answer(answer, citedOnly(answer, context.citations()));
	}

	/** Keeps only the passages the model actually cited, so the sources shown match the text. */
	private static List<Citation> citedOnly(String answer, List<Citation> citations) {
		Set<Integer> used = CITATION_MARKER.matcher(answer).results()
				.map(match -> Integer.parseInt(match.group(1)))
				.collect(Collectors.toSet());
		return citations.stream().filter(citation -> used.contains(citation.index())).toList();
	}

}
