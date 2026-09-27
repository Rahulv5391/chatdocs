package com.company.chatdocs.event;

import java.util.UUID;

/** Published when a new document row is saved. Ingestion starts once the upload transaction commits. */
public record DocumentUploadedEvent(UUID documentId) {
}
