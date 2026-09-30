package net.modtale.service.finance;

import net.modtale.model.finance.FinanceLedgerEntry;

/** Provider events are not themselves settled revenue; moving money is not new revenue. */
public final class FinanceLedgerRules {
    private FinanceLedgerRules() {}

    public static boolean isReal(FinanceLedgerEntry entry) {
        if (entry.getMetadata() != null && ("true".equals(entry.getMetadata().get("simulated"))
                || "true".equals(entry.getMetadata().get("testMode")))) return false;
        return entry.getStripeReference() == null || !entry.getStripeReference().startsWith("sim_");
    }

    public static boolean isRevenue(FinanceLedgerEntry entry) {
        return entry.getType() == FinanceLedgerEntry.LedgerType.DONATION
                || entry.getType() == FinanceLedgerEntry.LedgerType.REFUND_ADJUSTMENT
                || entry.getType() == FinanceLedgerEntry.LedgerType.DISPUTE_ADJUSTMENT
                || entry.getType() == FinanceLedgerEntry.LedgerType.DISPUTE_FEE_ADJUSTMENT
                || entry.getType() == FinanceLedgerEntry.LedgerType.DISPUTE_REVERSAL
                || entry.getType() == FinanceLedgerEntry.LedgerType.AD_CLICK
                || entry.getType() == FinanceLedgerEntry.LedgerType.AD_IMPRESSION;
    }

    public static boolean isRecognizedRevenue(FinanceLedgerEntry entry) {
        return isReal(entry) && isSettledRevenue(entry);
    }

    public static boolean isInMode(FinanceLedgerEntry entry, boolean testMode) {
        if (entry.getMetadata() != null && "true".equals(entry.getMetadata().get("simulated"))) return false;
        return testMode ? entry.getMetadata() != null && "true".equals(entry.getMetadata().get("testMode")) : isReal(entry);
    }

    public static boolean isSettledRevenue(FinanceLedgerEntry entry) {
        return isRevenue(entry)
                && (entry.getStatus() == FinanceLedgerEntry.EntryStatus.AVAILABLE
                || entry.getStatus() == FinanceLedgerEntry.EntryStatus.PAID)
                && entry.getMetadata() != null && "settled".equals(entry.getMetadata().get("settlement"));
    }
}
