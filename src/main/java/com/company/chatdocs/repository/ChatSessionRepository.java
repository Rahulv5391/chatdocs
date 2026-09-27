package com.company.chatdocs.repository;

import com.company.chatdocs.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

	List<ChatSession> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

	Optional<ChatSession> findByIdAndOwnerId(UUID id, UUID ownerId);

}
