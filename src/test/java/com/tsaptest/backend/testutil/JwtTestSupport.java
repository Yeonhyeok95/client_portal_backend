package com.tsaptest.backend.testutil;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tsaptest.backend.auth.TokenService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * 테스트 전용 JWT 인프라 — SecurityConfig와 같은 방식(HS256 대칭키)으로
 * 인코더/디코더/컨버터를 만든다. 시크릿은 컴파일 타임 상수라서
 * 어노테이션 속성(@SpringBootTest properties 등)에서도 참조 가능.
 */
public final class JwtTestSupport {

    /** HS256은 최소 256비트(32바이트) 키 필요 */
    public static final String SECRET = "test-only-secret-test-only-secret-42";

    private JwtTestSupport() {
    }

    private static SecretKey key() {
        return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public static JwtEncoder encoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key()));
    }

    public static JwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** SecurityConfig.jwtAuthenticationConverter()와 동일한 매핑 (role → ROLE_*) */
    public static JwtAuthenticationConverter converter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    public static TokenService tokenService() {
        return new TokenService(encoder(), Duration.ofHours(12));
    }

    /** 정식 토큰과 동일한 형태 (role 포함) */
    public static String roleToken(long userId, String role) {
        return encode(JwtClaimsSet.builder()
                .issuer("tsaptest-backend")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                .subject(String.valueOf(userId))
                .claim("role", role)
                .build());
    }

    /** pre-auth 토큰과 동일한 형태 (scope=twofa, role 없음) */
    public static String preAuthToken(long userId) {
        return encode(JwtClaimsSet.builder()
                .issuer("tsaptest-backend")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
                .subject(String.valueOf(userId))
                .claim("scope", "twofa")
                .build());
    }

    /** 이미 만료된 토큰 */
    public static String expiredToken(long userId) {
        return encode(JwtClaimsSet.builder()
                .issuer("tsaptest-backend")
                .issuedAt(Instant.now().minus(Duration.ofHours(2)))
                .expiresAt(Instant.now().minus(Duration.ofHours(1)))
                .subject(String.valueOf(userId))
                .claim("role", "CLIENT")
                .build());
    }

    private static String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder().encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
