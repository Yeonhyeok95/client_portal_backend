package com.tsaptest.backend.chat;

import java.time.Instant;
import java.util.List;

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

    /** 메시지 한 페이지 (오래된 → 최신 순). hasMore = 더 오래된 메시지가 남아 있는가. */
    public record ThreadPageDto(
            List<MessageDto> messages,
            boolean hasMore) {
    }

    /**
     * 상담사 화면의 대화방 목록 항목.
     * REST 목록 조회와 /topic/advisor WS 브로드캐스트가 같은 형태를 쓴다 —
     * 목록에 없던 대화방이 브로드캐스트로 와도 그대로 목록에 추가할 수 있다.
     */
    public record ConversationSummaryDto(
            Long id,
            String clientName,
            String clientEmail,
            String lastMessage,
            Instant lastMessageAt,
            long unreadCount) {
    }

    /** 고객 네브 배지용 안읽음 요약. */
    public record UnreadDto(
            Long conversationId,
            long unreadCount) {
    }

    /** WS로 들어오는 발신 페이로드. */
    public record SendMessageRequest(String content) {
    }
}
