package com.tsaptest.backend.chat;

import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 데모 대화 시딩 — frontend/lib/portalData.ts의 initialMessages 이관.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true")
public class ChatSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatSeeder.class);

    private final UserRepository userRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    public ChatSeeder(UserRepository userRepository,
                      ChatConversationRepository conversationRepository,
                      ChatMessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User client = userRepository.findByEmailIgnoreCase("client@tsaptest.com").orElse(null);
        User advisor = userRepository.findByEmailIgnoreCase("advisor@tsaptest.com").orElse(null);
        if (client == null || advisor == null) {
            return;
        }
        if (conversationRepository.findByClientId(client.getId()).isPresent()) {
            return;
        }

        ChatConversation conversation = conversationRepository.save(new ChatConversation(client));
        messageRepository.saveAll(List.of(
                new ChatMessage(conversation, advisor,
                        "Good morning Eleanor — the Q2 statements are posted under Documents. "
                                + "Nothing requires action, but do note the municipal ladder "
                                + "reinvestment summarized on page 4.",
                        Instant.parse("2026-07-02T13:14:00Z")),
                new ChatMessage(conversation, client,
                        "Thank you, Marcus. Could we revisit the 529 funding for the "
                                + "grandchildren before September?",
                        Instant.parse("2026-07-03T12:02:00Z")),
                new ChatMessage(conversation, advisor,
                        "Of course. I will prepare projections for each of the three plans "
                                + "and propose some times for next week.",
                        Instant.parse("2026-07-03T14:41:00Z"))));
        log.info("Seeded demo conversation for {}", client.getEmail());
    }
}
