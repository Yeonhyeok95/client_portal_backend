package com.tsaptest.backend.portfolio;

import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 데모 고객(client@tsaptest.com)의 포트폴리오 시딩.
 * 원본은 frontend/lib/portalData.ts에 하드코딩되어 있던 더미 데이터.
 * DemoUserSeeder(@Order(1)) 다음에 실행되며, 계좌가 이미 있으면 건너뛴다.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true")
public class PortfolioSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSeeder.class);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final ActivityEntryRepository activityEntryRepository;
    private final PortfolioSnapshotRepository snapshotRepository;

    public PortfolioSeeder(UserRepository userRepository,
                           AccountRepository accountRepository,
                           HoldingRepository holdingRepository,
                           ActivityEntryRepository activityEntryRepository,
                           PortfolioSnapshotRepository snapshotRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.holdingRepository = holdingRepository;
        this.activityEntryRepository = activityEntryRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User client = userRepository.findByEmailIgnoreCase("client@tsaptest.com").orElse(null);
        if (client == null) {
            return;
        }

        // 기존 DB의 이전 시더 이름을 포털 인물과 일치하도록 정정
        if (!"Eleanor Whitfield".equals(client.getDisplayName())) {
            client.setDisplayName("Eleanor Whitfield");
            userRepository.save(client);
        }
        userRepository.findByEmailIgnoreCase("advisor@tsaptest.com").ifPresent(advisor -> {
            if (!"Marcus Bell".equals(advisor.getDisplayName())) {
                advisor.setDisplayName("Marcus Bell");
                userRepository.save(advisor);
            }
        });

        if (accountRepository.countByUserId(client.getId()) > 0) {
            return;
        }

        accountRepository.saveAll(List.of(
                new Account(client, "Whitfield Family Trust", "Discretionary · ···8241",
                        "Family Trust", 7_214_850, "+9.2% YTD", true, 1),
                new Account(client, "Retirement IRA", "Discretionary · ···5507",
                        "Retirement IRA", 2_904_607, "+7.8% YTD", true, 2),
                new Account(client, "Municipal Bond Portfolio", "Laddered · ···1193",
                        "Municipal", 1_812_936, "+3.9% YTD", true, 3),
                new Account(client, "Cash Reserve", "Treasury program · ···6620",
                        "Cash Reserve", 914_120, "4.1% APY", true, 4)));

        holdingRepository.saveAll(List.of(
                new Holding(client, "VTI", "Vanguard Total Stock Market ETF", "Family Trust",
                        "11,650", new BigDecimal("312.44"), 3_639_926, "+0.4%", 1, "+18.2%", true, 1),
                new Holding(client, "MSFT", "Microsoft Corporation", "Family Trust",
                        "2,400", new BigDecimal("512.30"), 1_229_520, "+0.8%", 1, "+34.1%", true, 2),
                new Holding(client, "VXUS", "Vanguard Total International ETF", "Family Trust",
                        "16,900", new BigDecimal("71.08"), 1_201_252, "−0.2%", -1, "+9.6%", true, 3),
                new Holding(client, "BRK.B", "Berkshire Hathaway Class B", "Family Trust",
                        "1,068", new BigDecimal("498.15"), 532_024, "+0.1%", 1, "+12.4%", true, 4),
                new Holding(client, "PCF III", "Private Credit Fund III", "Family Trust",
                        "—", null, 612_128, "—", 0, "+11.2%", true, 5),
                new Holding(client, "AGG", "iShares Core U.S. Aggregate Bond", "Retirement IRA",
                        "17,200", new BigDecimal("102.61"), 1_764_892, "+0.1%", 1, "+4.2%", true, 6),
                new Holding(client, "SCHD", "Schwab U.S. Dividend Equity ETF", "Retirement IRA",
                        "38,700", new BigDecimal("29.45"), 1_139_715, "+0.3%", 1, "+8.8%", true, 7),
                new Holding(client, "VWIUX", "Vanguard Intermediate Muni Fund", "Municipal",
                        "84,100", new BigDecimal("14.32"), 1_204_312, "0.0%", 0, "+3.6%", true, 8),
                new Holding(client, "MUB", "iShares National Muni Bond ETF", "Municipal",
                        "5,540", new BigDecimal("109.86"), 608_624, "+0.1%", 1, "+3.1%", true, 9),
                new Holding(client, "T-BILLS", "U.S. Treasury Bill Ladder", "Cash Reserve",
                        "—", null, 914_120, "—", 0, "+2.0%", true, 10)));

        activityEntryRepository.saveAll(List.of(
                new ActivityEntry(client, "Dividend received — VTI", "Jul 1",
                        "+$6,214", "+ ····", "green", 1),
                new ActivityEntry(client, "Municipal coupon payment", "Jun 30",
                        "+$18,900", "+ ····", "green", 2),
                new ActivityEntry(client, "Quarterly advisory fee", "Jun 30",
                        "−$8,050", "− ····", "gray", 3),
                new ActivityEntry(client, "Rebalance — trimmed MSFT", "Jun 24",
                        "4 trades", "4 trades", "gray", 4)));

        snapshotRepository.save(new PortfolioSnapshot(client,
                "+$41,286 (+0.32%)", "+ ······ (+0.32%)", "+8.4%",
                196_400, 914_120, "4.1% APY"));

        log.info("Seeded portfolio data for {}", client.getEmail());
    }
}
