package com.tsaptest.backend.chat;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * STOMP 레벨 인증/인가.
 *
 * 브라우저 WebSocket은 커스텀 HTTP 헤더를 못 실으므로, HTTP 핸드셰이크가 아니라
 * STOMP CONNECT 프레임의 Authorization 헤더로 JWT를 받는다. REST와 같은
 * JwtDecoder를 재사용하고, 검증된 사용자를 세션 Principal로 심는다.
 * SUBSCRIBE 시에는 자기 대화방(또는 상담사)만 토픽을 구독할 수 있게 막는다.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";
    private static final String ADVISOR_TOPIC = "/topic/advisor";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final ChatService chatService;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder,
                                       JwtAuthenticationConverter jwtAuthenticationConverter,
                                       ChatService chatService) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new AccessDeniedException("Missing bearer token");
            }
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()));
            AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
            // 2FA 전의 pre-auth 토큰(role 없음)으로는 WS 연결 불가.
            // isEmpty() 검사로는 부족하다 — Security 7부터 모든 JWT 인증에
            // FACTOR_BEARER 권한이 자동 부여되어 절대 비지 않는다. ROLE_*를 요구해야 한다.
            boolean hasRole = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().startsWith("ROLE_"));
            if (!hasRole) {
                throw new AccessDeniedException("Full authentication required");
            }
            accessor.setUser(authentication);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !(accessor.getUser() instanceof Authentication auth)) {
            throw new AccessDeniedException("Unauthenticated subscription");
        }
        boolean advisor = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADVISOR".equals(a.getAuthority()));

        if (destination.startsWith(CONVERSATION_TOPIC_PREFIX)) {
            long conversationId;
            try {
                conversationId = Long.parseLong(destination.substring(CONVERSATION_TOPIC_PREFIX.length()));
            } catch (NumberFormatException e) {
                throw new AccessDeniedException("Invalid destination");
            }
            Long userId = Long.valueOf(auth.getName());
            if (!chatService.canAccess(conversationId, userId, advisor)) {
                throw new AccessDeniedException("Not a participant of this conversation");
            }
        } else if (ADVISOR_TOPIC.equals(destination)) {
            if (!advisor) {
                throw new AccessDeniedException("Advisor only");
            }
        } else {
            throw new AccessDeniedException("Unknown destination");
        }
    }
}
