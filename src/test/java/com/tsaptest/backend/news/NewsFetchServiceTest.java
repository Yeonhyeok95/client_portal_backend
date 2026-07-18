package com.tsaptest.backend.news;

import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 수집 파이프라인 규칙: url 중복 skip, HTML 정리, 피드 장애 격리, 보존 한도(prune), 신선도 판정.
 * 실제 HTTP 대신 FeedLoader를 픽스처로 대체한다.
 */
class NewsFetchServiceTest {

    private NewsArticleRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(NewsArticleRepository.class);
        when(repository.findByCategoryOrderByPublishedAtDescIdDesc(any())).thenReturn(List.of());
    }

    private static SyndFeed feedWith(SyndEntry... entries) {
        SyndFeedImpl feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setTitle("fixture");
        feed.setEntries(List.of(entries));
        return feed;
    }

    private static SyndEntry entry(String title, String link, String descriptionHtml) {
        SyndEntryImpl e = new SyndEntryImpl();
        e.setTitle(title);
        e.setLink(link);
        if (descriptionHtml != null) {
            SyndContentImpl desc = new SyndContentImpl();
            desc.setValue(descriptionHtml);
            e.setDescription(desc);
        }
        e.setPublishedDate(Date.from(Instant.parse("2026-07-18T00:00:00Z")));
        return e;
    }

    /** 첫 번째 피드(CNBC Markets)에만 픽스처를 주고 나머지 피드는 실패시키는 로더 */
    private static NewsFetchService.FeedLoader singleFeedLoader(SyndFeed feed) {
        String firstUrl = NewsFetchService.FEEDS.get(0).url();
        return url -> {
            if (url.equals(firstUrl)) {
                return feed;
            }
            throw new RuntimeException("simulated feed outage");
        };
    }

    @Test
    void mapsEntryAndSurvivesOtherFeedOutages() {
        SyndFeed feed = feedWith(entry(
                "Fed holds rates",
                " https://example.com/fed ",
                "<p>Rates &amp; more <b>details</b></p>"));
        NewsFetchService service = new NewsFetchService(repository, singleFeedLoader(feed));

        // 나머지 5개 피드가 전부 죽어도 첫 피드의 기사는 저장돼야 한다 (장애 격리)
        int added = service.refresh();

        assertThat(added).isEqualTo(1);
        ArgumentCaptor<NewsArticle> saved = ArgumentCaptor.forClass(NewsArticle.class);
        verify(repository).save(saved.capture());
        NewsArticle a = saved.getValue();
        assertThat(a.getTitle()).isEqualTo("Fed holds rates");
        assertThat(a.getUrl()).isEqualTo("https://example.com/fed");
        assertThat(a.getSummary()).isEqualTo("Rates & more details"); // 태그 제거+엔티티 복원
        assertThat(a.getSource()).isEqualTo("CNBC");
        assertThat(a.getCategory()).isEqualTo(NewsCategory.MARKETS);
        assertThat(a.getPublishedAt()).isEqualTo(Instant.parse("2026-07-18T00:00:00Z"));
    }

    @Test
    void skipsDuplicateUrlsAndEntriesWithoutLinkOrTitle() {
        when(repository.existsByUrl("https://example.com/dup")).thenReturn(true);
        SyndFeed feed = feedWith(
                entry("Already stored", "https://example.com/dup", null),
                entry("No link", "", null),
                entry("", "https://example.com/no-title", null));
        NewsFetchService service = new NewsFetchService(repository, singleFeedLoader(feed));

        assertThat(service.refresh()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void prunesBeyondKeepLimitPerCategory() {
        List<NewsArticle> many = IntStream.range(0, NewsFetchService.KEEP_PER_CATEGORY + 5)
                .mapToObj(i -> new NewsArticle("CNBC", NewsCategory.MARKETS, "t" + i, null,
                        "https://example.com/" + i, Instant.now(), Instant.now()))
                .toList();
        when(repository.findByCategoryOrderByPublishedAtDescIdDesc(NewsCategory.MARKETS))
                .thenReturn(many);
        NewsFetchService service = new NewsFetchService(repository, singleFeedLoader(feedWith()));

        service.refresh();

        ArgumentCaptor<List<NewsArticle>> deleted = ArgumentCaptor.forClass(List.class);
        verify(repository).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).hasSize(5); // 한도 초과분만 삭제
    }

    @Test
    void staleWhenNeverFetchedOrOlderThanAnHour() {
        NewsFetchService service = new NewsFetchService(repository, singleFeedLoader(feedWith()));

        when(repository.maxFetchedAt()).thenReturn(null);
        assertThat(service.isStale()).isTrue();

        when(repository.maxFetchedAt()).thenReturn(Instant.now().minus(Duration.ofMinutes(5)));
        assertThat(service.isStale()).isFalse();

        when(repository.maxFetchedAt()).thenReturn(Instant.now().minus(Duration.ofMinutes(90)));
        assertThat(service.isStale()).isTrue();
    }

    @Test
    void cleanTextStripsHtmlDecodesEntitiesAndTruncates() {
        assertThat(NewsFetchService.cleanText(
                "<div>A &quot;big&quot;   move&nbsp;&#39;today&#39;</div>", 100))
                .isEqualTo("A \"big\" move 'today'");
        String longText = "x".repeat(500);
        assertThat(NewsFetchService.cleanText(longText, 100)).hasSizeLessThanOrEqualTo(100);
        assertThat(NewsFetchService.cleanText(longText, 100)).endsWith("…");
        assertThat(NewsFetchService.cleanText(null, 100)).isEmpty();
    }
}
