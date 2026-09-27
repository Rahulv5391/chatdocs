package com.company.chatdocs.exception;

import com.vaadin.hilla.exception.EndpointException;

/** The chat doesn't exist or belongs to someone else; both cases get the same message. */
public class ChatSessionNotFoundException extends EndpointException {

	public ChatSessionNotFoundException() {
		super("Chat not found.");
	}

}
