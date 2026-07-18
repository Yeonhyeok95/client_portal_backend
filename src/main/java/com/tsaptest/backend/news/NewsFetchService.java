package com.tsaptest.backend.news;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 외부 RSS 피드에서 금융/회계 뉴스를 수집해 DB에 저장한다.
 *
 * Render 무료 티어는 유휴 시 슬립하므로 @Scheduled만으로는 부족하다 —
 * 컨트롤러가 조회 시 신선도를 검사해(isStale) 필요할 때 갱신을 트리거하는 것이 주 경로,
 * 스케줄은 서버가 깨어 있는 동안의 보너스다.
 */
@Service
public class NewsFetchService {

    /** 피드 하나 = 소스 이름 + 카테고리 + RSS 주소 */
    record FeedSpec(String source, NewsCategory category, String url) {
    }

    /** url → 파싱된 피드. 테스트에서 픽스처 XML로 대체하기 위한 이음새. */
    @FunctionalInterface
    interface FeedLoader {
        SyndFeed load(String url) throws Exception;
    }

    // 2026-07-18 실응답 검증 완료 목록. 죽은 피드는 여기만 교체하면 된다.
    static final List<FeedSpec> FEEDS = List.of(
            new FeedSpec("CNBC", NewsCategory.MARKETS,
                    "https://www.cnbc.com/id/20910258/device/rss/rss.html"),
            new FeedSpec("MarketWatch", NewsCategory.MARKETS,
                    "https://feeds.content.dowjones.io/public/rss/mw_topstories"),
            new FeedSpec("CNBC", NewsCategory.BUSINESS,
                    "https://www.cnbc.com/id/10001147/device/rss/rss.html"),
            new FeedSpec("Yahoo Finance", NewsCategory.BUSINESS,
                    "https://finance.yahoo.com/news/rssindex"),
            new FeedSpec("Accounting Today", NewsCategory.TAX_ACCOUNTING,
                    "https://www.accountingtoday.com/feed?rss=true"),
            new FeedSpec("Tax Foundation", NewsCategory.TAX_ACCOUNTING,
                    "https://taxfoundation.org/feed/"));

    /** 카테고리별 보존 한도 — 초과분은 오래된 것부터 삭제 (무료 DB 용량 보호) */
    static final int KEEP_PER_CATEGORY = 60;

    private static final Duration STALE_AFTER = Duration.ofMinutes(60);
    private static final int TITLE_MAX = 500;
    private static final int SUMMARY_MAX = 400;

    private static final Logger log = LoggerFactory.getLogger(NewsFetchService.class);

    private final NewsArticleRepository repository;
    private final FeedLoader feedLoader;
    // 스케줄/비동기/수동 갱신이 겹쳐도 수집은 한 번에 하나만
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    // 생성자가 둘(테스트용 포함)이라 스프링이 쓸 쪽을 명시해야 한다
    @Autowired
    public NewsFetchService(NewsArticleRepository repository) {
        this(repository, httpFeedLoader());
    }

    NewsFetchService(NewsArticleRepository repository, FeedLoader feedLoader) {
        this.repository = repository;
        this.feedLoader = feedLoader;
    }

    private static FeedLoader httpFeedLoader() {
        // 타임아웃 필수 — 없으면 느린 피드 하나가 수집 전체(=첫 조회 요청)를 무한정 붙잡는다
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        RestClient client = RestClient.builder()
                .requestFactory(requestFactory)
                // 일부 매체는 기본 UA를 봇으로 보고 거부한다
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (compatible; TSAPtestNews/1.0)")
                .build();
        return url -> {
            byte[] body = client.get().uri(url).retrieve().body(byte[].class);
            return new SyndFeedInput().build(new XmlReader(new ByteArrayInputStream(body)));
        };
    }

