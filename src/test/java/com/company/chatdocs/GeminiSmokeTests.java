package com.company.chatdocs;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real Gemini API, so it uses free-tier quota and needs GEMINI_API_KEY (from .env).
 * Skipped by default. Run it with: ./mvnw test -Dtest=GeminiSmokeTests -Dexternal=true
 */
@Tag("external")
@EnabledIfSystemProperty(named = "external", matches = "true")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GeminiSmokeTests {

	private static final Logger log = LoggerFactory.getLogger(GeminiSmokeTests.class);

	@Autowired
	ChatClient chatClient;

	@Autowired
	EmbeddingModel embeddingModel;

	@Test
	void chatModelReplies() {
		String reply = chatClient.prompt().user("Say hello in one short sentence.").call().content();

		log.info("Gemini reply: {}", reply);
		assertThat(reply).isNotBlank();
	}

	@Test
	void chatModelStreamsInPieces() {
		List<String> pieces = chatClient.prompt()
				.user("Write about 150 words on why teams document their policies.")
				.stream()
				.content()
				.collectList()
				.block(Duration.ofSeconds(60));

		log.info("Gemini streamed {} piece(s): {}", pieces.size(), pieces);
		// A short reply can arrive as one piece; ~150 words comes in several.
		assertThat(pieces).hasSizeGreaterThan(1);
		assertThat(String.join("", pieces)).isNotBlank();
	}

	@Test
	void embeddingHas768Dimensions() {
		float[] embedding = embeddingModel.embed("Chat with my docs turns documents into searchable vectors.");

		log.info("embedding length = {}", embedding.length);
		assertThat(embedding).hasSize(768);
	}

}
