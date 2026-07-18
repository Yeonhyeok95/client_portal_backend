package com.tsaptest.backend.news;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * /insights 뉴스 API.
 * GET은 공개(마케팅 페이지), refresh/hidden 관리는 ADVISOR·ADMIN — SecurityConfig 참조.
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    public record NewsDto(Long id, String source, String category, String title,
                          String summary, String url, Instant publishedAt) {

        static NewsDto from(NewsArticle a) {
            return new NewsDto(a.getId(), a.getSource(), a.getCategory().name(),
                    a.getTitle(), a.getSummary(), a.getUrl(), a.getPublishedAt());
        }
    }

    public record RefreshResponse(int added) {
    }

    public record HiddenRequest(@NotNull Boolean hidden) {
    }

    private final NewsFetchService fetchService;
    private final NewsArticleRepository repository;

    public NewsController(NewsFetchService fetchService, NewsArticleRepository repository) {
        this.fetchService = fetchService;
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<NewsDto>> list(
            @RequestParam(required = false) NewsCategory category) {
        // 최초(빈 DB)는 동기 수집으로 내용을 만들어 주고,
        // 이후에는 오래됐으면 백그라운드 갱신을 걸고 기존 데이터로 즉시 응답한다
        if (!fetchService.hasArticles()) {
            fetchService.refresh();
        } else if (fetchService.isStale()) {
            fetchService.refreshAsync();
        }
        List<NewsArticle> articles = category == null
                ? repository.findByHiddenFalseOrderByPublishedAtDescIdDesc()
                : repository.findByHiddenFalseAndCategoryOrderByPublishedAtDescIdDesc(category);
        return ResponseEntity.ok()
                // 공개 콘텐츠 — 브라우저/CDN 5분 캐시 허용 (포트폴리오의 no-store와 반대)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(articles.stream().map(NewsDto::from).toList());
    }

    /** 수동 즉시 수집 (ADVISOR·ADMIN). */
    @PostMapping("/refresh")
    public RefreshResponse refresh() {
        return new RefreshResponse(fetchService.refresh());
    }

    /** 부적절 기사 숨김/복원 (ADVISOR·ADMIN). 데이터는 보존된다. */
    @PatchMapping("/{id}/hidden")
    public NewsDto setHidden(@PathVariable Long id, @Valid @RequestBody HiddenRequest request) {
        NewsArticle article = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        article.setHidden(request.hidden());
        return NewsDto.from(repository.save(article));
    }
}
