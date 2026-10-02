package net.modtale.service.finance;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Strict adapter for standalone Stripe service-fee transactions, not payment fees or principal. */
final class StripeCostEvidence {
    private StripeCostEvidence() {}
    record Snapshot(String transactionId, String currency, String type, String reportingCategory, String source,
            long amount, long fee, long net, long cost, Instant created, Instant available) {
        String digest(String account, boolean testMode) {
            try {
                var fields = List.of(account, Boolean.toString(testMode), transactionId, currency, type, reportingCategory,
                        source == null ? "" : source, Long.toString(amount), Long.toString(fee), Long.toString(net),
                        Long.toString(created.getEpochSecond()), Long.toString(available.getEpochSecond()));
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", fields).getBytes(StandardCharsets.UTF_8)));
            } catch (Exception impossible) { throw new IllegalStateException("Could not hash cost evidence."); }
        }
    }
    static Snapshot parse(String expectedId, Map<String, Object> data, Instant now) {
        if (!Objects.equals(expectedId, data.get("id")) || !"balance_transaction".equals(data.get("object"))) invalid();
        // Historical adjustments can contain principal. FX/tax/other fee categories need their own reviewed adapters.
        if (!"stripe_fee".equals(data.get("type")) || !"fee".equals(data.get("reporting_category"))
                || !"available".equals(data.get("status"))) invalid();
        String currency = data.get("currency") instanceof String text ? text : "";
        if (!currency.matches("[a-z]{3}")) invalid();
        try { if (Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getDefaultFractionDigits() < 0) invalid(); } catch (IllegalArgumentException unsupported) { invalid(); }
        if (!data.containsKey("source")) invalid();
        String source = null;
        if (data.get("source") != null) {
            if (!(data.get("source") instanceof String text) || !text.matches("[A-Za-z][A-Za-z0-9_]{0,119}")) invalid();
            source = (String) data.get("source");
            if (source.matches("(?:ch|py|re|dp|du|tr|po|pi|in)_.+")) invalid();
        }
        long amount = integer(data.get("amount")), fee = integer(data.get("fee")), net = integer(data.get("net"));
        if (amount == 0 || amount < -1_000_000_000_000L || amount > 1_000_000_000_000L || fee != 0 || net != amount
                || !(data.get("fee_details") instanceof List<?> details) || !details.isEmpty()) invalid();
        long created = integer(data.get("created")), available = integer(data.get("available_on"));
        if (created <= 0 || available <= 0 || created > now.getEpochSecond() + 300 || available > now.getEpochSecond()) invalid();
        return new Snapshot(expectedId, currency, "stripe_fee", "fee", source, amount, fee, net, -net,
                Instant.ofEpochSecond(created), Instant.ofEpochSecond(available));
    }
    private static long integer(Object value) {
        try {
            if (value instanceof Long l) return l;
            if (value instanceof Integer i) return i.longValue();
            if (value instanceof BigInteger b) return b.longValueExact();
        } catch (ArithmeticException overflow) { invalid(); }
        invalid(); return 0;
    }
    private static void invalid() { throw new IllegalArgumentException("Expected settled standalone Stripe service-fee evidence. Principal, payment/refund/dispute fees, pending amounts and unsupported categories require their existing reconciliation paths or separate review."); }
}
