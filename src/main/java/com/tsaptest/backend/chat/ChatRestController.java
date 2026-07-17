package com.tsaptest.backend.chat;

import com.tsaptest.backend.chat.ChatDtos.ConversationSummaryDto;
import com.tsaptest.backend.chat.ChatDtos.MessageDto;
import com.tsaptest.backend.chat.ChatDtos.ThreadPageDto;
import com.tsaptest.backend.chat.ChatDtos.UnreadDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatRestController {

    public record MyConversationResponse(
            Long conversationId, List<MessageDto> messages, boolean hasMore) {
    }

    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 고객 본인의 대화방 ID + 최신 페이지. 페이지 진입 시 1회 호출. */
    @GetMapping("/conversation")
    @PreAuthorize("hasRole('CLIENT')")
    public MyConversationResponse myConversation(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        ChatConversation conversation = chatService.getOrCreateConversationForClient(userId);
        ThreadPageDto page = chatService.getThreadPage(conversation.getId(), null);
        return new MyConversationResponse(
                conversation.getId(), page.messages(), page.hasMore());
    }

    /** 고객 네브 배지: 본인 대화방의 안읽음 수 (가벼운 폴링/초기 로드용). */
    @GetMapping("/unread")
    @PreAuthorize("hasRole('CLIENT')")
    public UnreadDto unread(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        ChatConversation conversation = chatService.getOrCreateConversationForClient(userId);
        return new UnreadDto(
                conversation.getId(), chatService.unreadCountForClient(userId));
    }

    /** 상담사: 전체 대화방 목록 (상담사 관점 unreadCount 포함). */
    @GetMapping("/conversations")
    @PreAuthorize("hasRole('ADVISOR')")
    public List<ConversationSummaryDto> conversations() {
        return chatService.listConversations();
    }

    /**
     * 상담사(또는 대화방 소유 고객): 과거 기록 한 페이지.
     * before = 이 메시지 ID보다 오래된 것들 (생략 시 최신 페이지).
     */
    @GetMapping("/conversations/{id}/messages")
    public ThreadPageDto thread(@PathVariable Long id,
                                @RequestParam(required = false) Long before,
                                @AuthenticationPrincipal Jwt jwt) {
        requireAccess(id, jwt);
        return chatService.getThreadPage(id, before);
    }

    /** 지금 최신 메시지까지 읽음 처리 — 호출자 역할의 마커만 전진. */
    @PostMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        boolean isAdvisor = requireAccess(id, jwt);
        chatService.markRead(id, isAdvisor);
    }

    /** 접근 불가면 403. 반환값 = 상담사 여부. */
    private boolean requireAccess(Long conversationId, Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        boolean isAdvisor = "ADVISOR".equals(jwt.getClaimAsString("role"));
        if (!chatService.canAccess(conversationId, userId, isAdvisor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return isAdvisor;
    }
}
