package net.modtale.service.finance;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class StripeCostEvidenceTest {
    static Map<String, Object> fee() {
        Map<String, Object> data = new HashMap<>(Map.of("id", "txn_fee", "object", "balance_transaction", "type", "stripe_fee", "reporting_category", "fee", "status", "available", "currency", "usd", "amount", -200, "fee", 0, "net", -200, "created", 1700000000));
        data.put("available_on", 1700000000); data.put("source", null); data.put("fee_details", List.of()); return data;
    }
    @Test void standaloneCostKeepsSignedAmountsAndDoesNotInventCreatorAttribution() {
        var snapshot = StripeCostEvidence.parse("txn_fee", fee(), Instant.now());
        assertEquals(200, snapshot.cost()); assertEquals(-200, snapshot.net()); assertNull(snapshot.source());
        var refund = fee(); refund.put("amount", 200); refund.put("net", 200);
        assertEquals(-200, StripeCostEvidence.parse("txn_fee", refund, Instant.now()).cost());
    }
    @ParameterizedTest @ValueSource(strings = {"charge", "payment", "refund", "payment_refund", "adjustment", "transfer", "payout", "application_fee", "application_fee_refund", "stripe_fx_fee", "tax_fee", "unknown"})
    void principalAndUnsupportedCategoriesCannotBecomeExtraFees(String type) {
        var value = fee(); value.put("type", type);
        assertThrows(IllegalArgumentException.class, () -> StripeCostEvidence.parse("txn_fee", value, Instant.now()));
    }
    @ParameterizedTest @ValueSource(strings = {"ch_charge", "py_payment", "re_refund", "dp_dispute", "du_dispute", "tr_transfer", "po_payout", "pi_payment", "in_invoice"})
    void existingFinancialSourcesCannotBeImportedAgainAsServiceCosts(String source) {
        var value = fee(); value.put("source", source);
        assertThrows(IllegalArgumentException.class, () -> StripeCostEvidence.parse("txn_fee", value, Instant.now()));
    }
    @Test void malformedFractionalMissingOrInconsistentAmountsAreRejected() {
        for (Object invalid : List.of(-1.5, "-200", 0, Long.MIN_VALUE, Long.MAX_VALUE)) {
            var value = fee(); value.put("amount", invalid); assertThrows(IllegalArgumentException.class, () -> StripeCostEvidence.parse("txn_fee", value, Instant.now()));
        }
        for (String missing : List.of("amount", "fee", "net", "source", "fee_details", "created", "available_on")) {
            var value = fee(); value.remove(missing); assertThrows(IllegalArgumentException.class, () -> StripeCostEvidence.parse("txn_fee", value, Instant.now()), missing);
        }
        for (var mismatch : Map.<String, Object>of("fee", 1, "net", -201, "currency", "xxx", "status", "pending", "reporting_category", "charge", "id", "txn_other", "object", "charge", "fee_details", List.of(Map.of("amount", 1))).entrySet()) {
            var value = fee(); value.put(mismatch.getKey(), mismatch.getValue());
            assertThrows(IllegalArgumentException.class, () -> StripeCostEvidence.parse("txn_fee", value, Instant.now()), mismatch.getKey());
        }
    }
    @Test void futureAvailabilityCannotBeRecordedAsSettled() {
        var value = fee(); value.put("available_on", Instant.now().plusSeconds(3600).getEpochSecond());
        assertThrows(IllegalArgumentException.class, () -> StripeCostEvidence.parse("txn_fee", value, Instant.now()));
    }
    @Test void digestBindsAccountModeAndExactFinancialEvidence() {
        var snapshot = StripeCostEvidence.parse("txn_fee", fee(), Instant.now());
        assertEquals(snapshot.digest("acct_one", true), snapshot.digest("acct_one", true));
        assertNotEquals(snapshot.digest("acct_one", true), snapshot.digest("acct_two", true));
        assertNotEquals(snapshot.digest("acct_one", true), snapshot.digest("acct_one", false));
        var altered = fee(); altered.put("amount", -201); altered.put("net", -201);
        assertNotEquals(snapshot.digest("acct_one", true), StripeCostEvidence.parse("txn_fee", altered, Instant.now()).digest("acct_one", true));
    }
}
