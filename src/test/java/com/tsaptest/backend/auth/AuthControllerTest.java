package com.tsaptest.backend.auth;

import com.tsaptest.backend.auth.AuthController.ErrorResponse;
import com.tsaptest.backend.auth.AuthController.LoginRequest;
import com.tsaptest.backend.auth.AuthController.LoginResponse;
import com.tsaptest.backend.auth.AuthController.VerifyRequest;
import com.tsaptest.backend.auth.AuthController.VerifyResponse;
import com.tsaptest.backend.auth.TwoFactorService.VerifyResult;
import com.tsaptest.backend.testutil.JwtTestSupport;
import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import com.tsaptest.backend.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 로그인 2단계 흐름의 컨트롤러 규칙:
 * 1단계(login)는 pre-auth 토큰만, 2단계(verify) 통과 후에만 정식 토큰.
 */
class AuthControllerTest {

    private final PasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
    private final TokenService tokenService = JwtTestSupport.tokenService();

    private UserRepository userRepository;
    private TwoFactorService twoFactorService;
    private AuthController controller; // 2FA 켜짐

    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        twoFactorService = mock(TwoFactorService.class);
        controller = controller(true);

        user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("client@tsaptest.com");
        when(user.getDisplayName()).thenReturn("Eleanor Whitfield");
        when(user.getRole()).thenReturn(UserRole.CLIENT);
        when(user.getPasswordHash()).thenReturn(bcrypt.encode("correct-password"));
        when(userRepository.findByEmailIgnoreCase("client@tsaptest.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private AuthController controller(boolean twofaEnabled) {
        return new AuthController(userRepository, bcrypt, tokenService,
                twoFactorService, JwtTestSupport.decoder(), twofaEnabled);
    }

    // ---- 1단계: login ----

    @Test
    void unknownEmailAndWrongPasswordReturnIdenticalError() {
        ResponseEntity<?> unknown = controller.login(
                new LoginRequest("nobody@tsaptest.com", "whatever"));
        ResponseEntity<?> wrongPw = controller.login(
                new LoginRequest("client@tsaptest.com", "wrong-password"));

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPw.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // 계정 존재 여부가 응답으로 드러나면 안 된다 (user enumeration 방지)
        assertThat(((ErrorResponse) unknown.getBody()).message())
                .isEqualTo(((ErrorResponse) wrongPw.getBody()).message());
    }

    @Test
    void loginWithTwofaReturnsPreAuthTokenOnly() {
        ResponseEntity<?> response = controller.login(
                new LoginRequest("client@tsaptest.com", "correct-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = (LoginResponse) response.getBody();
        assertThat(body.maskedEmail()).isEqualTo("c•••@tsaptest.com");

        Jwt jwt = JwtTestSupport.decoder().decode(body.preAuthToken());
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("twofa");
        assertThat(jwt.getClaimAsString("role")).isNull();
        verify(twoFactorService).issueCode(user);
    }

    @Test
    void loginWithTwofaDisabledIssuesFullTokenImmediately() {
        ResponseEntity<?> response = controller(false).login(
                new LoginRequest("client@tsaptest.com", "correct-password"));

        VerifyResponse body = (VerifyResponse) response.getBody();
        Jwt jwt = JwtTestSupport.decoder().decode(body.token());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CLIENT");
        verify(twoFactorService, never()).issueCode(any());
    }

    @Test
    void emailIsTrimmedAndCaseHandledByRepositoryLookup() {
        ResponseEntity<?> response = controller.login(
                new LoginRequest("  client@tsaptest.com  ", "correct-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- 2단계: verify ----

    @Test
    void verifyWithCorrectCodeIssuesFullToken() {
        when(twoFactorService.verify(1L, "123456")).thenReturn(VerifyResult.OK);
        String preAuth = tokenService.issuePreAuthToken(user);

        ResponseEntity<?> response = controller.verify(
                "Bearer " + preAuth, new VerifyRequest("123456"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        VerifyResponse body = (VerifyResponse) response.getBody();
        Jwt jwt = JwtTestSupport.decoder().decode(body.token());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CLIENT");
        assertThat(body.user().email()).isEqualTo("client@tsaptest.com");
    }

    @Test
    void verifyRejectsWrongExpiredAndBlockedCodes() {
        String preAuth = "Bearer " + tokenService.issuePreAuthToken(user);

        when(twoFactorService.verify(1L, "111111")).thenReturn(VerifyResult.WRONG_CODE);
        when(twoFactorService.verify(1L, "222222")).thenReturn(VerifyResult.EXPIRED_OR_MISSING);
        when(twoFactorService.verify(1L, "333333")).thenReturn(VerifyResult.TOO_MANY_ATTEMPTS);

        assertThat(controller.verify(preAuth, new VerifyRequest("111111")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.verify(preAuth, new VerifyRequest("222222")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.verify(preAuth, new VerifyRequest("333333")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void verifyWithoutHeaderIsRejected() {
        ResponseEntity<?> response = controller.verify(null, new VerifyRequest("123456"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(twoFactorService, never()).verify(any(), any());
    }

    @Test
    void verifyRejectsFullAccessTokenAsPreAuth() {
        // 정식 토큰에는 scope=twofa가 없으므로 verify 단계에서 쓸 수 없어야 한다
        String accessToken = tokenService.issueAccessToken(user);

        ResponseEntity<?> response = controller.verify(
                "Bearer " + accessToken, new VerifyRequest("123456"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(twoFactorService, never()).verify(any(), any());
    }

    @Test
    void verifyRejectsGarbageToken() {
        ResponseEntity<?> response = controller.verify(
                "Bearer not-a-jwt", new VerifyRequest("123456"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- 재발송: resend ----

    @Test
    void resendWithPreAuthTokenIssuesNewCode() {
        String preAuth = tokenService.issuePreAuthToken(user);

        ResponseEntity<?> response = controller.resend("Bearer " + preAuth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(twoFactorService).issueCode(user);
    }

    @Test
    void resendWithoutTokenIsRejected() {
        ResponseEntity<?> response = controller.resend(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(twoFactorService, never()).issueCode(any());
    }
}
