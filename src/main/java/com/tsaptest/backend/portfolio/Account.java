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

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    /** 목록에 표시되는 부제 (예: "Discretionary · ···8241") */
    @Column(nullable = false)
    private String subLabel;

    /** 홀딩스 필터 등에서 쓰는 짧은 이름 (예: "Family Trust") */
    @Column(nullable = false)
    private String shortName;

    /** 계좌 평가액 (달러, 정수) */
    @Column(nullable = false)
    private long value;

    /** 수익률 표시 문구 (예: "+9.2% YTD", "4.1% APY") */
    @Column(nullable = false)
    private String ytdLabel;

    @Column(nullable = false)
    private boolean up;

    @Column(nullable = false)
    private int displayOrder;

    protected Account() {
    }

    public Account(User user, String name, String subLabel, String shortName,
                   long value, String ytdLabel, boolean up, int displayOrder) {
        this.user = user;
        this.name = name;
        this.subLabel = subLabel;
        this.shortName = shortName;
        this.value = value;
        this.ytdLabel = ytdLabel;
        this.up = up;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getSubLabel() {
        return subLabel;
    }

    public String getShortName() {
        return shortName;
    }

    public long getValue() {
        return value;
    }

    public String getYtdLabel() {
        return ytdLabel;
    }

    public boolean isUp() {
        return up;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
