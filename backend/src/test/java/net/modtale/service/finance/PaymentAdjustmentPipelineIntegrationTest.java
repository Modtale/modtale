package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.modtale.model.finance.FinanceLedgerEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_MONGO_URI", matches = ".+")
class PaymentAdjustmentPipelineIntegrationTest extends FinancePipelineFixture {
    private Map<String, Object> charge(long refunded, boolean disputed) {
        return Map.of("id", "ch_pi_first", "livemode", false, "currency", "usd", "amount_refunded", refunded, "disputed", disputed);
    }
    private Map<String, Object> refund(String id, long amount, String status, String balanceStatus) {
        return Map.of("id", id, "charge", "ch_pi_first", "currency", "usd", "amount", amount, "status", status,
                "balance_transaction", Map.of("id", "txn_" + id, "amount", -amount, "fee", 0, "net", -amount, "currency", "usd", "status", balanceStatus));
    }
    private void refunds(List<Map<String, Object>> refunds) {
        when(gateway.getChargeRefunds("ch_pi_first", null)).thenReturn(Map.of("has_more", false, "data", refunds));
    }
    private int refundEvent(String id) throws Exception { return event(id, "charge.refunded", Map.of("id", "ch_pi_first")); }
    @Test void signedRefundWebhookPaginatesAndReversesBothSharesOncePreservingOriginalFee() throws Exception {
        settleOneTime(); when(gateway.getCharge("ch_pi_first")).thenReturn(charge(10000, false));
        when(gateway.getChargeRefunds("ch_pi_first", null)).thenReturn(Map.of("has_more", true, "data", List.of(refund("re_one", 3333, "succeeded", "available"))));
        when(gateway.getChargeRefunds("ch_pi_first", "re_one")).thenReturn(Map.of("has_more", false, "data", List.of(refund("re_two", 6667, "succeeded", "available"))));
        assertEquals(200, refundEvent("evt_refund")); assertEquals(-321, available());
        assertEquals(200, refundEvent("evt_refund")); assertEquals(200, refundEvent("evt_refund_later")); assertEquals(-321, available());
        assertFalse(wallets.getWallet("creator", "usd", true).isPayoutHold());
        var entries = ledger.findAll().stream().filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.REFUND_ADJUSTMENT).toList();
        assertEquals(2, entries.size()); assertEquals(-1234, entries.stream().mapToLong(FinanceLedgerEntry::getPlatformCents).sum());
        assertEquals(8445, credit("pi_first").getCreatorCents());
    }
    @Test void pendingRefundAndMissingBalanceStayHeldThenScheduledReconciliationRepairsThem() throws Exception {
        settleOneTime(); when(gateway.getCharge("ch_pi_first")).thenReturn(charge(10000, false));
        refunds(List.of(refund("re_pending", 10000, "pending", "pending")));
        assertEquals(200, refundEvent("evt_pending")); assertEquals(8445, available()); assertTrue(wallets.getWallet("creator", "usd", true).isPayoutHold());
        refunds(List.of(refund("re_pending", 10000, "succeeded", "pending"))); adjustments.reconcileHeldCharges(); assertEquals(8445, available());
        refunds(List.of(refund("re_pending", 10000, "succeeded", "available"))); adjustments.reconcileHeldCharges();
        assertEquals(-321, available()); assertFalse(wallets.getWallet("creator", "usd", true).isPayoutHold());
    }
    @Test void failedAndCanceledRefundsDoNotDebitAndOnlyTheirOwnHoldIsReleased() throws Exception {
        settleOneTime(); wallets.holdForRisk("creator", "usd", true, "unrelated-review");
        when(gateway.getCharge("ch_pi_first")).thenReturn(charge(0, false));
        refunds(List.of(refund("re_failed", 10000, "failed", "available"), refund("re_canceled", 10000, "canceled", "available")));
        assertEquals(200, refundEvent("evt_failed")); assertEquals(8445, available());
        assertEquals(List.of("unrelated-review"), wallets.getWallet("creator", "usd", true).getOpenRiskIds());
    }
    @Test void refundBeforeSettlementRetriesWithoutLosingTheEvent() throws Exception {
        var intent = checkout(false); event("evt_paid", "checkout.session.completed", session(intent));
        assertEquals(503, refundEvent("evt_early_refund"));
        when(gateway.getPaymentWithBalanceTransaction("pi_first")).thenReturn(payment("pi_first", "available")); settlement.reconcilePendingPayments();
        when(gateway.getCharge("ch_pi_first")).thenReturn(charge(10000, false)); refunds(List.of(refund("re_early", 10000, "succeeded", "available")));
        assertEquals(200, refundEvent("evt_early_refund")); assertEquals(-321, available());
    }
    @Test void wrongChargeModeCurrencyAndRefundSourceCannotDebit() throws Exception {
        settleOneTime();
        for (var changed : List.of(Map.<String, Object>of("livemode", true), Map.<String, Object>of("currency", "eur"), Map.<String, Object>of("id", "ch_other"))) {
            var invalid = new HashMap<>(charge(10000, false)); invalid.putAll(changed); when(gateway.getCharge("ch_pi_first")).thenReturn(invalid);
            assertEquals(503, refundEvent("evt_bad_charge")); assertEquals(8445, available());
        }
        var missingMode = new HashMap<>(charge(10000, false)); missingMode.remove("livemode"); when(gateway.getCharge("ch_pi_first")).thenReturn(missingMode);
        assertEquals(503, refundEvent("evt_missing_mode")); assertEquals(8445, available());
        when(gateway.getCharge("ch_pi_first")).thenReturn(charge(10000, false));
        for (var changed : List.of(Map.<String, Object>of("charge", "ch_other"), Map.<String, Object>of("currency", "eur"))) {
            var invalid = new HashMap<>(refund("re_bad", 10000, "succeeded", "available")); invalid.putAll(changed); refunds(List.of(invalid));
            refundEvent("evt_bad_refund_" + changed.keySet().iterator().next()); assertEquals(8445, available());
        }
        assertTrue(wallets.getWallet("creator", "usd", true).isPayoutHold());
    }
    @Test void accountAndModeSwitchCannotFindOrDebitAnUnrelatedOriginal() throws Exception {
        settleOneTime(); when(gateway.getPlatformAccountId()).thenReturn("acct_other");
        assertEquals(503, refundEvent("evt_scope")); assertEquals(8445, available()); verify(gateway, never()).getCharge("ch_pi_first");
        when(gateway.getPlatformAccountId()).thenReturn("acct_platform"); when(gateway.isTestMode()).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> adjustments.synchronizeCharge("ch_pi_first")); assertEquals(8445, available());
    }
    @Test void disputeLostReversesPrincipalOnceAndLeavesActualFeesForPolicyReview() throws Exception {
        settleOneTime(); wallets.holdForRisk("creator", "usd", true, "unrelated-review");
        when(gateway.getDispute("dp_case")).thenReturn(dispute("lost"));
        assertEquals(200, event("evt_lost", "charge.dispute.closed", Map.of("id", "dp_case", "charge", "ch_pi_first")));
        event("evt_lost_duplicate", "charge.dispute.updated", Map.of("id", "dp_case", "charge", "ch_pi_first"));
        assertEquals(-321, available()); assertTrue(wallets.getWallet("creator", "usd", true).isPayoutHold());
        var review = adjustments.getDisputeCases().getFirst(); assertEquals(1500, review.actualFeeCents());
        assertEquals("POLICY_REVIEW_REQUIRED", review.reviewStatus()); assertTrue(wallets.getWallet("creator", "usd", true).getOpenRiskIds().contains("unrelated-review"));
    }
    @Test void openWonAndWarningClosedDisputesDoNotInventPrincipalOrFeeDebits() throws Exception {
        settleOneTime();
        for (String state : List.of("needs_response", "under_review", "won", "warning_closed", "prevented", "future_unknown")) {
            when(gateway.getDispute("dp_case")).thenReturn(dispute(state));
            event("evt_" + state, "charge.dispute.updated", Map.of("id", "dp_case", "charge", "ch_pi_first")); assertEquals(8445, available());
        }
        assertEquals(2, ledger.count()); assertTrue(wallets.getWallet("creator", "usd", true).isPayoutHold());
    }
    private Map<String, Object> dispute(String status) {
        return Map.of("id", "dp_case", "charge", "ch_pi_first", "currency", "usd", "livemode", false, "amount", 10000, "status", status,
                "balance_transactions", List.of(Map.of("id", "txn_dispute", "currency", "usd", "amount", -10000, "fee", 1500, "net", -11500, "status", "available")));
    }
}
