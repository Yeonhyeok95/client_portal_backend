package com.tsaptest.backend.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 외부 RSS에서 수집한 기사 한 건. 본문은 저장하지 않고 원문 url로 연결만 한다. */
@Entity
@Table(name = "news_articles")
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NewsCategory category;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 1000)
    private String summary;

    @Column(nullable = false, length = 1000, unique = true)
    private String url;

    // 카드 썸네일 — 이미지 없는 항목은 수집 단계에서 걸러지므로 항상 존재
    @Column(nullable = false, length = 1000)
    private String imageUrl;

    private Instant publishedAt;

    @Column(nullable = false)
    private Instant fetchedAt;

    // 관리자/상담사가 부적절 기사를 목록에서 감출 때 사용 (데이터는 보존)
    @Column(nullable = false)
    private boolean hidden = false;

    protected NewsArticle() {
        // JPA 스펙상 기본 생성자 필요
    }

    public NewsArticle(String source, NewsCategory category, String title, String summary,
                       String url, String imageUrl, Instant publishedAt, Instant fetchedAt) {
        this.source = source;
        this.category = category;
        this.title = title;
        this.summary = summary;
        this.url = url;
        this.imageUrl = imageUrl;
        this.publishedAt = publishedAt;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public NewsCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getUrl() {
        return url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
