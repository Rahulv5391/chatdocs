package com.company.chatdocs.service;

/** An ingestion failure with a message that is safe and useful to show to the user. */
class IngestionException extends RuntimeException {

	IngestionException(String message) {
		super(message);
	}

	IngestionException(String message, Throwable cause) {
		super(message, cause);
	}

}
