package com.tsaptest.backend.admin;

import com.tsaptest.backend.chat.ChatService;
import com.tsaptest.backend.portfolio.PortfolioService;
import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import com.tsaptest.backend.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

/**
 * IT 관리자용 계정 관리.
 *
 * 공통 가드: 자기 자신과 ADMIN 계정은 이 API로 건드릴 수 없다.
 * ADMIN 계정은 시더/DB로만 관리 — "마지막 관리자를 지워서 잠기는" 사고를 원천 차단한다.
 */
@Service
public class AdminUserService {

    public record AdminUserDto(
            Long id, String email, String displayName, String role,
            boolean enabled, Instant createdAt) {

        static AdminUserDto from(User user) {
            return new AdminUserDto(
                    user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getRole().name(), user.isEnabled(), user.getCreatedAt());
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    /** 임시 비밀번호 문자셋 — 헷갈리는 문자(0/O, 1/l/I) 제외 */
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PortfolioService portfolioService;
    private final ChatService chatService;
    private final TempPasswordMailer tempPasswordMailer;
    private final SecureRandom random = new SecureRandom();

    public AdminUserService(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            PortfolioService portfolioService,
                            ChatService chatService,
                            TempPasswordMailer tempPasswordMailer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.portfolioService = portfolioService;
        this.chatService = chatService;
        this.tempPasswordMailer = tempPasswordMailer;
    }

    public List<AdminUserDto> listUsers() {
        return userRepository.findAll(Sort.by("id")).stream()
                .map(AdminUserDto::from)
                .toList();
    }

    public AdminUserDto createUser(String email, String displayName, UserRole role,
                                   String initialPassword) {
        if (role == UserRole.ADMIN) {
            throw new AdminOperationException(HttpStatus.BAD_REQUEST,
                    "Admin accounts cannot be created via this API.");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new AdminOperationException(HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        }
        User saved = userRepository.save(new User(
                normalizedEmail,
                passwordEncoder.encode(initialPassword),
                displayName.trim(),
                role));
        log.info("Admin created user {} ({})", saved.getEmail(), role);
        return AdminUserDto.from(saved);
    }

    public AdminUserDto setEnabled(Long actorId, Long targetId, boolean enabled) {
        User target = guardedTarget(actorId, targetId);
        target.setEnabled(enabled);
        User saved = userRepository.save(target);
        log.info("Admin {} user {}", enabled ? "enabled" : "disabled", saved.getEmail());
        return AdminUserDto.from(saved);
    }

    /** 임시 비밀번호를 생성해 저장하고 사용자 이메일로 발송한다. 응답에는 싣지 않는다. */
    public void resetPassword(Long actorId, Long targetId) {
        User target = guardedTarget(actorId, targetId);
        String tempPassword = generateTempPassword();
        target.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(target);
        tempPasswordMailer.sendTempPassword(
                target.getEmail(), target.getDisplayName(), tempPassword);
        log.info("Admin reset password for user {}", target.getEmail());
    }

    /** 완전삭제 — FK 역순으로 연관 데이터를 명시적으로 지운 뒤 사용자 삭제. */
    @Transactional
    public void deleteUser(Long actorId, Long targetId) {
        User target = guardedTarget(actorId, targetId);
        portfolioService.deleteDataForUser(targetId);
        chatService.deleteDataForUser(targetId);
        userRepository.delete(target);
        log.info("Admin deleted user {} and all related data", target.getEmail());
    }

    private User guardedTarget(Long actorId, Long targetId) {
        if (targetId.equals(actorId)) {
            throw new AdminOperationException(HttpStatus.BAD_REQUEST,
                    "You cannot modify your own account.");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new AdminOperationException(HttpStatus.NOT_FOUND,
                        "User not found."));
        if (target.getRole() == UserRole.ADMIN) {
            throw new AdminOperationException(HttpStatus.BAD_REQUEST,
                    "Admin accounts cannot be modified via this API.");
        }
        return target;
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
