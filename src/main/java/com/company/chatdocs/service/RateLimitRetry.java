package com.company.chatdocs.service;

import com.company.chatdocs.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Retries a Gemini call that hit the rate limit (429). Waits 30s, then 60s, then 120s: per-minute quotas usually
 * reset within that time. Any other error, or a 429 after the last retry, is thrown as-is.
 */
final class RateLimitRetry {

	private static final Logger log = LoggerFactory.getLogger(RateLimitRetry.class);

	private static final RetryTemplate RETRY_TEMPLATE = new RetryTemplate(RetryPolicy.builder()
			.predicate(RateLimitRetry::shouldRetry)
			.maxRetries(3)
			.delay(Duration.ofSeconds(30))
			.multiplier(2)
			.build());

	private RateLimitRetry() {
	}

	static <T> T call(Supplier<T> action) {
		try {
			return RETRY_TEMPLATE.execute(action::get);
		}
		catch (RetryException e) {
			throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
		}
	}

	private static boolean shouldRetry(Throwable error) {
		boolean rateLimited = AiServiceException.isRateLimited(error);
		if (rateLimited) {
			log.warn("Gemini rate limit hit, retrying: {}", error.getMessage());
		}
		return rateLimited;
	}

}
