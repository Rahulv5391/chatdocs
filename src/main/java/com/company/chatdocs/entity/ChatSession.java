package com.company.chatdocs.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One conversation. Its messages point back to it (chat_message.session_id), and the database deletes them
 * together with the session (ON DELETE CASCADE).
 * <p>
 * A chat can be limited to chosen documents (the "scope"). Deleting a document removes it from every scope
 * (ON DELETE CASCADE), and {@link #scoped} keeps an emptied scope from turning into "all documents".
 */
@Entity
@Table(name = "chat_session")
public class ChatSession extends BaseEntity {

	public static final String DEFAULT_TITLE = "New chat";

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private AppUser owner;

	/** Same column as {@link #owner}, read-only; lets queries filter by owner id without a join. */
	@Column(name = "owner_id", insertable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false)
	private String title = DEFAULT_TITLE;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(nullable = false)
	private boolean scoped;

	/** Ids of the documents this chat may use; only meaningful when {@link #scoped} is true. */
	@ElementCollection
	@CollectionTable(name = "chat_session_document", joinColumns = @JoinColumn(name = "session_id"))
	@Column(name = "document_id", nullable = false)
	private Set<UUID> documentIds = new HashSet<>();

	protected ChatSession() {
	}

	public ChatSession(AppUser owner) {
		this.owner = owner;
	}

	/** A chat limited to these documents. */
	public ChatSession(AppUser owner, Set<UUID> documentIds) {
		this.owner = owner;
		this.scoped = true;
		this.documentIds = new HashSet<>(documentIds);
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

	public String getTitle() {
		return title;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public boolean isScoped() {
		return scoped;
	}

	public Set<UUID> getDocumentIds() {
		return Set.copyOf(documentIds);
	}

}
