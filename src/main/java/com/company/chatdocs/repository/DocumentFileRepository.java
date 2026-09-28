package com.company.chatdocs.repository;

import com.company.chatdocs.entity.DocumentFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Original files by document id. Callers check that the document belongs to the user first. */
public interface DocumentFileRepository extends JpaRepository<DocumentFile, UUID> {
}
