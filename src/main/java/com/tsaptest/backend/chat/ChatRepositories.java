package com.tsaptest.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    Optional<ChatConversation> findByClientId(Long clientId);

    @Query("select c from ChatConversation c join fetch c.client order by c.id")
    List<ChatConversation> findAllWithClient();
}

interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("select m from ChatMessage m join fetch m.sender where m.conversation.id = :conversationId order by m.sentAt, m.id")
    List<ChatMessage> findThread(Long conversationId);

    Optional<ChatMessage> findTopByConversationIdOrderBySentAtDescIdDesc(Long conversationId);

    long countByConversationId(Long conversationId);
}
