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
@Table(name = "activity")
public class ActivityEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String label;

    /** 표시용 날짜 문구 (예: "Jul 1") */
    @Column(nullable = false)
    private String dateLabel;

    /** 금액 표시 문구 (예: "+$6,214", "4 trades") */
    @Column(nullable = false)
    private String amountLabel;

    /** 마스킹 모드일 때 표시 문구 (예: "+ ····") */
    @Column(nullable = false)
    private String maskedAmountLabel;

    /** "green" | "gray" */
    @Column(nullable = false)
    private String colorLabel;

    @Column(nullable = false)
    private int displayOrder;

    protected ActivityEntry() {
    }

    public ActivityEntry(User user, String label, String dateLabel, String amountLabel,
                         String maskedAmountLabel, String colorLabel, int displayOrder) {
        this.user = user;
        this.label = label;
        this.dateLabel = dateLabel;
        this.amountLabel = amountLabel;
        this.maskedAmountLabel = maskedAmountLabel;
        this.colorLabel = colorLabel;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getLabel() {
        return label;
    }

    public String getDateLabel() {
        return dateLabel;
    }

    public String getAmountLabel() {
        return amountLabel;
    }

    public String getMaskedAmountLabel() {
        return maskedAmountLabel;
    }

    public String getColorLabel() {
        return colorLabel;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
