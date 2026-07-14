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
     * 클라이언트가 /app/conversations/{id}/send 로 보낸 메시지를
     * 저장한 뒤 해당 대화방 토픽(양쪽 화면)과 상담사 목록 토픽에 브로드캐스트.
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
        messagingTemplate.convertAndSend("/topic/advisor", dto);
    }
}
