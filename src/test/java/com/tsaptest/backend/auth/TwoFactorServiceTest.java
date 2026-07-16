package com.tsaptest.backend.auth;

import com.tsaptest.backend.auth.TwoFactorService.VerifyResult;
import com.tsaptest.backend.email.ResendEmailSender;
import com.tsaptest.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwoFactorServiceTest {

    private static final Pattern CODE_IN_EMAIL = Pattern.compile("code is (\\d{6})");

    private ResendEmailSender emailSender;
    private TwoFactorService service;
    private User user;

    @BeforeEach
    void setUp() {
        emailSender = mock(ResendEmailSender.class);
        // strength 4 = 테스트 속도용 (운영 기본값보다 훨씬 약하지만 해시 로직은 동일)
        service = new TwoFactorService(emailSender, new BCryptPasswordEncoder(4), "", false);
        user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("client@tsaptest.com");
    }

    /** 발송된 이메일 본문에서 6자리 코드를 회수한다 (코드는 해시로만 저장되므로). */
    private String sentCode() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce()).send(any(), any(), any(), body.capture());
        Matcher m = CODE_IN_EMAIL.matcher(body.getValue());
        assertThat(m.find()).as("이메일 본문에 6자리 코드 포함").isTrue();
        return m.group(1);
    }

    @Test
    void correctCodeVerifiesExactlyOnce() {
        service.issueCode(user);
        String code = sentCode();

        assertThat(service.verify(1L, code)).isEqualTo(VerifyResult.OK);
        // 성공한 코드는 즉시 무효 — 재사용(리플레이) 불가
        assertThat(service.verify(1L, code)).isEqualTo(VerifyResult.EXPIRED_OR_MISSING);
    }

    @Test
    void verifyWithoutIssuedCodeIsRejected() {
        assertThat(service.verify(1L, "123456")).isEqualTo(VerifyResult.EXPIRED_OR_MISSING);
    }

    @Test
    void wrongCodeThenCorrectCodeStillWorks() {
        service.issueCode(user);
        String code = sentCode();
        String wrong = code.equals("000000") ? "000001" : "000000";

        assertThat(service.verify(1L, wrong)).isEqualTo(VerifyResult.WRONG_CODE);
        assertThat(service.verify(1L, code)).isEqualTo(VerifyResult.OK);
    }

    @Test
    void sixthAttemptIsBlockedEvenWithCorrectCode() {
        service.issueCode(user);
        String code = sentCode();
        String wrong = code.equals("000000") ? "000001" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThat(service.verify(1L, wrong)).isEqualTo(VerifyResult.WRONG_CODE);
        }
        // 5회 실패 후에는 정답이라도 거부하고 코드를 폐기한다 (브루트포스 차단)
        assertThat(service.verify(1L, code)).isEqualTo(VerifyResult.TOO_MANY_ATTEMPTS);
        assertThat(service.verify(1L, code)).isEqualTo(VerifyResult.EXPIRED_OR_MISSING);
    }

    @Test
    void reissueInvalidatesPreviousCode() {
        service.issueCode(user);
        String first = sentCode();

        service.issueCode(user);
        String second = sentCode();

        // "Send a new code" 후 이전 코드는 더 이상 통하지 않는다
        if (!first.equals(second)) {
            assertThat(service.verify(1L, first)).isEqualTo(VerifyResult.WRONG_CODE);
        }
        assertThat(service.verify(1L, second)).isEqualTo(VerifyResult.OK);
    }

    @Test
    void overrideEmailRedirectsAllCodes() {
        TwoFactorService overridden = new TwoFactorService(
                emailSender, new BCryptPasswordEncoder(4), "admin@tsaptest.com", false);

        overridden.issueCode(user);

        // 샌드박스 우회: 수신자가 사용자 본인이 아니라 override 주소여야 한다
        verify(emailSender).send(eq("admin@tsaptest.com"), any(), any(), any());
    }

    @Test
    void withoutOverrideCodeGoesToUser() {
        service.issueCode(user);

        verify(emailSender).send(eq("client@tsaptest.com"), any(), any(), any());
    }
}
