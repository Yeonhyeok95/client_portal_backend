package com.tsaptest.backend.chat;

import java.time.Instant;

public final class ChatDtos {

    private ChatDtos() {
    }

    /** 채팅 메시지 한 건 — REST 기록 조회와 WS 브로드캐스트가 같은 형태를 쓴다. */
    public record MessageDto(
            Long id,
            Long conversationId,
            String senderRole,
            String senderName,
            String content,
            Instant sentAt) {
    }

    /** 상담사 화면의 대화방 목록 항목. */
    public record ConversationSummaryDto(
            Long id,
            String clientName,
            String clientEmail,
            String lastMessage,
            Instant lastMessageAt) {
    }

    /** WS로 들어오는 발신 페이로드. */
    public record SendMessageRequest(String content) {
    }
}
