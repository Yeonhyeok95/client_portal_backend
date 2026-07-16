package com.tsaptest.backend.chat;

import com.tsaptest.backend.testutil.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WS 보안 규칙: CONNECT는 정식 토큰만 (pre-auth 거부),
 * SUBSCRIBE는 본인 대화방 또는 상담사만 (도청 차단).
 */
class StompAuthChannelInterceptorTest {

    private ChatService chatService;
    private StompAuthChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        interceptor = new StompAuthChannelInterceptor(
                JwtTestSupport.decoder(), JwtTestSupport.converter(), chatService);
    }

    private Message<byte[]> connect(String authHeaderValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeaderValue != null) {
            accessor.addNativeHeader("Authorization", authHeaderValue);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribe(String token, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (token != null) {
            Authentication auth = JwtTestSupport.converter()
                    .convert(JwtTestSupport.decoder().decode(token));
            accessor.setUser(auth);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // ---- CONNECT ----

    @Test
    void connectWithoutTokenIsDenied() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect("Basic abc"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithPreAuthTokenIsDenied() {
        // 2FA 전의 pre-auth 토큰(role 없음)으로는 WS 연결 불가
        String preAuth = JwtTestSupport.preAuthToken(1L);

        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer " + preAuth), channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Full authentication required");
    }

    @Test
    void connectWithExpiredTokenIsDenied() {
        String expired = JwtTestSupport.expiredToken(1L);

        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer " + expired), channel))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void connectWithFullTokenSetsSessionUser() {
        String token = JwtTestSupport.roleToken(1L, "CLIENT");
        Message<byte[]> message = connect("Bearer " + token);

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(result != null ? result : message);
        assertThat(accessor.getUser()).isInstanceOf(Authentication.class);
        assertThat(((Authentication) accessor.getUser()).getName()).isEqualTo("1");
    }

    // ---- SUBSCRIBE ----

    @Test
    void clientCanSubscribeOwnConversationTopic() {
        when(chatService.canAccess(10L, 1L, false)).thenReturn(true);
        String token = JwtTestSupport.roleToken(1L, "CLIENT");

        interceptor.preSend(subscribe(token, "/topic/conversations/10"), channel);
        // 예외 없이 통과하면 성공
    }

    @Test
    void clientCannotSubscribeOthersConversationTopic() {
        when(chatService.canAccess(99L, 1L, false)).thenReturn(false);
        String token = JwtTestSupport.roleToken(1L, "CLIENT");

        assertThatThrownBy(() ->
                interceptor.preSend(subscribe(token, "/topic/conversations/99"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void advisorTopicIsAdvisorOnly() {
        String client = JwtTestSupport.roleToken(1L, "CLIENT");
        String advisor = JwtTestSupport.roleToken(2L, "ADVISOR");

        assertThatThrownBy(() ->
                interceptor.preSend(subscribe(client, "/topic/advisor"), channel))
                .isInstanceOf(AccessDeniedException.class);

        interceptor.preSend(subscribe(advisor, "/topic/advisor"), channel); // 통과
    }

    @Test
    void unknownOrMalformedDestinationsAreDenied() {
        String token = JwtTestSupport.roleToken(1L, "CLIENT");

        assertThatThrownBy(() ->
                interceptor.preSend(subscribe(token, "/topic/everything"), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                interceptor.preSend(subscribe(token, "/topic/conversations/not-a-number"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeWithoutAuthenticatedUserIsDenied() {
        assertThatThrownBy(() ->
                interceptor.preSend(subscribe(null, "/topic/conversations/10"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }
}
