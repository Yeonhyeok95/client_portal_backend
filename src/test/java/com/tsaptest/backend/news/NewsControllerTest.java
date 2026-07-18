package com.tsaptest.backend.news;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 뉴스 API 인가 규칙: 조회는 완전 공개, 수집/숨김 관리는 ADVISOR·ADMIN만.
 */
@WebMvcTest(NewsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.jwt.secret=" + JwtTestSupport.SECRET,
        "app.cors.allowed-origins=http://localhost:3000"
})
class NewsControllerTest {

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
    private NewsFetchService fetchService;

    @MockitoBean
    private NewsArticleRepository repository;

    private NewsArticle article() {
        return new NewsArticle("CNBC", NewsCategory.MARKETS, "Fed holds rates",
                "summary", "https://example.com/fed",
                Instant.parse("2026-07-18T00:00:00Z"), Instant.now());
    }

    // ---- 공개 조회 ----

    @Test
    void listIsPublicWithPublicCacheHeader() throws Exception {
        when(fetchService.hasArticles()).thenReturn(true);
        when(repository.findByHiddenFalseOrderByPublishedAtDescIdDesc())
                .thenReturn(List.of(article()));

        mockMvc.perform(get("/api/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Fed holds rates"))
                .andExpect(jsonPath("$[0].url").value("https://example.com/fed"))
                // 공개 콘텐츠 — 포트폴리오(no-store)와 달리 공유 캐시 허용
                .andExpect(header().string("Cache-Control", containsString("public")));
    }

    @Test
    void listWithCategoryFilters() throws Exception {
        when(fetchService.hasArticles()).thenReturn(true);
        when(repository.findByHiddenFalseAndCategoryOrderByPublishedAtDescIdDesc(
                NewsCategory.TAX_ACCOUNTING)).thenReturn(List.of());

        mockMvc.perform(get("/api/news").param("category", "TAX_ACCOUNTING"))
                .andExpect(status().isOk());
        verify(repository).findByHiddenFalseAndCategoryOrderByPublishedAtDescIdDesc(
                NewsCategory.TAX_ACCOUNTING);
    }

    @Test
    void firstListTriggersSynchronousRefreshWhenEmpty() throws Exception {
        when(fetchService.hasArticles()).thenReturn(false);

        mockMvc.perform(get("/api/news")).andExpect(status().isOk());

        verify(fetchService).refresh();
        verify(fetchService, never()).refreshAsync();
    }

    @Test
    void staleListTriggersBackgroundRefreshOnly() throws Exception {
        when(fetchService.hasArticles()).thenReturn(true);
        when(fetchService.isStale()).thenReturn(true);

        mockMvc.perform(get("/api/news")).andExpect(status().isOk());

        verify(fetchService).refreshAsync();
        verify(fetchService, never()).refresh();
    }

    // ---- 관리 (ADVISOR·ADMIN) ----

    @Test
    void refreshRequiresAdvisorOrAdmin() throws Exception {
        mockMvc.perform(post("/api/news/refresh"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/news/refresh")
                        .header("Authorization", "Bearer " + JwtTestSupport.roleToken(1L, "CLIENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/news/refresh")
                        .header("Authorization", "Bearer " + JwtTestSupport.preAuthToken(1L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/news/refresh")
                        .header("Authorization", "Bearer " + JwtTestSupport.roleToken(2L, "ADVISOR")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/news/refresh")
                        .header("Authorization", "Bearer " + JwtTestSupport.roleToken(100L, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void hideArticleWorksForAdvisorAnd404sOnUnknownId() throws Exception {
        NewsArticle a = article();
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.save(a)).thenReturn(a);
        when(repository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/news/1/hidden")
                        .header("Authorization", "Bearer " + JwtTestSupport.roleToken(2L, "ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/news/99/hidden")
                        .header("Authorization", "Bearer " + JwtTestSupport.roleToken(2L, "ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\":true}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/news/1/hidden")
                        .header("Authorization", "Bearer " + JwtTestSupport.roleToken(1L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\":true}"))
                .andExpect(status().isForbidden());
    }
}
