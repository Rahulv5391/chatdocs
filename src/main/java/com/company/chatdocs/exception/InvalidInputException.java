package com.company.chatdocs.exception;

import com.vaadin.hilla.exception.EndpointException;

/** Input the user can fix, such as an empty chat title or message. The message is shown in the browser. */
public class InvalidInputException extends EndpointException {

	public InvalidInputException(String message) {
		super(message);
	}

}
