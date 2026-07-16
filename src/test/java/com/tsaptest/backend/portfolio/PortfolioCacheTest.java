package com.tsaptest.backend.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * "5분 지연 허용" 캐시 규칙: 같은 사용자의 반복 조회는 DB를 다시 치지 않고,
 * asOf(캐시 적재 시각)가 고정된 채 재사용된다. 사용자별로 캐시 키가 분리된다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PortfolioCacheTest.Config.class)
class PortfolioCacheTest {

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            // 운영은 application.yml의 Caffeine spec(TTL 5분)을 쓰지만,
            // 여기서는 @Cacheable 동작 자체만 검증하므로 기본 캐시로 충분
            return new CaffeineCacheManager("portfolio");
        }

        @Bean
        AccountRepository accountRepository() {
            return mock(AccountRepository.class);
        }

        @Bean
        HoldingRepository holdingRepository() {
            return mock(HoldingRepository.class);
        }

        @Bean
        ActivityEntryRepository activityEntryRepository() {
            return mock(ActivityEntryRepository.class);
        }

        @Bean
        PortfolioSnapshotRepository portfolioSnapshotRepository() {
            return mock(PortfolioSnapshotRepository.class);
        }

        @Bean
        PortfolioService portfolioService(AccountRepository accounts,
                                          HoldingRepository holdings,
                                          ActivityEntryRepository activity,
                                          PortfolioSnapshotRepository snapshots) {
            return new PortfolioService(accounts, holdings, activity, snapshots);
        }
    }

    @Autowired
    private PortfolioService service;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        cacheManager.getCache("portfolio").clear();
        reset(accountRepository);
    }

    @Test
    void repeatedLoadForSameUserHitsDbOnce() {
        PortfolioResponse first = service.loadPortfolio(1L);
        PortfolioResponse second = service.loadPortfolio(1L);

        verify(accountRepository, times(1)).findByUserIdOrderByDisplayOrder(1L);
        // 캐시에서 같은 객체가 그대로 나온다 → asOf도 적재 시각으로 고정
        assertThat(second).isSameAs(first);
        assertThat(second.asOf()).isEqualTo(first.asOf());
    }

    @Test
    void differentUsersAreCachedSeparately() {
        service.loadPortfolio(1L);
        service.loadPortfolio(2L);
        service.loadPortfolio(2L);

        // 사용자마다 키가 분리 — 1번 사용자 캐시가 2번에게 새어가지 않는다
        verify(accountRepository, times(1)).findByUserIdOrderByDisplayOrder(1L);
        verify(accountRepository, times(1)).findByUserIdOrderByDisplayOrder(2L);
    }
}
