package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.modtale.model.finance.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_MONGO_URI", matches = ".+")
class DisputeResolutionIntegrationTest extends FinancePipelineFixture {
    private Map<String, Object> balance(String id, String disputeId, long amount, long fee) {
        return Map.of("id", id, "source", disputeId, "type", "adjustment", "currency", "usd", "status", "available", "amount", amount, "fee", fee, "net", amount - fee);
    }
    private Map<String, Object> dispute(String id, String status, List<?> balances) {
        return Map.of("id", id, "charge", "ch_pi_first", "currency", "usd", "livemode", false, "amount", 10000, "status", status, "balance_transactions", balances);
    }
    private FinanceDisputeCase current(String id) { return mongo.findById(FinanceSourceKey.stripe(true, "acct_platform", "dispute-case:" + id), FinanceDisputeCase.class); }
    private void refresh(String id, String status, List<?> balances) throws Exception {
        when(gateway.getDispute(id)).thenReturn(dispute(id, status, balances));
        assertEquals(200, event("evt_" + UUID.randomUUID(), "charge.dispute.updated", Map.of("id", id, "charge", "ch_pi_first")));
    }
    private FinanceDisputeResolution resolve(String id, long fee) {
        when(gateway.verifyPlatformAccountId("acct_platform")).thenReturn(true);
        var review = current(id); return adjustments.resolveCase(review.id(), review.evidenceDigest(), fee, donor, "Explicit policy review using verified provider costs");
    }
    @Test void lossDoesNotAutomaticallyChargeDiscretionaryFeesAndReviewIsIdempotent() throws Exception {
        settleOneTime(); wallets.holdForRisk("creator", "usd", true, "unrelated-review");
        var loss = balance("txn_loss", "dp_case", -10000, 1500); refresh("dp_case", "lost", List.of(loss, loss));
        assertEquals(-321, available()); assertEquals(1500, current("dp_case").actualFeeCents());
        resolve("dp_case", 1000); assertEquals(-1321, available()); resolve("dp_case", 1000); assertEquals(-1321, available());
        assertEquals(List.of("unrelated-review"), wallets.getWallet("creator", "usd", true).getOpenRiskIds());
        assertEquals(1, mongo.getCollection("finance_dispute_resolutions").countDocuments());
        var fee = ledger.findAll().stream().filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.DISPUTE_FEE_ADJUSTMENT).findFirst().orElseThrow();
        assertEquals(-1000, fee.getCreatorCents()); assertEquals(-500, fee.getPlatformCents()); assertEquals(1500, fee.getProcessorFeeCents());
    }
    @Test void lateWinRestoresOnlyProvenPrincipalAndActualReturnedFeesWithoutRewritingLoss() throws Exception {
        settleOneTime(); var loss = balance("txn_loss", "dp_case", -10000, 1500); refresh("dp_case", "lost", List.of(loss));
        resolve("dp_case", 1500); assertEquals(-1821, available());
        refresh("dp_case", "won", List.of(loss, balance("txn_return", "dp_case", 10000, -1500)));
        assertEquals(-1821, available()); assertTrue(wallets.getWallet("creator", "usd", true).isPayoutHold());
        resolve("dp_case", 0); assertEquals(8445, available()); assertFalse(wallets.getWallet("creator", "usd", true).isPayoutHold());
        event("evt_old_payload", "charge.dispute.closed", Map.of("id", "dp_case", "charge", "ch_pi_first", "status", "lost"));
        resolve("dp_case", 0); assertEquals(8445, available());
        assertEquals(1, ledger.findAll().stream().filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.DISPUTE_REVERSAL).count());
        assertEquals(-10000, ledger.findById(FinanceSourceKey.stripe(true, "acct_platform", "dispute-principal:dp_case")).orElseThrow().getGrossCents());
        assertEquals(2, mongo.getCollection("finance_dispute_resolutions").countDocuments());
    }
    @Test void wonStatusWithoutLinkedPrincipalReturnCannotReleaseOrRestoreFunds() throws Exception {
        settleOneTime(); refresh("dp_case", "lost", List.of(balance("txn_loss", "dp_case", -10000, 1500)));
        refresh("dp_case", "won", List.of()); assertFalse(current("dp_case").evidenceReady());
        assertThrows(IllegalArgumentException.class, () -> resolve("dp_case", 0)); assertEquals(-321, available());
        assertTrue(wallets.getWallet("creator", "usd", true).isPayoutHold());
    }
    @Test void feeBoundsStaleEvidenceAndDifferentPriorDecisionAreRejected() throws Exception {
        settleOneTime(); refresh("dp_case", "lost", List.of(balance("txn_loss", "dp_case", -10000, 1500)));
        assertThrows(IllegalArgumentException.class, () -> resolve("dp_case", 1501)); assertThrows(IllegalArgumentException.class, () -> resolve("dp_case", -1));
        var review = current("dp_case"); assertThrows(IllegalArgumentException.class, () -> adjustments.resolveCase(review.id(), "0".repeat(64), 0, donor, "Stale review"));
        assertEquals(-321, available()); resolve("dp_case", 0); assertThrows(IllegalArgumentException.class, () -> resolve("dp_case", 1)); assertEquals(-321, available());
    }
    @Test void warningAndPreventedCasesClearOnlyTheReviewedDispute() throws Exception {
        settleOneTime(); wallets.holdForRisk("creator", "usd", true, "unrelated-review");
        refresh("dp_warning", "warning_closed", List.of()); resolve("dp_warning", 0);
        refresh("dp_prevented", "prevented", List.of()); resolve("dp_prevented", 0);
        assertEquals(8445, available()); assertEquals(List.of("unrelated-review"), wallets.getWallet("creator", "usd", true).getOpenRiskIds());
        refresh("dp_unknown", "future_status", List.of()); assertThrows(IllegalArgumentException.class, () -> resolve("dp_unknown", 0));
    }
    @Test void genericHistoricalChargeHoldNeedsEveryPaginatedDisputeReviewed() throws Exception {
        settleOneTime(); wallets.holdForRisk("creator", "usd", true, "ch_pi_first"); wallets.holdForRisk("creator", "usd", true, "unrelated-review");
        when(gateway.getCharge("ch_pi_first")).thenReturn(Map.of("id", "ch_pi_first", "livemode", false, "currency", "usd", "amount_refunded", 0, "disputed", true));
        when(gateway.getChargeRefunds("ch_pi_first", null)).thenReturn(Map.of("has_more", false, "data", List.of()));
        var one = dispute("dp_one", "warning_closed", List.of()); var two = dispute("dp_two", "prevented", List.of());
        when(gateway.getDispute("dp_one")).thenReturn(one); when(gateway.getDispute("dp_two")).thenReturn(two);
        when(gateway.getChargeDisputes("ch_pi_first", null)).thenReturn(Map.of("has_more", true, "data", List.of(one)));
        when(gateway.getChargeDisputes("ch_pi_first", "dp_one")).thenReturn(Map.of("has_more", false, "data", List.of(two)));
        adjustments.synchronizeCharge("ch_pi_first"); resolve("dp_one", 0);
        assertTrue(wallets.getWallet("creator", "usd", true).getOpenRiskIds().contains("ch_pi_first"));
        assertTrue(wallets.getWallet("creator", "usd", true).getOpenRiskIds().contains("dp_two"));
        resolve("dp_two", 0); assertEquals(List.of("unrelated-review"), wallets.getWallet("creator", "usd", true).getOpenRiskIds());
    }
    @Test void aNewObservationFencesAnOlderReviewUntilCanonicalEvidenceIsSaved() throws Exception {
        settleOneTime(); refresh("dp_case", "warning_closed", List.of()); var previous = current("dp_case");
        wallets.beginDisputeRefresh(previous.id(), previous.disputeId(), previous.chargeId(), credit("pi_first"), true);
        assertThrows(IllegalArgumentException.class, () -> wallets.resolveDispute(previous.id(), previous.evidenceDigest(), 0, "reviewer", "Old evidence"));
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class, () -> mongo.save(previous));
        assertTrue(wallets.getWallet("creator", "usd", true).isPayoutHold()); assertEquals(8445, available());
    }
    @Test void invalidSourceOrPendingBalanceCannotBookALoss() throws Exception {
        settleOneTime(); var pending = new HashMap<>(balance("txn_loss", "dp_case", -10000, 1500)); pending.put("status", "pending");
        refresh("dp_case", "lost", List.of(pending)); assertEquals(8445, available());
        refresh("dp_case", "lost", List.of(balance("txn_wrong", "dp_other", -10000, 1500))); assertEquals(8445, available());
        assertThrows(IllegalArgumentException.class, () -> resolve("dp_case", 0)); assertTrue(wallets.getWallet("creator", "usd", true).isPayoutHold());
    }
}
