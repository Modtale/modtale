package net.modtale.service.finance;

/** Provider object IDs are scoped to the platform account and test/live mode in our ledger. */
public final class FinanceSourceKey {
    private FinanceSourceKey() {}
    public static String stripe(boolean testMode, String accountId, String objectId) {
        if (accountId == null || !accountId.startsWith("acct_") || objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("A verified provider account and source object are required.");
        }
        return "stripe:" + (testMode ? "test:" : "live:") + accountId + ":" + objectId;
    }
}
