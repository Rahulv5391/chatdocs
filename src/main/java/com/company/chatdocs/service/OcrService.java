package com.company.chatdocs.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

/**
 * Part of RAG step 1 (ingestion): reads the text of a scanned page by sending its image to Gemini, which is
 * multimodal. Used for PDF pages that have no text layer.
 */
@Service
public class OcrService {

	private final ChatClient chatClient;
	private final Resource prompt;

	OcrService(ChatClient chatClient, @Value("classpath:prompts/ocr-page.st") Resource prompt) {
		this.chatClient = chatClient;
		this.prompt = prompt;
	}

	/**
	 * @param png the page rendered as a PNG image
	 * @return the page's text, or an empty string if it has none
	 */
	public String readPage(byte[] png) {
		String text = RateLimitRetry.call(() -> chatClient.prompt()
				.user(user -> user.text(prompt).media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(png)))
				.call()
				.content());
		return text == null ? "" : text.strip();
	}

}
