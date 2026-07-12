package com.tsaptest.backend.portfolio;

import com.tsaptest.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String securityName;

    /** 소속 계좌의 shortName (예: "Family Trust") */
    @Column(nullable = false)
    private String accountLabel;

    /** 수량 표시 문구 (예: "11,650", 사모펀드는 "—") */
    @Column(nullable = false)
    private String qtyLabel;

    /** 단가 — 가격이 없는 자산(사모펀드 등)은 null */
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private long value;

    /** 일간 변동 문구 (예: "+0.4%", "—") */
    @Column(nullable = false)
    private String dayLabel;

    /** 1 상승 / 0 보합 / -1 하락 */
    @Column(nullable = false)
    private int dayTrend;

    @Column(nullable = false)
    private String gainLabel;

    @Column(nullable = false)
    private boolean gainUp;

    @Column(nullable = false)
    private int displayOrder;

    protected Holding() {
    }

    public Holding(User user, String ticker, String securityName, String accountLabel,
                   String qtyLabel, BigDecimal price, long value, String dayLabel,
                   int dayTrend, String gainLabel, boolean gainUp, int displayOrder) {
        this.user = user;
        this.ticker = ticker;
        this.securityName = securityName;
        this.accountLabel = accountLabel;
        this.qtyLabel = qtyLabel;
        this.price = price;
        this.value = value;
        this.dayLabel = dayLabel;
        this.dayTrend = dayTrend;
        this.gainLabel = gainLabel;
        this.gainUp = gainUp;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTicker() {
        return ticker;
    }

    public String getSecurityName() {
        return securityName;
    }

    public String getAccountLabel() {
        return accountLabel;
    }

    public String getQtyLabel() {
        return qtyLabel;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getValue() {
        return value;
    }

    public String getDayLabel() {
        return dayLabel;
    }

    public int getDayTrend() {
        return dayTrend;
    }

    public String getGainLabel() {
        return gainLabel;
    }

    public boolean isGainUp() {
        return gainUp;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
