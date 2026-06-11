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
            if (entry.getCreatedAt() == null) continue;
            LocalDate date = entry.getCreatedAt().toLocalDate();
            long[] sums = buckets.computeIfAbsent(date, key -> new long[3]);
            sums[0] += Math.max(0, entry.getGrossCents());
            sums[1] += Math.max(0, entry.getCreatorCents());
            sums[2] += Math.max(0, entry.getPlatformCents());
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(LocalDate.now()); day = day.plusDays(1)) {
            long[] sums = buckets.getOrDefault(day, new long[3]);
            Map<String, Object> item = new HashMap<>();
            item.put("date", day.format(RevenueOpsSupport.DATE_FMT));
            item.put("grossCents", sums[0]);
            item.put("creatorCents", sums[1]);
            item.put("platformCents", sums[2]);
            response.add(item);
        }

        return response;
    }

    @Scheduled(cron = "0 30 0 * * *")
    public void expireCreatorFunds() {
        PlatformFinanceSettings settings = financeAccountService.getSettings();
        LocalDateTime now = LocalDateTime.now();

        List<FinanceLedgerEntry> expiringEntries = ledgerRepository.findByStatusAndExpiresAtBefore(FinanceLedgerEntry.EntryStatus.AVAILABLE, now);
        if (expiringEntries.isEmpty()) return;

        List<FinanceLedgerEntry> updates = new ArrayList<>();
        List<FinanceLedgerEntry> transferEntries = new ArrayList<>();

        for (FinanceLedgerEntry entry : expiringEntries) {
            if (entry.getCreatorCents() <= 0) continue;

            entry.setStatus(FinanceLedgerEntry.EntryStatus.EXPIRED);
            entry.setCompletedAt(now);
            updates.add(entry);

            FinanceLedgerEntry transfer = new FinanceLedgerEntry();
            transfer.setType(FinanceLedgerEntry.LedgerType.EXPIRED_TRANSFER);
            transfer.setProjectId(entry.getProjectId());
            transfer.setGrossCents(entry.getCreatorCents());
            transfer.setCreatorCents(0);
            transfer.setPlatformCents(entry.getCreatorCents());
            transfer.setCurrency(settings.getCurrency());
            transfer.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE);
            transfer.setCreatedAt(now);
            transfer.setAvailableAt(now);
            transfer.setExpiresAt(now.plusYears(50));
            transfer.setExternalReference(entry.getId());
            transfer.getMetadata().put("reason", "creator_funds_expired_after_365_days");
            transferEntries.add(transfer);
        }

        if (!updates.isEmpty()) ledgerRepository.saveAll(updates);
        if (!transferEntries.isEmpty()) ledgerRepository.saveAll(transferEntries);

        donationIntentRepository.findByStatusAndExpiresAtBefore(DonationIntent.DonationStatus.PENDING, now)
                .forEach(intent -> {
                    intent.setStatus(DonationIntent.DonationStatus.EXPIRED);
                    donationIntentRepository.save(intent);
                });
    }
}
