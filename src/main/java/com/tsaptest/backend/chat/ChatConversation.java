package com.tsaptest.backend.chat;

import com.tsaptest.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 고객 1명당 1개의 대화방 (고객 ↔ 상담 데스크).
 */
@Entity
@Table(name = "chat_conversations")
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", unique = true)
    private User client;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ChatConversation() {
    }

    public ChatConversation(User client) {
        this.client = client;
    }

    public Long getId() {
        return id;
    }

    public User getClient() {
        return client;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