    /** 전체 피드 수집. 피드 단위로 격리해 한 피드 장애가 나머지를 못 죽인다. @return 신규 저장 건수 */
    public int refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return 0; // 이미 다른 스레드가 수집 중
        }
        try {
            Instant now = Instant.now();
            int added = 0;
            // 네트워크는 병렬(느린 피드가 전체를 지연시키지 않게), DB 저장은 순차
            for (Map.Entry<FeedSpec, SyndFeed> loaded : loadAllFeeds().entrySet()) {
                added += ingestFeed(loaded.getKey(), loaded.getValue(), now);
            }
            for (NewsCategory category : NewsCategory.values()) {
                prune(category);
            }
            log.info("News refresh done: {} new articles", added);
            return added;
        } finally {
            refreshing.set(false);
        }
    }

    /** 전 피드를 가상 스레드로 동시에 fetch/파싱. 실패한 피드는 결과에서 빠진다 (장애 격리). */
    private Map<FeedSpec, SyndFeed> loadAllFeeds() {
        Map<FeedSpec, Future<SyndFeed>> futures = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (FeedSpec feed : FEEDS) {
                futures.put(feed, executor.submit(() -> feedLoader.load(feed.url())));
            }
        } // try-with-resources close()가 모든 작업 완료를 기다린다
        Map<FeedSpec, SyndFeed> result = new LinkedHashMap<>();
        for (Map.Entry<FeedSpec, Future<SyndFeed>> e : futures.entrySet()) {
            try {
                result.put(e.getKey(), e.getValue().get());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.warn("News feed failed, skipping: {} ({}) — {}",
                        e.getKey().source(), e.getKey().url(), ex.toString());
            }
        }
        return result;
    }

    /** 컨트롤러가 조회 시 부르는 비동기 갱신 (응답은 기존 데이터로 즉시 나간다). */
    @Async
    public void refreshAsync() {
        refresh();
    }

    /** 서버가 깨어 있는 동안의 주기 수집. 기동 직후는 첫 조회의 stale-check에 맡긴다. */
    @Scheduled(initialDelayString = "PT15M", fixedDelayString = "PT1H")
    void scheduledRefresh() {
        refresh();
    }

    public boolean hasArticles() {
        return repository.count() > 0;
    }

    public boolean isStale() {
        Instant last = repository.maxFetchedAt();
        return last == null || last.isBefore(Instant.now().minus(STALE_AFTER));
    }

    private int ingestFeed(FeedSpec feed, SyndFeed parsed, Instant now) {
        int added = 0;
        for (SyndEntry entry : parsed.getEntries()) {
            String url = entry.getLink() != null ? entry.getLink().trim() : "";
            String title = cleanText(entry.getTitle(), TITLE_MAX);
            if (url.isBlank() || url.length() > 1000 || title.isBlank()
                    || repository.existsByUrl(url)) {
                continue;
            }
            String summary = entry.getDescription() != null
                    ? cleanText(entry.getDescription().getValue(), SUMMARY_MAX)
                    : null;
            Instant publishedAt = entry.getPublishedDate() != null
                    ? entry.getPublishedDate().toInstant()
                    : (entry.getUpdatedDate() != null ? entry.getUpdatedDate().toInstant() : now);
            repository.save(new NewsArticle(
                    feed.source(), feed.category(), title, summary, url, publishedAt, now));
            added++;
        }
        return added;
    }

    private void prune(NewsCategory category) {
        List<NewsArticle> all = repository.findByCategoryOrderByPublishedAtDescIdDesc(category);
        if (all.size() > KEEP_PER_CATEGORY) {
            repository.deleteAll(all.subList(KEEP_PER_CATEGORY, all.size()));
        }
    }

    /** RSS 요약에 섞여 오는 HTML 태그/엔티티 제거 + 길이 제한. */
    static String cleanText(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String text = raw
                .replaceAll("(?s)<[^>]*>", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() > max) {
            text = text.substring(0, max - 1).trim() + "…";
        }
        return text;
    }
}
