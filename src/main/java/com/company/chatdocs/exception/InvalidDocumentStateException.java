package com.company.chatdocs.exception;

import com.vaadin.hilla.exception.EndpointException;

/** The action isn't allowed in the document's current status, e.g. reprocessing a READY document. */
public class InvalidDocumentStateException extends EndpointException {

	public InvalidDocumentStateException(String message) {
		super(message);
	}

}
