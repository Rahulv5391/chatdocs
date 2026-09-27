package com.company.chatdocs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One conversation. Its messages point back to it (chat_message.session_id), and the database deletes them
 * together with the session (ON DELETE CASCADE).
 */
@Entity
@Table(name = "chat_session")
public class ChatSession extends BaseEntity {

	public static final String DEFAULT_TITLE = "New chat";

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private AppUser owner;

	@Column(name = "owner_id", insertable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false)
	private String title = DEFAULT_TITLE;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ChatSession() {
	}

	public ChatSession(AppUser owner) {
		this.owner = owner;
	}

	public void rename(String title) {
		this.title = title;
	}

	/** Moves the session to the top of the list when a message is added. */
	public void touch() {
		updatedAt = Instant.now();
	}

	@PrePersist
	@PreUpdate
	protected void onUpdate() {
		updatedAt = Instant.now();
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public String getTitle() {
		return title;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
