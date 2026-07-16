package com.tsaptest.backend.auth;

import com.tsaptest.backend.testutil.JwtTestSupport;
import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 2단계 토큰 설계의 핵심 규칙:
 * 정식 토큰에는 role이 있고, pre-auth 토큰에는 role이 없어야 한다
 * (role이 없으면 hasAnyRole로 잠긴 API 전체에 접근 불가).
 */
class TokenServiceTest {

    private final TokenService tokenService = JwtTestSupport.tokenService();

    private User user(long id, UserRole role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn("client@tsaptest.com");
        when(user.getDisplayName()).thenReturn("Eleanor Whitfield");
        when(user.getRole()).thenReturn(role);
        return user;
    }

    @Test
    void accessTokenContainsRoleAndIdentity() {
        String token = tokenService.issueAccessToken(user(7L, UserRole.CLIENT));

        Jwt jwt = JwtTestSupport.decoder().decode(token);
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CLIENT");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("client@tsaptest.com");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Eleanor Whitfield");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofHours(12));
    }

    @Test
    void preAuthTokenHasNoRoleAndShortTtl() {
        String token = tokenService.issuePreAuthToken(user(7L, UserRole.CLIENT));

        Jwt jwt = JwtTestSupport.decoder().decode(token);
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("twofa");
        // role 클레임이 없어야 어떤 API 권한도 얻지 못한다
        assertThat(jwt.getClaimAsString("role")).isNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void preAuthTokenYieldsNoRoleAuthority() {
        String token = tokenService.issuePreAuthToken(user(7L, UserRole.CLIENT));

        var authentication = JwtTestSupport.converter()
                .convert(JwtTestSupport.decoder().decode(token));
        // Security 7은 모든 JWT에 FACTOR_BEARER를 부여하므로 "비어 있음"이 아니라
        // "ROLE_* 없음"이 pre-auth 토큰의 올바른 불변식이다
        assertThat(authentication.getAuthorities())
                .extracting(a -> a.getAuthority())
                .noneMatch(a -> a.startsWith("ROLE_"));
    }

    @Test
    void accessTokenYieldsRoleAuthority() {
        String token = tokenService.issueAccessToken(user(7L, UserRole.ADVISOR));

        var authentication = JwtTestSupport.converter()
                .convert(JwtTestSupport.decoder().decode(token));
        assertThat(authentication.getAuthorities())
                .extracting(a -> a.getAuthority())
                .contains("ROLE_ADVISOR");
    }
}
