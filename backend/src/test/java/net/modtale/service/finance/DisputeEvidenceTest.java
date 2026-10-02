package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DisputeEvidenceTest {
    private Map<String, Object> balance(String id, long amount, long fee) { return Map.of("id", id, "source", "dp_case", "type", "adjustment", "currency", "usd", "status", "available", "amount", amount, "fee", fee, "net", amount - fee); }
    private DisputeEvidence read(Object balances) { return DisputeEvidence.read("acct_platform", true, "dp_case", "ch_source", "usd", 10000, "won", balances); }
    @Test void deduplicatesBySourceIdAndSeparatesPrincipalFromFees() {
        var loss = balance("txn_loss", -10000, 1500); var returned = balance("txn_return", 10000, -1500);
        var evidence = read(List.of(loss, loss, returned)); assertTrue(evidence.ready()); assertEquals(0, evidence.principalMovementCents());
        assertEquals(0, evidence.actualFeeCents()); assertEquals(10000, evidence.returnedPrincipalCents()); assertEquals(2, evidence.balances().size());
        assertEquals(evidence.digest(), read(List.of(returned, loss)).digest());
    }
    @Test void conflictingDuplicatesAndUnlinkedOrUnsettledMovementsCannotAuthorizeRelease() {
        var valid = balance("txn_loss", -10000, 1500);
        assertFalse(read(List.of(valid, balance("txn_loss", -9999, 1500))).ready());
        for (var change : List.of(Map.<String,Object>of("source", "dp_other"), Map.<String,Object>of("type", "charge"), Map.<String,Object>of("currency", "eur"), Map.<String,Object>of("status", "pending"),
                Map.<String,Object>of("net", -10000), Map.<String,Object>of("fee", 1.5), Map.<String,Object>of("amount", "-10000"), Map.<String,Object>of("id", ""))) {
            var altered = new HashMap<>(valid); altered.putAll(change); assertFalse(read(List.of(altered)).ready(), change.toString());
        }
    }
    @Test void missingEvidenceIsDifferentFromACompleteEmptyNonFinancialWarning() {
        assertFalse(read(null).ready()); assertFalse(read(Map.of()).ready()); assertTrue(read(List.of()).ready());
        var invalid = new HashMap<>(balance("txn_loss", -10000, 1500)); invalid.remove("fee");
        assertFalse(read(List.of(invalid)).ready()); assertNull(read(List.of(invalid)).actualFeeCents());
    }
}
