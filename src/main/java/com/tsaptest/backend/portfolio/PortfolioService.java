package com.tsaptest.backend.portfolio;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final ActivityEntryRepository activityEntryRepository;
    private final PortfolioSnapshotRepository snapshotRepository;

    public PortfolioService(AccountRepository accountRepository,
                            HoldingRepository holdingRepository,
                            ActivityEntryRepository activityEntryRepository,
                            PortfolioSnapshotRepository snapshotRepository) {
        this.accountRepository = accountRepository;
        this.holdingRepository = holdingRepository;
        this.activityEntryRepository = activityEntryRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * 고객별 포트폴리오 전체를 한 번에 조립한다.
     * Caffeine 캐시에 5분간 보관되므로(application.yml spec 참조) 같은 고객의
     * 반복 조회는 DB를 다시 치지 않는다. asOf가 캐시 적재 시각으로 고정되어
     * 화면의 "Values as of ..." 표시가 스냅샷 시각을 그대로 반영한다.
     */
    @Cacheable(cacheNames = "portfolio", key = "#userId")
    @Transactional(readOnly = true)
    public PortfolioResponse loadPortfolio(Long userId) {
        List<Account> accounts = accountRepository.findByUserIdOrderByDisplayOrder(userId);
        List<Holding> holdings = holdingRepository.findByUserIdOrderByDisplayOrder(userId);
        List<ActivityEntry> activity = activityEntryRepository.findByUserIdOrderByDisplayOrder(userId);
        PortfolioSnapshot snapshot = snapshotRepository.findByUserId(userId).orElse(null);

        long totalValue = accounts.stream().mapToLong(Account::getValue).sum();

        PortfolioResponse.Summary summary = new PortfolioResponse.Summary(
                totalValue,
                snapshot != null ? snapshot.getDayChangeLabel() : "",
                snapshot != null ? snapshot.getDayChangeMaskedLabel() : "",
                snapshot != null ? snapshot.getYtdReturnLabel() : "",
                snapshot != null ? snapshot.getIncomeYtd() : 0,
                snapshot != null ? snapshot.getCashAvailable() : 0,
                snapshot != null ? snapshot.getCashApyLabel() : "");

        List<String> accountFilters = new ArrayList<>();
        accountFilters.add("All");
        accounts.forEach(a -> accountFilters.add(a.getShortName()));

        return new PortfolioResponse(
                Instant.now(),
                summary,
                accounts.stream().map(a -> new PortfolioResponse.AccountDto(
                        a.getName(), a.getSubLabel(), a.getValue(), a.getYtdLabel(), a.isUp())).toList(),
                activity.stream().map(e -> new PortfolioResponse.ActivityDto(
                        e.getLabel(), e.getDateLabel(), e.getAmountLabel(),
                        e.getMaskedAmountLabel(), e.getColorLabel())).toList(),
                holdings.stream().map(h -> new PortfolioResponse.HoldingDto(
                        h.getTicker(), h.getSecurityName(), h.getAccountLabel(), h.getQtyLabel(),
                        h.getPrice(), h.getValue(), h.getDayLabel(), h.getDayTrend(),
                        h.getGainLabel(), h.isGainUp())).toList(),
                accountFilters);
    }
}
