package com.company.chatdocs;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adds 3 tiny texts to the pgvector table, searches them, then deletes them.
 * Uses real Gemini embeddings (4 calls), so it's skipped by default.
 * Run it with: ./mvnw test -Dtest=VectorStoreSmokeTests -Dexternal=true
 */
@Tag("external")
@EnabledIfSystemProperty(named = "external", matches = "true")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VectorStoreSmokeTests {

	private static final Logger log = LoggerFactory.getLogger(VectorStoreSmokeTests.class);

	@Autowired
	VectorStore vectorStore;

	@Test
	void returnsMostRelevantTextFirst() {
		List<Document> texts = List.of(
				new Document("The cafeteria serves vegetarian lunch every Friday.", Map.of("source", "smoke")),
				new Document("Employees get 24 days of paid annual leave per year.", Map.of("source", "smoke")),
				new Document("Use the VPN when working from a coffee shop.", Map.of("source", "smoke")));
		vectorStore.add(texts);
		try {
			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
					.query("How many vacation days do I get?")
					.topK(3)
					.filterExpression("source == 'smoke'")
					.build());

			results.forEach(result -> log.info("score {} -> {}", result.getScore(), result.getText()));
			assertThat(results).hasSize(3);
			assertThat(results.getFirst().getText()).contains("annual leave");
		}
		finally {
			vectorStore.delete(texts.stream().map(Document::getId).toList());
		}
	}

}
