package com.company.chatdocs.repository;

import com.company.chatdocs.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

	List<ChatMessage> findBySessionIdOrderBySeqAsc(UUID sessionId);

}
