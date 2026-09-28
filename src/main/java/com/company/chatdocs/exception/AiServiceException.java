package com.company.chatdocs.exception;

import com.company.chatdocs.service.EmbeddingThrottler;
import com.vaadin.hilla.exception.EndpointException;

/**
 * A Gemini (chat or embedding) failure, translated into a message that is safe and useful to show in the browser.
 * The raw Google error stays in the server log.
 */
public class AiServiceException extends EndpointException {

	public static final String QUOTA_MESSAGE =
			"Gemini's free-tier quota is used up for now. Please wait a minute and try again.";

	public static final String UNAVAILABLE_MESSAGE =
			"The AI service isn't responding right now. Please try again in a moment.";

	private AiServiceException(String message, Throwable cause) {
		super(message, cause);
	}

	/** Picks the friendly message for any error thrown while calling Gemini. */
	public static AiServiceException from(Throwable error) {
		if (error instanceof AiServiceException already) {
			return already;
		}
		return new AiServiceException(EmbeddingThrottler.isRateLimited(error) ? QUOTA_MESSAGE : UNAVAILABLE_MESSAGE,
				error);
	}

}
