package com.tsaptest.backend.chat;

import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import com.tsaptest.backend.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private ChatMessage savedMessage(long id, ChatConversation conversation, User sender, String content) {
        ChatMessage m = mock(ChatMessage.class);
        when(m.getId()).thenReturn(id);
        when(m.getConversation()).thenReturn(conversation);
        when(m.getSender()).thenReturn(sender);
        when(m.getContent()).thenReturn(content);
        when(m.getSentAt()).thenReturn(Instant.now());
        return m;
    }

    @Test
    void recordMessageSavesAndMapsToDto() {
        ChatConversation conversation = conversation(10L);
        User sender = mock(User.class);
        when(sender.getRole()).thenReturn(UserRole.CLIENT);
        when(sender.getDisplayName()).thenReturn("Eleanor Whitfield");
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(inv -> savedMessage(77L, conversation, sender, "Hello"));

        var dto = service.recordMessage(10L, 1L, "Hello");

        assertThat(dto.conversationId()).isEqualTo(10L);
        assertThat(dto.senderRole()).isEqualTo("CLIENT");
        assertThat(dto.senderName()).isEqualTo("Eleanor Whitfield");
        assertThat(dto.content()).isEqualTo("Hello");
        assertThat(dto.sentAt()).isNotNull();
        // 자기가 보낸 메시지는 자기에게는 읽은 것 — 보낸 쪽(CLIENT) 마커가 전진해야 한다
        verify(conversation).markReadUpTo(77L, false);
    }

    // ---- 읽음 마커 / 안읽음 수 ----

    @Test
    void readMarkerOnlyMovesForward() {
        ChatConversation c = new ChatConversation(mock(User.class));

        c.markReadUpTo(7L, false);
        c.markReadUpTo(3L, false); // 과거로는 되돌아가지 않는다

        assertThat(c.getClientLastReadMessageId()).isEqualTo(7L);
        assertThat(c.getAdvisorLastReadMessageId()).isZero(); // 상대 마커는 그대로
    }

    @Test
    void markReadAdvancesCallersMarkerToLatestMessage() {
        ChatConversation c = new ChatConversation(mock(User.class));
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(c));
        ChatMessage last = mock(ChatMessage.class);
        when(last.getId()).thenReturn(42L);
        when(messageRepository.findTopByConversationIdOrderBySentAtDescIdDesc(10L))
                .thenReturn(Optional.of(last));

        service.markRead(10L, true);

        assertThat(c.getAdvisorLastReadMessageId()).isEqualTo(42L);
        assertThat(c.getClientLastReadMessageId()).isZero();
    }

    @Test
    void unreadForClientCountsAdvisorMessagesAfterMarker() {
        ChatConversation c = mock(ChatConversation.class);
        when(c.getId()).thenReturn(10L);
        when(c.getClientLastReadMessageId()).thenReturn(5L);
        when(conversationRepository.findByClientId(1L)).thenReturn(Optional.of(c));
        when(messageRepository.countUnread(10L, 5L, UserRole.ADVISOR)).thenReturn(3L);

        assertThat(service.unreadCountForClient(1L)).isEqualTo(3L);
    }

    @Test
    void unreadForClientWithoutConversationIsZero() {
        when(conversationRepository.findByClientId(2L)).thenReturn(Optional.empty());

        assertThat(service.unreadCountForClient(2L)).isZero();
    }

    // ---- 페이지네이션 ----

    @Test
    void threadPageTrimsToPageSizeAndReportsHasMore() {
        ChatConversation conversation = conversation(10L);
        User sender = mock(User.class);
        when(sender.getRole()).thenReturn(UserRole.CLIENT);
        when(sender.getDisplayName()).thenReturn("Eleanor Whitfield");
        // 저장소는 최신순(desc)으로 PAGE_SIZE+1개를 돌려준다 → 한 개 남으면 hasMore
        List<ChatMessage> newestFirst = new ArrayList<>();
        for (long id = ChatService.PAGE_SIZE + 1; id >= 1; id--) {
            newestFirst.add(savedMessage(id, conversation, sender, "m" + id));
        }
        when(messageRepository.findPageBefore(eq(10L), eq(Long.MAX_VALUE), any()))
                .thenReturn(newestFirst);

        var page = service.getThreadPage(10L, null);

        assertThat(page.hasMore()).isTrue();
        assertThat(page.messages()).hasSize(ChatService.PAGE_SIZE);
        // 화면 순서는 오래된 → 최신. 잘려나가는 건 가장 오래된 1건(id=1)이어야 한다
        assertThat(page.messages().getFirst().id()).isEqualTo(2L);
        assertThat(page.messages().getLast().id()).isEqualTo(ChatService.PAGE_SIZE + 1L);
    }

    @Test
    void threadPageWithCursorPassesItToRepository() {
        when(messageRepository.findPageBefore(eq(10L), eq(50L), any()))
                .thenReturn(List.of());

        var page = service.getThreadPage(10L, 50L);

        assertThat(page.messages()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }
}
