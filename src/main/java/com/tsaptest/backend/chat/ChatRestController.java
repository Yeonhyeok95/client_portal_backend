package com.tsaptest.backend.chat;

import com.tsaptest.backend.chat.ChatDtos.ConversationSummaryDto;
import com.tsaptest.backend.chat.ChatDtos.MessageDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatRestController {

    public record MyConversationResponse(Long conversationId, List<MessageDto> messages) {
    }

    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 고객 본인의 대화방 ID + 과거 기록. 페이지 진입 시 1회 호출. */
    @GetMapping("/conversation")
    @PreAuthorize("hasRole('CLIENT')")
    public MyConversationResponse myConversation(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        ChatConversation conversation = chatService.getOrCreateConversationForClient(userId);
        return new MyConversationResponse(
                conversation.getId(),
                chatService.getThread(conversation.getId()));
    }

    /** 상담사: 전체 대화방 목록. */
    @GetMapping("/conversations")
    @PreAuthorize("hasRole('ADVISOR')")
    public List<ConversationSummaryDto> conversations() {
        return chatService.listConversations();
    }

    /** 상담사(또는 대화방 소유 고객): 특정 대화방의 과거 기록. */
    @GetMapping("/conversations/{id}/messages")
    public List<MessageDto> thread(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        boolean isAdvisor = "ADVISOR".equals(jwt.getClaimAsString("role"));
        if (!chatService.canAccess(id, userId, isAdvisor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return chatService.getThread(id);
    }
}
