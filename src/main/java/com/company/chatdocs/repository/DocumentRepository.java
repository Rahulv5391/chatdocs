package com.company.chatdocs.repository;

import com.company.chatdocs.entity.Document;
import com.company.chatdocs.entity.DocumentStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

	List<Document> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

	boolean existsByOwnerIdAndChecksumSha256(UUID ownerId, String checksumSha256);

	Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

	/**
	 * Updates only the processing fields. Unlike save(), this never re-creates a row that was deleted
	 * while ingestion was still running; it just updates 0 rows.
	 */
	@Transactional
	@Modifying
	@Query("""
			update Document d
			set d.status = :status, d.chunkCount = :chunkCount, d.errorMessage = :errorMessage, d.updatedAt = :now
			where d.id = :id""")
	int updateStatus(UUID id, DocumentStatus status, int chunkCount, @Nullable String errorMessage, Instant now);

}
