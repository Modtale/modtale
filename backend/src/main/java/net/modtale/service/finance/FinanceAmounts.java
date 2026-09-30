package net.modtale.service.finance;

/** Integer-only money validation for the currently supported two-decimal USD amounts. */
public final class FinanceAmounts {
    public static final long MIN_SUPPORT_CENTS = 100;
    public static final long MAX_SUPPORT_CENTS = 100000;

    private FinanceAmounts() {}

    public static long validateSupportAmount(long cents) {
        if (cents < MIN_SUPPORT_CENTS || cents > MAX_SUPPORT_CENTS) {
            throw new IllegalArgumentException("Enter an amount between 1.00 and 1,000.00 USD.");
        }
        return cents;
    }

    public static long share(long cents, int basisPoints) {
        if (cents < 0 || basisPoints < 0 || basisPoints > 10000) {
            throw new IllegalArgumentException("Invalid revenue allocation.");
        }
        return Math.addExact(Math.multiplyExact(cents, basisPoints), 5000) / 10000;
    }
}
