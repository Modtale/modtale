package net.modtale.model.dto.request.finance;

import java.time.LocalDate;
import java.math.BigDecimal;

public record StageAdSettlementRequest(String provider, String providerAccount, String reportId,
        String depositId, String currency, LocalDate from, LocalDate through,
        BigDecimal reportedCollectedCents, String reportSha256) {
    public StageAdSettlementRequest {
        if (reportedCollectedCents != null) reportedCollectedCents = reportedCollectedCents.stripTrailingZeros();
    }
    public StageAdSettlementRequest(String provider, String providerAccount, String reportId, String depositId,
            String currency, LocalDate from, LocalDate through, long reportedCollectedCents, String reportSha256) {
        this(provider, providerAccount, reportId, depositId, currency, from, through, BigDecimal.valueOf(reportedCollectedCents), reportSha256);
    }
    public long exactCents() {
        try { return reportedCollectedCents.longValueExact(); }
        catch (ArithmeticException | NullPointerException invalid) { throw new IllegalArgumentException("Reported collected cents must be a whole integer."); }
    }
}
