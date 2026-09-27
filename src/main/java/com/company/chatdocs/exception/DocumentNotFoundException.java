package com.company.chatdocs.exception;

import com.vaadin.hilla.exception.EndpointException;

/**
 * The document does not exist or belongs to someone else. Both cases get the same message,
 * so users cannot probe for other users' document ids.
 */
public class DocumentNotFoundException extends EndpointException {

	public DocumentNotFoundException() {
		super("Document not found.");
	}

}
