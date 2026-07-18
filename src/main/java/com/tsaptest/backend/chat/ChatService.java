package com.tsaptest.backend.chat;

import com.tsaptest.backend.chat.ChatDtos.ConversationSummaryDto;
import com.tsaptest.backend.chat.ChatDtos.MessageDto;
import com.tsaptest.backend.chat.ChatDtos.ThreadPageDto;
import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import com.tsaptest.backend.user.UserRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatService {

    /** 한 번에 내려주는 메시지 수 — 프론트 "Load earlier messages" 단위. */
    public static final int PAGE_SIZE = 30;

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    public ChatService(ChatConversationRepository conversationRepository,
                       ChatMessageRepository messageRepository,
                       UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /** 고객 본인의 대화방을 반환하고, 없으면 만든다. */
    @Transactional
    public ChatConversation getOrCreateConversationForClient(Long clientId) {
        return conversationRepository.findByClientId(clientId)
                .orElseGet(() -> {
                    User client = userRepository.findById(clientId).orElseThrow();
                    return conversationRepository.save(new ChatConversation(client));
                });
    }

    /**
     * 메시지 한 페이지. beforeId가 null이면 최신 페이지부터.
     * limit+1개를 조회해서 한 개가 남으면 hasMore=true — count 쿼리 없이 다음 페이지 유무를 안다.
     */
    @Transactional(readOnly = true)
    public ThreadPageDto getThreadPage(Long conversationId, Long beforeId) {
        long cursor = beforeId != null ? beforeId : Long.MAX_VALUE;
        List<ChatMessage> newestFirst = messageRepository.findPageBefore(
                conversationId, cursor, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasMore = newestFirst.size() > PAGE_SIZE;
        List<MessageDto> page = new ArrayList<>(
                newestFirst.stream().limit(PAGE_SIZE).map(ChatService::toDto).toList());
        Collections.reverse(page); // 화면은 오래된 → 최신 순
        return new ThreadPageDto(List.copyOf(page), hasMore);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryDto> listConversations() {
        return conversationRepository.findAllWithClient().stream()
                .map(this::toSummary)
                .toList();
    }

    /** WS 브로드캐스트용 단건 요약 (상담사 관점의 unreadCount 포함). */
    @Transactional(readOnly = true)
    public ConversationSummaryDto getConversationSummary(Long conversationId) {
        return toSummary(conversationRepository.findByIdWithClient(conversationId).orElseThrow());
    }

    /** 고객 네브 배지: 본인 대화방의 안읽음 수 (대화방이 없으면 0). */
    @Transactional(readOnly = true)
    public long unreadCountForClient(Long clientId) {
        return conversationRepository.findByClientId(clientId)
                .map(c -> messageRepository.countUnread(
                        c.getId(), c.getClientLastReadMessageId(), UserRole.ADVISOR))
                .orElse(0L);
    }

    /** 현재 최신 메시지까지 읽음 처리. 호출자의 역할에 따라 해당 마커만 전진한다. */
    @Transactional
    public void markRead(Long conversationId, boolean advisor) {
        ChatConversation conversation =
                conversationRepository.findById(conversationId).orElseThrow();
        messageRepository.findTopByConversationIdOrderBySentAtDescIdDesc(conversationId)
                .ifPresent(last -> conversation.markReadUpTo(last.getId(), advisor));
        // JPA dirty checking이 트랜잭션 커밋 시 UPDATE를 만든다 — save 불필요
    }

    /** 메시지를 저장하고 브로드캐스트용 DTO를 돌려준다. */
    @Transactional
    public MessageDto recordMessage(Long conversationId, Long senderId, String content) {
        ChatConversation conversation = conversationRepository.findById(conversationId).orElseThrow();
        User sender = userRepository.findById(senderId).orElseThrow();
        ChatMessage saved = messageRepository.save(new ChatMessage(conversation, sender, content));
        // 본인이 보낸 메시지는 본인에게는 읽은 것 — 마커를 같이 전진시킨다
        conversation.markReadUpTo(saved.getId(), sender.getRole() == UserRole.ADVISOR);
        return toDto(saved);
    }

    /** 이 사용자가 해당 대화방에 접근할 수 있는가 (본인 대화방이거나 상담사). */
    @Transactional(readOnly = true)
    public boolean canAccess(Long conversationId, Long userId, boolean isAdvisor) {
        if (isAdvisor) {
            return conversationRepository.existsById(conversationId);
        }
        return conversationRepository.findByClientId(userId)
                .map(c -> c.getId().equals(conversationId))
                .orElse(false);
    }

    /**
     * 계정 완전삭제(관리자) 시 이 사용자의 채팅 흔적 전부 제거.
     * 고객이면 본인 대화방과 그 안의 메시지 전체(상담사 발신 포함)를,
     * 상담사면 다른 고객 대화방에 남긴 본인 발신 메시지를 지운다 (FK 충족).
     */
    @Transactional
    public void deleteDataForUser(Long userId) {
        conversationRepository.findByClientId(userId).ifPresent(conversation -> {
            messageRepository.deleteByConversationId(conversation.getId());
            conversationRepository.delete(conversation);
        });
        messageRepository.deleteBySenderId(userId);
    }

    private ConversationSummaryDto toSummary(ChatConversation c) {
        ChatMessage last = messageRepository
                .findTopByConversationIdOrderBySentAtDescIdDesc(c.getId())
                .orElse(null);
        long unread = messageRepository.countUnread(
                c.getId(), c.getAdvisorLastReadMessageId(), UserRole.CLIENT);
        return new ConversationSummaryDto(
                c.getId(),
                c.getClient().getDisplayName(),
                c.getClient().getEmail(),
                last != null ? last.getContent() : "",
                last != null ? last.getSentAt() : c.getCreatedAt(),
                unread);
    }

    private static MessageDto toDto(ChatMessage m) {
        UserRole role = m.getSender().getRole();
        return new MessageDto(
                m.getId(),
                m.getConversation().getId(),
                role.name(),
                m.getSender().getDisplayName(),
                m.getContent(),
                m.getSentAt());
    }
}
