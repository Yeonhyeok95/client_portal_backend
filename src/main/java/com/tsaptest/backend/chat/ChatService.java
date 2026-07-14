package com.tsaptest.backend.chat;

import com.tsaptest.backend.chat.ChatDtos.ConversationSummaryDto;
import com.tsaptest.backend.chat.ChatDtos.MessageDto;
import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import com.tsaptest.backend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatService {

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

    @Transactional(readOnly = true)
    public List<MessageDto> getThread(Long conversationId) {
        return messageRepository.findThread(conversationId).stream()
                .map(ChatService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryDto> listConversations() {
        return conversationRepository.findAllWithClient().stream()
                .map(c -> {
                    ChatMessage last = messageRepository
                            .findTopByConversationIdOrderBySentAtDescIdDesc(c.getId())
                            .orElse(null);
                    return new ConversationSummaryDto(
                            c.getId(),
                            c.getClient().getDisplayName(),
                            c.getClient().getEmail(),
                            last != null ? last.getContent() : "",
                            last != null ? last.getSentAt() : c.getCreatedAt());
                })
                .toList();
    }

    /** 메시지를 저장하고 브로드캐스트용 DTO를 돌려준다. */
    @Transactional
    public MessageDto recordMessage(Long conversationId, Long senderId, String content) {
        ChatConversation conversation = conversationRepository.findById(conversationId).orElseThrow();
        User sender = userRepository.findById(senderId).orElseThrow();
        ChatMessage saved = messageRepository.save(new ChatMessage(conversation, sender, content));
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
