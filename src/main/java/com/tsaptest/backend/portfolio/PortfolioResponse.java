package com.tsaptest.backend.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioResponse(
        Instant asOf,
        Summary summary,
        List<AccountDto> accounts,
        List<ActivityDto> activity,
        List<HoldingDto> holdings,
        List<String> accountFilters) {

    public record Summary(
            long totalValue,
            String dayChange,
            String dayChangeMasked,
            String ytdReturn,
            long incomeYtd,
            long cashAvailable,
            String cashApy) {
    }

    public record AccountDto(String name, String sub, long value, String ytd, boolean up) {
    }

    public record ActivityDto(String label, String date, String amount,
                              String maskedAmount, String color) {
    }

    public record HoldingDto(String ticker, String name, String account, String qty,
                             BigDecimal price, long value, String day, int dayTrend,
                             String gain, boolean gainUp) {
    }
}
