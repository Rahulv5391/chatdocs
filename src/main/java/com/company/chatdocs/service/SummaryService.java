package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Part of RAG step 1 (ingestion): writes a short summary of a whole document, so later steps can answer
 * "what is this document about?" without retrieving every chunk.
 */
@Service
public class SummaryService {

	private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

	private final ChatClient chatClient;
	private final Resource prompt;
	private final boolean enabled;
	private final int maxInputChars;

	SummaryService(ChatClient chatClient, AppProperties properties,
			@Value("classpath:prompts/summarize-document.st") Resource prompt) {
		this.chatClient = chatClient;
		this.prompt = prompt;
		this.enabled = properties.ingestion().summarize();
		this.maxInputChars = properties.ingestion().summaryInputChars();
	}

	/**
	 * Best effort: a summary is nice to have, so a failure (e.g. quota) is logged and the document still becomes
	 * READY without one.
	 *
	 * @param pages the document's text, page by page
	 * @return the summary, or null if summaries are turned off or it couldn't be written
	 */
	public @Nullable String summarize(String fileName, List<Document> pages) {
		if (!enabled) {
			return null;
		}
		String text = pages.stream()
				.map(Document::getText)
				.filter(Objects::nonNull)
				.collect(Collectors.joining("\n\n"));
		if (text.length() > maxInputChars) {
			text = text.substring(0, maxInputChars);
		}
		String input = text;
		try {
			String summary = RateLimitRetry.call(() -> chatClient.prompt()
					.user(user -> user.text(prompt).param("fileName", fileName).param("text", input))
					.call()
					.content());
			return summary == null || summary.isBlank() ? null : summary.strip();
		}
		catch (RuntimeException e) {
			log.warn("Could not summarize '{}', continuing without a summary", fileName, e);
			return null;
		}
	}

}
