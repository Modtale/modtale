package net.modtale.service.finance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.modtale.model.finance.FinanceDisputeBalance;

/** Only unique, source-linked, same-currency available balance movements can support a financial release. */
public record DisputeEvidence(List<FinanceDisputeBalance> balances, Long principalMovementCents, Long actualFeeCents,
        long returnedPrincipalCents, boolean ready, String digest) {
    static DisputeEvidence read(String accountId, boolean testMode, String disputeId, String chargeId, String currency,
            long amount, String status, Object values) {
        Map<String, FinanceDisputeBalance> unique = new TreeMap<>(); boolean valid = values instanceof List<?>;
        if (values instanceof List<?> list) for (Object value : list) {
            if (!(value instanceof Map<?, ?> data) || !(data.get("id") instanceof String id)) { valid = false; continue; }
            if (!id.matches("txn_[A-Za-z0-9]+")) valid = false;
            String source = data.get("source") instanceof String direct ? direct : data.get("source") instanceof Map<?, ?> object && object.get("id") instanceof String idValue ? idValue : null;
            var balance = new FinanceDisputeBalance(id, source, string(data.get("type")), string(data.get("currency")), string(data.get("status")), number(data.get("amount")), number(data.get("fee")), number(data.get("net")));
            FinanceDisputeBalance previous = unique.putIfAbsent(id, balance);
            if (previous != null && !previous.equals(balance)) valid = false;
            if (!disputeId.equals(source) || !"adjustment".equals(balance.type()) || !currency.equals(balance.currency()) || !"available".equals(balance.status())
                    || balance.amount() == null || balance.fee() == null || balance.net() == null) valid = false;
            else try { if (Math.subtractExact(balance.amount(), balance.fee()) != balance.net()) valid = false; }
            catch (ArithmeticException overflow) { valid = false; }
        }
        long principal = 0, fees = 0, returned = 0;
        if (valid) try {
            for (FinanceDisputeBalance balance : unique.values()) {
                principal = Math.addExact(principal, balance.amount()); fees = Math.addExact(fees, balance.fee());
                if (balance.amount() > 0) returned = Math.addExact(returned, balance.amount());
            }
        } catch (ArithmeticException overflow) { valid = false; }
        String fingerprint = accountId + ":" + testMode + ":" + disputeId + ":" + chargeId + ":" + currency + ":" + amount + ":" + status + ":" + valid + ":" + unique.values();
        String digest;
        try { digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fingerprint.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return new DisputeEvidence(List.copyOf(unique.values()), valid ? principal : null, valid ? fees : null, valid ? returned : 0, valid, digest);
    }
    private static String string(Object value) { return value instanceof String text ? text : null; }
    private static Long number(Object value) {
        long parsed = PaymentAdjustmentService.number(value); return parsed == Long.MIN_VALUE ? null : parsed;
    }
}
