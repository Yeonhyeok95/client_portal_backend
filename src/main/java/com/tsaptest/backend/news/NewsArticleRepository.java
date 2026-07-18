package com.tsaptest.backend.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    List<NewsArticle> findByHiddenFalseOrderByPublishedAtDescIdDesc();

    List<NewsArticle> findByHiddenFalseAndCategoryOrderByPublishedAtDescIdDesc(NewsCategory category);

    /** prune용 — hidden 포함 전체 (숨긴 기사도 보존 한도에는 포함) */
    List<NewsArticle> findByCategoryOrderByPublishedAtDescIdDesc(NewsCategory category);

    boolean existsByUrl(String url);

    /** 마지막 수집 시각 — 신선도(stale) 판정 기준. 기사 없으면 null. */
    @Query("select max(a.fetchedAt) from NewsArticle a")
    Instant maxFetchedAt();
}
