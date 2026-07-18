package com.tsaptest.backend.chat;

import com.tsaptest.backend.user.UserRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    Optional<ChatConversation> findByClientId(Long clientId);

    @Query("select c from ChatConversation c join fetch c.client order by c.id")
    List<ChatConversation> findAllWithClient();

    @Query("select c from ChatConversation c join fetch c.client where c.id = :id")
    Optional<ChatConversation> findByIdWithClient(Long id);
}

interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 키셋 페이지네이션: beforeId보다 오래된 메시지를 최신순으로 최대 limit개.
     * OFFSET 방식과 달리 새 메시지가 끼어들어도 페이지가 밀리지 않는다.
     * 첫 페이지는 beforeId = Long.MAX_VALUE로 호출한다.
     */
    @Query("select m from ChatMessage m join fetch m.sender "
            + "where m.conversation.id = :conversationId and m.id < :beforeId "
            + "order by m.id desc")
    List<ChatMessage> findPageBefore(Long conversationId, long beforeId, Pageable pageable);

    Optional<ChatMessage> findTopByConversationIdOrderBySentAtDescIdDesc(Long conversationId);

    /** 마커 이후에 상대방(senderRole)이 보낸 메시지 수 = 안읽음 수. */
    @Query("select count(m) from ChatMessage m "
            + "where m.conversation.id = :conversationId and m.id > :afterId "
            + "and m.sender.role = :senderRole")
    long countUnread(Long conversationId, long afterId, UserRole senderRole);

    long countByConversationId(Long conversationId);

    void deleteByConversationId(Long conversationId);

    void deleteBySenderId(Long senderId);
}
