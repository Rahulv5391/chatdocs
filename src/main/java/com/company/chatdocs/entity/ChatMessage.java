package com.company.chatdocs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One message in a chat session: the many side of session 1──* message. */
@Entity
@Table(name = "chat_message")
public class ChatMessage extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false, updatable = false)
	private ChatSession session;

	/** Filled by the database (identity column); used only for ordering. */
	@Column(insertable = false, updatable = false)
	private Long seq;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MessageRole role;

	@Column(nullable = false)
	private String content;

	protected ChatMessage() {
	}

	public ChatMessage(ChatSession session, MessageRole role, String content) {
		this.session = session;
		this.role = role;
		this.content = content;
	}

	public MessageRole getRole() {
		return role;
	}

	public String getContent() {
		return content;
	}

}
