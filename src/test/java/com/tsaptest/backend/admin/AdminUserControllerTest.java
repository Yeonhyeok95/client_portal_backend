package com.tsaptest.backend.admin;

import com.tsaptest.backend.config.SecurityConfig;
import com.tsaptest.backend.testutil.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/admin/** 인가 규칙: ADMIN 토큰만 통과, CLIENT/ADVISOR/pre-auth는 403.
 * (반대 방향 — ADMIN이 일반 API 접근 불가 — 는 SecurityConfigTest에서 검증)
 */
@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.jwt.secret=" + JwtTestSupport.SECRET,
        "app.cors.allowed-origins=http://localhost:3000"
})
class AdminUserControllerTest {

    /** SecurityConfigTest와 동일 — @EnableCaching이 슬라이스에 요구하는 CacheManager 공급 */
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
    private AdminUserService adminUserService;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---- 인가 ----

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientAndAdvisorAndPreAuthTokensAreForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(JwtTestSupport.roleToken(1L, "CLIENT"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(JwtTestSupport.roleToken(2L, "ADVISOR"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(JwtTestSupport.preAuthToken(1L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenListsUsers() throws Exception {
        when(adminUserService.listUsers()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(JwtTestSupport.roleToken(100L, "ADMIN"))))
                .andExpect(status().isOk());
    }

    // ---- 동작/에러 매핑 ----

    @Test
    void adminTokenDeletesUser() throws Exception {
        mockMvc.perform(delete("/api/admin/users/1")
                        .header("Authorization", bearer(JwtTestSupport.roleToken(100L, "ADMIN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminOperationExceptionMapsToStatusAndMessage() throws Exception {
        doThrow(new AdminOperationException(HttpStatus.CONFLICT,
                "An account with this email already exists."))
                .when(adminUserService)
                .createUser(anyString(), anyString(), any(), anyString());

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(JwtTestSupport.roleToken(100L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"client@tsaptest.com","displayName":"Dup",
                                 "role":"CLIENT","initialPassword":"Password!123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("An account with this email already exists."));
    }

    @Test
    void createValidatesRequestBody() throws Exception {
        // 비밀번호 8자 미만 — @Size(min=8) 위반
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(JwtTestSupport.roleToken(100L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"x@tsaptest.com","displayName":"X",
                                 "role":"CLIENT","initialPassword":"short"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchEnabledPassesActorIdFromJwtSubject() throws Exception {
        mockMvc.perform(patch("/api/admin/users/1/enabled")
                        .header("Authorization", bearer(JwtTestSupport.roleToken(100L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(adminUserService).setEnabled(100L, 1L, false);
    }

    @Test
    void passwordResetReturnsAcceptedWithoutBody() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/password-reset")
                        .header("Authorization", bearer(JwtTestSupport.roleToken(100L, "ADMIN"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").doesNotExist());
    }
}
