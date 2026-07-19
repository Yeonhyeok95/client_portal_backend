package com.tsaptest.backend.news;

import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEnclosureImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 수집 파이프라인 규칙: 이미지 없는 항목 skip, url 중복 skip, HTML 정리,
 * 피드 장애 격리, 보존 한도 45(=9장×5페이지), 신선도 판정.
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

    private static SyndEntry entry(String title, String link, String descriptionHtml,
                                   String imageUrl) {
        SyndEntryImpl e = new SyndEntryImpl();
        e.setTitle(title);
        e.setLink(link);
        if (descriptionHtml != null) {
            SyndContentImpl desc = new SyndContentImpl();
            desc.setValue(descriptionHtml);
            e.setDescription(desc);
        }
        if (imageUrl != null) {
            SyndEnclosureImpl enclosure = new SyndEnclosureImpl();
            enclosure.setUrl(imageUrl);
            enclosure.setType("image/jpeg");
            e.setEnclosures(List.of(enclosure));
        }
        e.setPublishedDate(Date.from(Instant.parse("2026-07-18T00:00:00Z")));
        return e;
    }

    /** 첫 번째 피드(MarketWatch)에만 픽스처를 주고 나머지 피드는 실패시키는 로더 */
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
    void mapsEntryWithImageAndSurvivesOtherFeedOutages() {
        SyndFeed feed = feedWith(entry(
                "Fed holds rates",
                " https://example.com/fed ",
                "<p>Rates &amp; more <b>details</b></p>",
                "https://img.example.com/fed.jpg"));
        NewsFetchService service = new NewsFetchService(repository, singleFeedLoader(feed));

        // 나머지 피드가 전부 죽어도 첫 피드의 기사는 저장돼야 한다 (장애 격리)
        int added = service.refresh();

        assertThat(added).isEqualTo(1);
        ArgumentCaptor<NewsArticle> saved = ArgumentCaptor.forClass(NewsArticle.class);
        verify(repository).save(saved.capture());
        NewsArticle a = saved.getValue();
        assertThat(a.getTitle()).isEqualTo("Fed holds rates");
        assertThat(a.getUrl()).isEqualTo("https://example.com/fed");
        assertThat(a.getImageUrl()).isEqualTo("https://img.example.com/fed.jpg");
        assertThat(a.getSummary()).isEqualTo("Rates & more details"); // 태그 제거+엔티티 복원
        assertThat(a.getSource()).isEqualTo("MarketWatch");
        assertThat(a.getCategory()).isEqualTo(NewsCategory.MARKETS);
        assertThat(a.getPublishedAt()).isEqualTo(Instant.parse("2026-07-18T00:00:00Z"));
        assertThat(a.getFetchedAt()).isAfter(Instant.parse("2026-07-18T00:00:00Z"));
    }

    @Test
    void skipsEntriesWithoutImageDuplicateUrlOrMissingFields() {
        when(repository.existsByUrl("https://example.com/dup")).thenReturn(true);
        SyndFeed feed = feedWith(
                entry("No image entry", "https://example.com/no-image", null, null),
                entry("Already stored", "https://example.com/dup", null, "https://img/x.jpg"),
                entry("No link", "", null, "https://img/y.jpg"),
                entry("", "https://example.com/no-title", null, "https://img/z.jpg"));
        NewsFetchService service = new NewsFetchService(repository, singleFeedLoader(feed));

        assertThat(service.refresh()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void extractsImageFromMediaContentAndMediaThumbnail() throws Exception {
        // MarketWatch/Yahoo(media:content), Accounting Today(media:thumbnail) 실포맷 재현
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                  <channel><title>t</title>
                    <item>
                      <title>content style</title>
                      <link>https://example.com/a</link>
                      <media:content url="https://img.example.com/content.jpg"
                                     medium="image" type="image/jpeg"/>
                    </item>
                    <item>
                      <title>thumbnail style</title>
                      <link>https://example.com/b</link>
                      <media:thumbnail url="https://img.example.com/thumb.jpg"/>
                    </item>
                  </channel>
                </rss>
                """;
        SyndFeed feed = new SyndFeedInput().build(new StringReader(xml));

        assertThat(NewsFetchService.extractImageUrl(feed.getEntries().get(0)))
                .isEqualTo("https://img.example.com/content.jpg");
        assertThat(NewsFetchService.extractImageUrl(feed.getEntries().get(1)))
                .isEqualTo("https://img.example.com/thumb.jpg");
    }

    @Test
    void prunesBeyondKeepLimitPerCategory() {
        List<NewsArticle> many = IntStream.range(0, NewsFetchService.KEEP_PER_CATEGORY + 5)
                .mapToObj(i -> new NewsArticle("MarketWatch", NewsCategory.MARKETS, "t" + i, null,
                        "https://example.com/" + i, "https://img/" + i,
                        Instant.now(), Instant.now()))
                .toList();
        when(repository.findByCategoryOrderByPublishedAtDescIdDesc(NewsCategory.MARKETS))
                .thenReturn(many);
        NewsFetchService service = new NewsFetchService(repository, singleFeedLoader(feedWith()));

        service.refresh();

        ArgumentCaptor<List<NewsArticle>> deleted = ArgumentCaptor.forClass(List.class);
        verify(repository).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).hasSize(5); // 45(9장×5페이지) 초과분만 삭제
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
