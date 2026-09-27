package com.company.chatdocs.repository;

import com.company.chatdocs.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

	List<Document> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

	boolean existsByOwnerIdAndChecksumSha256(UUID ownerId, String checksumSha256);

}
