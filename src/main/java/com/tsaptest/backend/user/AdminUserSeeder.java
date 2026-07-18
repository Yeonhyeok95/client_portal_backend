package com.tsaptest.backend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 관리자 계정 시딩. ADMIN은 관리자 API로 생성/삭제할 수 없으므로(잠금 사고 방지)
 * env(ADMIN_ACCOUNT_EMAIL/PASSWORD) 기반의 이 시더가 유일한 생성 경로다.
 * 모든 프로필에서 동작하며 env 미설정 시 no-op.
 * DemoUserSeeder(@Order 1)의 "사용자 0명일 때만 시딩" 검사를 깨지 않도록 마지막(@Order 4)에 실행.
 */
@Component
@Order(4)
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminUserSeeder(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin-account.email:}") String email,
                           @Value("${app.admin-account.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        userRepository.save(new User(
                email.trim().toLowerCase(),
                passwordEncoder.encode(password),
                "IT Administrator",
                UserRole.ADMIN));
        log.info("Seeded admin account: {}", email);
    }
}
