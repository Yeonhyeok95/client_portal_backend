package com.tsaptest.backend.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tsaptest.backend.email.ResendEmailSender;
import com.tsaptest.backend.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 이메일 OTP 방식 2단계 인증.
 *
 * 코드는 평문 대신 BCrypt 해시로 Caffeine 캐시에 5분간만 보관한다.
 * 단일 인스턴스 배포(Railway)라 인메모리로 충분하며, 수평 확장 시에는
 * Redis 같은 공유 저장소로 옮기는 것이 다음 단계다.
 */
@Service
public class TwoFactorService {

    public enum VerifyResult { OK, WRONG_CODE, EXPIRED_OR_MISSING, TOO_MANY_ATTEMPTS }

    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);
    private static final int MAX_ATTEMPTS = 5;

    private record PendingCode(String codeHash, AtomicInteger attempts) {
    }

    private final Cache<Long, PendingCode> codes = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    private final SecureRandom random = new SecureRandom();
    private final ResendEmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final String overrideEmail;
    private final boolean logCodes;

    public TwoFactorService(ResendEmailSender emailSender,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.twofa.override-email:}") String overrideEmail,
                            @Value("${app.twofa.log-codes:false}") boolean logCodes) {
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.overrideEmail = overrideEmail;
        this.logCodes = logCodes;
    }

    /** 새 코드를 만들어 저장하고 이메일 발송을 시작한다 (재요청 시 기존 코드 대체). */
    public void issueCode(User user) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.put(user.getId(), new PendingCode(passwordEncoder.encode(code), new AtomicInteger()));
        if (logCodes) {
            // dev 전용 편의 기능 — 운영에서 켜면 OTP가 로그에 남으므로 금지
            log.warn("[dev] 2FA code for {}: {}", user.getEmail(), code);
        }
        sendCodeEmail(user, code);
    }

    @Async
    void sendCodeEmail(User user, String code) {
        // Resend 도메인 인증 전에는 가입 계정으로만 발송 가능 → override로 우회
        String to = overrideEmail.isBlank() ? user.getEmail() : overrideEmail;
        String body = """
                Your TSAPtest sign-in code is %s

                It expires in 5 minutes. If you did not try to sign in,
                you can safely ignore this email.

                (Sign-in attempt for %s)
                """.formatted(code, user.getEmail());
        try {
            emailSender.send(to, null, "Your TSAPtest verification code", body);
        } catch (Exception e) {
            log.error("Failed to send 2FA code email", e);
        }
    }

    public VerifyResult verify(Long userId, String code) {
        PendingCode pending = codes.getIfPresent(userId);
        if (pending == null) {
            return VerifyResult.EXPIRED_OR_MISSING;
        }
        if (pending.attempts().incrementAndGet() > MAX_ATTEMPTS) {
            codes.invalidate(userId);
            return VerifyResult.TOO_MANY_ATTEMPTS;
        }
        if (!passwordEncoder.matches(code, pending.codeHash())) {
            return VerifyResult.WRONG_CODE;
        }
        codes.invalidate(userId);
        return VerifyResult.OK;
    }
}
