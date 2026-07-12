package com.tsaptest.backend.portfolio;

import com.tsaptest.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * 계좌 합계로 계산할 수 없는 포트폴리오 요약 수치 (사용자당 1행).
 */
@Entity
@Table(name = "portfolio_snapshots")
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    /** 일간 변동 문구 (예: "+$41,286 (+0.32%)") */
    @Column(nullable = false)
    private String dayChangeLabel;

    /** 마스킹 모드 문구 (예: "+ ······ (+0.32%)") */
    @Column(nullable = false)
    private String dayChangeMaskedLabel;

    /** YTD 수익률 문구 (예: "+8.4%") */
    @Column(nullable = false)
    private String ytdReturnLabel;

    @Column(nullable = false)
    private long incomeYtd;

    @Column(nullable = false)
    private long cashAvailable;

    /** 현금 수익률 문구 (예: "4.1% APY") */
    @Column(nullable = false)
    private String cashApyLabel;

    protected PortfolioSnapshot() {
    }

    public PortfolioSnapshot(User user, String dayChangeLabel, String dayChangeMaskedLabel,
                             String ytdReturnLabel, long incomeYtd, long cashAvailable,
                             String cashApyLabel) {
        this.user = user;
        this.dayChangeLabel = dayChangeLabel;
        this.dayChangeMaskedLabel = dayChangeMaskedLabel;
        this.ytdReturnLabel = ytdReturnLabel;
        this.incomeYtd = incomeYtd;
        this.cashAvailable = cashAvailable;
        this.cashApyLabel = cashApyLabel;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getDayChangeLabel() {
        return dayChangeLabel;
    }

    public String getDayChangeMaskedLabel() {
        return dayChangeMaskedLabel;
    }

    public String getYtdReturnLabel() {
        return ytdReturnLabel;
    }

    public long getIncomeYtd() {
        return incomeYtd;
    }

    public long getCashAvailable() {
        return cashAvailable;
    }

    public String getCashApyLabel() {
        return cashApyLabel;
    }
}
