package com.tsaptest.backend.auth;

import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TwoFactorService twoFactorService;
    private final JwtDecoder jwtDecoder;
    private final boolean twofaEnabled;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          TokenService tokenService,
                          TwoFactorService twoFactorService,
                          JwtDecoder jwtDecoder,
                          @Value("${app.twofa.enabled}") boolean twofaEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.twoFactorService = twoFactorService;
        this.jwtDecoder = jwtDecoder;
        this.twofaEnabled = twofaEnabled;
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record LoginResponse(String preAuthToken, String maskedEmail) {
    }

    public record VerifyRequest(@NotBlank String code) {
    }

    public record UserInfo(String email, String name, String role) {
    }

    public record VerifyResponse(String token, UserInfo user) {
    }

    public record ErrorResponse(String message) {
    }

    /** 1단계: 비밀번호 확인 → 임시(pre-auth) 토큰 + 이메일 코드 발송. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Optional<User> found = userRepository.findByEmailIgnoreCase(request.email().trim());
        // 계정 존재 여부가 드러나지 않도록 실패 사유는 하나의 메시지로 통일
        if (found.isEmpty()
                || !passwordEncoder.matches(request.password(), found.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid email or password."));
        }
        User user = found.get();
        // 비밀번호 검증 후에만 노출 — 본인이므로 user enumeration 문제 없음
        if (!user.isEnabled()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("This account has been disabled."));
        }
        if (!twofaEnabled) {
            // 2FA 토글 꺼짐 (TWOFA_ENABLED=false) — 즉시 정식 토큰 발급
            return ResponseEntity.ok(fullSignIn(user));
        }
        twoFactorService.issueCode(user);
        return ResponseEntity.ok(new LoginResponse(
                tokenService.issuePreAuthToken(user),
                maskEmail(user.getEmail())));
    }

    /** 2단계: 이메일 코드 검증 → 정식 토큰 발급. */
    @PostMapping("/verify")
    public ResponseEntity<?> verify(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody VerifyRequest request) {
        User user = resolvePreAuthUser(authHeader);
        if (user == null) {
            return unauthorized("Your sign-in session has expired. Please sign in again.");
        }
        return switch (twoFactorService.verify(user.getId(), request.code().trim())) {
            case OK -> ResponseEntity.ok(fullSignIn(user));
            case WRONG_CODE -> unauthorized("That code is not correct. Please try again.");
            case EXPIRED_OR_MISSING ->
                    unauthorized("Your code has expired. Please request a new one.");
            case TOO_MANY_ATTEMPTS ->
                    unauthorized("Too many attempts. Please request a new code.");
        };
    }

    /** 코드 재발송 ("Send a new code"). */
    @PostMapping("/resend")
    public ResponseEntity<?> resend(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        User user = resolvePreAuthUser(authHeader);
        if (user == null) {
            return unauthorized("Your sign-in session has expired. Please sign in again.");
        }
        twoFactorService.issueCode(user);
        return ResponseEntity.accepted().build();
    }

    /** Bearer pre-auth 토큰(scope=twofa)을 검증하고 사용자로 변환. 실패 시 null. */
    private User resolvePreAuthUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(authHeader.substring("Bearer ".length()));
        } catch (JwtException e) {
            return null;
        }
        if (!"twofa".equals(jwt.getClaimAsString("scope"))) {
            return null;
        }
        // 2FA 진행 도중 비활성화된 계정도 여기서 차단된다
        return userRepository.findById(Long.valueOf(jwt.getSubject()))
                .filter(User::isEnabled)
                .orElse(null);
    }

    private VerifyResponse fullSignIn(User user) {
        return new VerifyResponse(
                tokenService.issueAccessToken(user),
                new UserInfo(user.getEmail(), user.getDisplayName(), user.getRole().name()));
    }

    private ResponseEntity<ErrorResponse> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(message));
    }

    /** client@tsaptest.com → c•••@tsaptest.com */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at < 1) {
            return "•••";
        }
        return email.charAt(0) + "•••" + email.substring(at);
    }
}
