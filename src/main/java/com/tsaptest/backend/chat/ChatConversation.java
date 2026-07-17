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

    /** 고객이 읽은 마지막 메시지 ID (0 = 아무것도 안 읽음). */
    @Column(nullable = false)
    private long clientLastReadMessageId;

    /** 상담 데스크가 읽은 마지막 메시지 ID — 상담사들이 공유하는 값. */
    @Column(nullable = false)
    private long advisorLastReadMessageId;

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

    public long getClientLastReadMessageId() {
        return clientLastReadMessageId;
    }

    public long getAdvisorLastReadMessageId() {
        return advisorLastReadMessageId;
    }

    /** 마커는 앞으로만 이동한다 — 옛 페이지를 다시 열어도 읽음 상태가 되돌아가지 않도록. */
    public void markReadUpTo(long messageId, boolean advisor) {
        if (advisor) {
            advisorLastReadMessageId = Math.max(advisorLastReadMessageId, messageId);
        } else {
            clientLastReadMessageId = Math.max(clientLastReadMessageId, messageId);
        }
    }
}
