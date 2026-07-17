package com.tsaptest.backend.chat;

import com.tsaptest.backend.chat.ChatDtos.MessageDto;
import com.tsaptest.backend.chat.ChatDtos.SendMessageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWsController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWsController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 클라이언트가 /app/conversations/{id}/send 로 보낸 메시지를 저장한 뒤,
     * - /topic/conversations/{id} : 메시지 본문 (열려 있는 스레드 화면용)
     * - /topic/advisor            : 대화방 요약 (상담사 목록 갱신용 — 목록에 없던
     *                               새 대화방도 이걸로 실시간 추가된다)
     */
    @MessageMapping("/conversations/{id}/send")
    public void send(@DestinationVariable Long id, SendMessageRequest request, Principal principal) {
        if (!(principal instanceof Authentication auth)) {
            return;
        }
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty() || content.length() > 4000) {
            return;
        }
        Long userId = Long.valueOf(auth.getName());
        boolean advisor = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADVISOR".equals(a.getAuthority()));
        if (!chatService.canAccess(id, userId, advisor)) {
            return;
        }
        MessageDto dto = chatService.recordMessage(id, userId, content);
        messagingTemplate.convertAndSend("/topic/conversations/" + id, dto);
        messagingTemplate.convertAndSend("/topic/advisor", chatService.getConversationSummary(id));
    }
}
