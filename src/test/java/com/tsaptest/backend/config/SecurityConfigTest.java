package com.tsaptest.backend.config;

import com.tsaptest.backend.portfolio.PortfolioController;
import com.tsaptest.backend.portfolio.PortfolioService;
import com.tsaptest.backend.testutil.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig 통합 검증 — 이 프로젝트 보안의 대들보:
 * "API 전체는 role 필수" 규칙 때문에 pre-auth 토큰(2FA 전)이 무력화된다.
 */
@WebMvcTest(PortfolioController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.jwt.secret=" + JwtTestSupport.SECRET,
        "app.cors.allowed-origins=http://localhost:3000"
})
class SecurityConfigTest {

    /**
     * BackendApplication의 @EnableCaching이 슬라이스에도 적용되는데
     * @WebMvcTest에는 CacheManager 자동구성이 없어서 직접 공급한다.
     */
    @TestConfiguration
    static class CacheStub {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("portfolio");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/portfolio"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preAuthTokenCannotAccessApi() throws Exception {
        // 서명은 유효하지만 role이 없는 토큰 — 인증은 되나 인가(403)에서 막혀야 한다
        mockMvc.perform(get("/api/portfolio")
                        .header("Authorization", "Bearer " + JwtTestSupport.preAuthToken(1L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/portfolio")
                        .header("Authorization", "Bearer " + JwtTestSupport.expiredToken(1L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsUnauthorized() throws Exception {
        String token = JwtTestSupport.roleToken(1L, "CLIENT");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/portfolio")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientTokenAccessesPortfolioWithNoStoreCacheHeaders() throws Exception {
        mockMvc.perform(get("/api/portfolio")
                        .header("Authorization", "Bearer " + JwtTestSupport.roleToken(1L, "CLIENT")))
                .andExpect(status().isOk())
                // 개인 금융정보 — 공유/CDN 캐시 금지 헤더가 반드시 붙어야 한다
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void advisorRoleAlsoAllowed() throws Exception {
        mockMvc.perform(get("/api/portfolio")
                        .header("Authorization", "Bearer " + JwtTestSupport.roleToken(2L, "ADVISOR")))
                .andExpect(status().isOk());
    }

    @Test
    void adminTokenCannotAccessClientApis() throws Exception {
        // 최소권한: ADMIN은 /api/admin/** 전용 — 고객 금융 데이터 API는 403
        mockMvc.perform(get("/api/portfolio")
                        .header("Authorization", "Bearer " + JwtTestSupport.roleToken(100L, "ADMIN")))
                .andExpect(status().isForbidden());
    }
}
