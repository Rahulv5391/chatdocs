package com.company.chatdocs.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingThrottlerTests {

	@Test
	void detectsRateLimitAnywhereInTheCauseChain() {
		var gemini = new RuntimeException("429 . Resource has been exhausted (e.g. check quota).");
		var wrapped = new RuntimeException("Failed to embed", gemini);

		assertThat(EmbeddingThrottler.isRateLimited(wrapped)).isTrue();
		assertThat(EmbeddingThrottler.isRateLimited(new RuntimeException("RESOURCE_EXHAUSTED"))).isTrue();
	}

	@Test
	void otherErrorsAreNotRetried() {
		assertThat(EmbeddingThrottler.isRateLimited(new RuntimeException("400 . API key not valid"))).isFalse();
		assertThat(EmbeddingThrottler.isRateLimited(new IllegalStateException())).isFalse();
	}

}
