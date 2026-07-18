package com.tsaptest.backend.admin;

import com.tsaptest.backend.email.ResendEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 초기화 메일 발송. 임시 비밀번호는 응답에 싣지 않고 사용자 이메일로만 전달한다.
 * 2FA 메일과 동일하게 Resend 샌드박스 동안은 override 주소로 우회한다.
 * (별도 빈으로 분리한 이유: @Async는 프록시 경유 호출에서만 동작 — 서비스 자기호출 방지)
 */
@Component
public class TempPasswordMailer {

    private static final Logger log = LoggerFactory.getLogger(TempPasswordMailer.class);

    private final ResendEmailSender emailSender;
    private final String overrideEmail;
    private final boolean logSecrets;

    public TempPasswordMailer(ResendEmailSender emailSender,
                              @Value("${app.twofa.override-email:}") String overrideEmail,
                              @Value("${app.twofa.log-codes:false}") boolean logSecrets) {
        this.emailSender = emailSender;
        this.overrideEmail = overrideEmail;
        this.logSecrets = logSecrets;
    }

    @Async
    public void sendTempPassword(String userEmail, String displayName, String tempPassword) {
        if (logSecrets) {
            // dev 전용 편의 기능 (2FA log-codes와 동일 플래그) — 운영에서 켜면 금지
            log.warn("[dev] temp password for {}: {}", userEmail, tempPassword);
        }
        String to = overrideEmail.isBlank() ? userEmail : overrideEmail;
        String body = """
                Hello %s,

                Your TSAPtest portal password has been reset by an administrator.

                Temporary password: %s

                Please sign in with this password. If you did not request this reset,
                contact your advisor immediately.

                (Password reset for %s)
                """.formatted(displayName, tempPassword, userEmail);
        try {
            emailSender.send(to, null, "Your TSAPtest password has been reset", body);
        } catch (Exception e) {
            log.error("Failed to send password reset email", e);
        }
    }
}
