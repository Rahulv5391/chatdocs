package com.company.chatdocs.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceExceptionTests {

	@Test
	void detectsRateLimitAnywhereInTheCauseChain() {
		var gemini = new RuntimeException("429 . Resource has been exhausted (e.g. check quota).");
		var wrapped = new RuntimeException("Failed to embed", gemini);

		assertThat(AiServiceException.isRateLimited(wrapped)).isTrue();
		assertThat(AiServiceException.isRateLimited(new RuntimeException("RESOURCE_EXHAUSTED"))).isTrue();
	}

	@Test
	void otherErrorsAreNotRateLimits() {
		assertThat(AiServiceException.isRateLimited(new RuntimeException("400 . API key not valid"))).isFalse();
		assertThat(AiServiceException.isRateLimited(new IllegalStateException())).isFalse();
	}

	@Test
	void friendlyMessageHidesTheRawError() {
		assertThat(AiServiceException.from(new RuntimeException("429 quota"))).hasMessage(AiServiceException.QUOTA_MESSAGE);
		assertThat(AiServiceException.from(new RuntimeException("socket closed")))
				.hasMessage(AiServiceException.UNAVAILABLE_MESSAGE);
	}

}
