package com.tsaptest.backend.chat;

import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import com.tsaptest.backend.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 채팅 인가의 핵심 규칙(canAccess): 고객은 자기 대화방만, 상담사는 전부.
 * WS SUBSCRIBE 도청 차단(StompAuthChannelInterceptor)이 이 메서드에 의존한다.
 */
class ChatServiceTest {

    private ChatConversationRepository conversationRepository;
    private ChatMessageRepository messageRepository;
    private UserRepository userRepository;
    private ChatService service;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ChatConversationRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ChatService(conversationRepository, messageRepository, userRepository);
    }

    private ChatConversation conversation(long id) {
        ChatConversation c = mock(ChatConversation.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    // ---- canAccess: 인가 규칙 ----

    @Test
    void clientCanAccessOwnConversationOnly() {
        // 주의: when(...) 안에서 conversation()을 부르면 중첩 스터빙으로 깨진다
        ChatConversation own = conversation(10L);
        when(conversationRepository.findByClientId(1L)).thenReturn(Optional.of(own));

        assertThat(service.canAccess(10L, 1L, false)).isTrue();
        // 남의 대화방 id로는 거부 — 토픽 구독 도청 차단의 근거
        assertThat(service.canAccess(99L, 1L, false)).isFalse();
    }

    @Test
    void clientWithoutConversationCannotAccessAnything() {
        when(conversationRepository.findByClientId(2L)).thenReturn(Optional.empty());

        assertThat(service.canAccess(10L, 2L, false)).isFalse();
    }

    @Test
    void advisorCanAccessAnyExistingConversation() {
        when(conversationRepository.existsById(10L)).thenReturn(true);
        when(conversationRepository.existsById(999L)).thenReturn(false);

        assertThat(service.canAccess(10L, 42L, true)).isTrue();
        // 존재하지 않는 방은 상담사라도 false (잘못된 구독 방지)
        assertThat(service.canAccess(999L, 42L, true)).isFalse();
    }

    // ---- 대화방 생성/조회 ----

    @Test
    void getOrCreateReturnsExistingConversation() {
        ChatConversation existing = conversation(10L);
        when(conversationRepository.findByClientId(1L)).thenReturn(Optional.of(existing));

        assertThat(service.getOrCreateConversationForClient(1L)).isSameAs(existing);
        verify(conversationRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void getOrCreateCreatesWhenAbsent() {
        User client = mock(User.class);
        when(conversationRepository.findByClientId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(client));
        when(conversationRepository.save(any(ChatConversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ChatConversation created = service.getOrCreateConversationForClient(1L);

        assertThat(created.getClient()).isSameAs(client);
        verify(conversationRepository).save(any(ChatConversation.class));
    }

    // ---- 메시지 저장 → 브로드캐스트 DTO ----

    @Test
    void recordMessageSavesAndMapsToDto() {
        ChatConversation conversation = conversation(10L);
        User sender = mock(User.class);
        when(sender.getRole()).thenReturn(UserRole.CLIENT);
        when(sender.getDisplayName()).thenReturn("Eleanor Whitfield");
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var dto = service.recordMessage(10L, 1L, "Hello");

        assertThat(dto.conversationId()).isEqualTo(10L);
        assertThat(dto.senderRole()).isEqualTo("CLIENT");
        assertThat(dto.senderName()).isEqualTo("Eleanor Whitfield");
        assertThat(dto.content()).isEqualTo("Hello");
        assertThat(dto.sentAt()).isNotNull();
    }
}
