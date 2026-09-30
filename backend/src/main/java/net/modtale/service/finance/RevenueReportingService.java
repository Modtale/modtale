package net.modtale.service.finance;

import net.modtale.model.finance.DonationIntent;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.finance.PlatformFinanceSettings;
import net.modtale.repository.finance.DonationIntentRepository;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RevenueReportingService {

    @Autowired private EarningsAccountService financeAccountService;
    @Autowired private FinanceLedgerEntryRepository ledgerRepository;
    @Autowired private DonationIntentRepository donationIntentRepository;
    @Autowired private RevenueOpsSupport core;

    public List<Map<String, Object>> getPublicDailyRevenue(int days) {
        int safeDays = Math.max(1, Math.min(365, days));
        LocalDate start = LocalDate.now().minusDays(safeDays - 1);
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = LocalDateTime.now();

        List<FinanceLedgerEntry> entries = ledgerRepository.findByCreatedAtBetween(startAt, endAt);
        Map<LocalDate, long[]> buckets = new HashMap<>();

        for (FinanceLedgerEntry entry : entries) {
            if (entry.getCreatedAt() == null || !FinanceLedgerRules.isRecognizedRevenue(entry)) continue;
            LocalDate date = entry.getCreatedAt().toLocalDate();
            long[] sums = buckets.computeIfAbsent(date, key -> new long[4]);
            sums[0] += Math.max(0, entry.getGrossCents());
            sums[1] += Math.max(0, entry.getCreatorCents());
            sums[2] += Math.max(0, entry.getPlatformCents());
            sums[3] += Math.max(0, entry.getProcessorFeeCents() == null ? 0 : entry.getProcessorFeeCents());
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(LocalDate.now()); day = day.plusDays(1)) {
            long[] sums = buckets.getOrDefault(day, new long[4]);
            Map<String, Object> item = new HashMap<>();
            item.put("date", day.format(RevenueOpsSupport.DATE_FMT));
            item.put("grossCents", sums[0]);
            item.put("creatorCents", sums[1]);
            item.put("platformCents", sums[2]);
            item.put("processorFeeCents", sums[3]);
            response.add(item);
        }

        return response;
    }

    /** Earned creator funds are never forfeited. Unclaimed-property handling requires a separate reviewed process. */
    @Deprecated
    public void expireCreatorFunds() {
        // Intentionally no database writes and no scheduled invocation.
    }
}
