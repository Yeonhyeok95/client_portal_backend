package com.tsaptest.backend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 데모 계정 시딩. app.seed-demo-users=true(dev 프로필)일 때만 동작하며,
 * 이미 사용자가 있으면 건너뛴다.
 */
@Component
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
        userRepository.save(new User(
                "client@tsaptest.com",
                passwordEncoder.encode("PortalDemo!2026"),
                "Alexandra Reeve",
                UserRole.CLIENT));
        userRepository.save(new User(
                "advisor@tsaptest.com",
                passwordEncoder.encode("AdvisorDemo!2026"),
                "TSAPtest Advisory",
                UserRole.ADVISOR));
        log.info("Seeded demo users: client@tsaptest.com, advisor@tsaptest.com");
    }
}
