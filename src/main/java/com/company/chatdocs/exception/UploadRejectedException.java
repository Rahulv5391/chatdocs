package com.company.chatdocs.exception;

import com.vaadin.hilla.exception.EndpointException;

/**
 * An upload the user can fix (wrong type, too big, duplicate). Hilla sends the message to the browser as-is,
 * so it must be friendly and must not leak internal details.
 */
public class UploadRejectedException extends EndpointException {

	public UploadRejectedException(String message) {
		super(message);
	}

}
