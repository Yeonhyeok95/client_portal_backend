package com.tsaptest.backend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 데모 계정 시딩. app.seed-demo-users=true(dev 프로필)일 때만 동작하며,
 * 이미 사용자가 있으면 건너뛴다. PortfolioSeeder(@Order(2))보다 먼저 실행.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        // 포털 더미 데이터의 인물(Eleanor Whitfield / Marcus Bell)과 일치시킨다
        userRepository.save(new User(
                "client@tsaptest.com",
                passwordEncoder.encode("PortalDemo!2026"),
                "Eleanor Whitfield",
                UserRole.CLIENT));
        userRepository.save(new User(
                "advisor@tsaptest.com",
                passwordEncoder.encode("AdvisorDemo!2026"),
                "Marcus Bell",
                UserRole.ADVISOR));
        log.info("Seeded demo users: client@tsaptest.com, advisor@tsaptest.com");
    }
}
