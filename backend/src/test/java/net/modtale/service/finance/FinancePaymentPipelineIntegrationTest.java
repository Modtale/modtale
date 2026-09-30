package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_MONGO_URI", matches = ".+")
class FinancePaymentPipelineIntegrationTest extends FinancePipelineFixture {
    @Test void exactSavedBasisPointsSurviveQuoteCheckoutWebhookAndActualFeeSettlement() throws Exception {
        assertEquals(1234, donations.getDonationConfig("project").get("donationPlatformCutBps"));
        assertEquals(12.34, donations.getDonationConfig("project").get("donationPlatformCutPercent"));
        var intent = checkout(false); assertEquals(1234, intent.getPlatformCents()); assertEquals(1234, intent.getPlatformCutBps());
        project.setDonationPlatformCutBps(2500);
        assertEquals(200, event("evt_paid", "checkout.session.completed", session(intent)));
        assertEquals(0, available()); assertEquals(1, ledger.count());
        when(gateway.getPaymentWithBalanceTransaction("pi_first")).thenReturn(payment("pi_first", "available"));
        settlement.reconcilePendingPayments(); settlement.reconcilePendingPayments();
        assertEquals(8445, available()); assertEquals(1234, credit("pi_first").getPlatformCents());
        assertEquals(321, credit("pi_first").getProcessorFeeCents()); assertEquals(8766, credit("pi_first").getCreatorGrossCents());
        assertEquals(0, wallets.getWallet("creator", "usd", false).getAvailableCents()); assertEquals(2, ledger.count());
    }
    @Test void aLegacyWalletConflictDoesNotStarveOtherCreatorsSettledPayments() throws Exception {
        var legacy = new net.modtale.model.finance.CreatorWallet(); legacy.setId(FinanceWalletService.walletId("creator", "usd", true));
        legacy.setCreatorId("creator"); legacy.setCurrency("usd"); legacy.setTestMode(true); legacy.setAvailableCents(2000); mongo.insert(legacy);
        var first = checkout(false); event("evt_first", "checkout.session.completed", session(first));
        when(gateway.getPaymentWithBalanceTransaction("pi_first")).thenReturn(payment("pi_first", "available"));
        project.setAuthorId("creator2"); var next = checkout(false); var nextSession = session(next); nextSession.put("payment_intent", "pi_next");
        event("evt_next", "checkout.session.completed", nextSession); when(gateway.getPaymentWithBalanceTransaction("pi_next")).thenReturn(payment("pi_next", "available"));
        settlement.reconcilePendingPayments();
        assertEquals(2000, available()); assertEquals(8445, wallets.getWallet("creator2", "usd", true).getAvailableCents());
        assertEquals("source_or_wallet_scope_conflict", ledger.findById(FinanceSourceKey.stripe(true, "acct_platform", "checkout:" + first.getStripeSessionId())).orElseThrow().getMetadata().get("reconciliationReview"));
    }
    @Test void staleShareIsRejectedBeforeCreatingIntentOrProviderCheckout() {
        assertThrows(DonationCheckoutService.SupportTermsChangedException.class,
                () -> donations.createDonationCheckout("project", 10000, false, donor, false, 1000));
        assertEquals(0, intents.count()); verify(gateway, never()).createOrSimulateDonationCheckout(anyString(), anyString(), anyLong(), anyBoolean(), anyString(), anyString(), anyString(), anyBoolean());
    }
    @Test void unpaidCompletionAndReturnUrlCannotCreditButLaterAsyncSuccessCan() throws Exception {
        var intent = checkout(false); var session = session(intent); session.put("payment_status", "unpaid");
        when(gateway.getCheckoutSession(intent.getStripeSessionId(), false)).thenReturn(session);
        assertEquals(200, event("evt_unpaid", "checkout.session.completed", session));
        assertEquals(false, donations.confirmDonationIntent(intent.getId()).get("ok")); assertEquals(0, ledger.count());
        session.put("payment_status", "paid"); assertEquals(200, event("evt_async", "checkout.session.async_payment_succeeded", session));
        assertEquals(1, ledger.count()); assertEquals(0, available());
    }
    @Test void duplicateEventAndSeparateEventForSameCheckoutNeverDoubleCredit() throws Exception {
        var intent = checkout(false); var session = session(intent);
        assertEquals(200, event("evt_same", "checkout.session.completed", session));
        assertEquals(200, event("evt_same", "checkout.session.completed", session));
        assertEquals(200, event("evt_other", "checkout.session.async_payment_succeeded", session));
        when(gateway.getPaymentWithBalanceTransaction("pi_first")).thenReturn(payment("pi_first", "available"));
        settlement.reconcilePendingPayments(); donations.confirmDonationIntent(intent.getId()); settlement.reconcilePendingPayments();
        assertEquals(8445, available()); assertEquals(2, ledger.count());
        assertEquals(2, mongo.getCollection("payment_webhook_receipts").countDocuments());
    }
    @Test void pendingOrMissingActualFeeNeverMakesMoneyAvailable() throws Exception {
        var intent = checkout(false); event("evt_paid", "checkout.session.completed", session(intent));
        when(gateway.getPaymentWithBalanceTransaction("pi_first")).thenReturn(payment("pi_first", "pending"));
        settlement.reconcilePendingPayments(); assertEquals(0, available());
        var pending = new HashMap<>(payment("pi_first", "available")); pending.put("latest_charge", Map.of("id", "ch_pi_first", "paid", true, "captured", true));
        when(gateway.getPaymentWithBalanceTransaction("pi_first")).thenReturn(pending);
        settlement.reconcilePendingPayments(); assertEquals(0, available());
        when(gateway.getPaymentWithBalanceTransaction("pi_first")).thenReturn(payment("pi_first", "available"));
        settlement.reconcilePendingPayments(); assertEquals(8445, available());
    }
    @Test void wrongEventAccountModeApiVersionAndWrongPaymentCurrencyFailClosed() throws Exception {
        var intent = checkout(false); var session = session(intent);
        assertEquals(503, event("evt_account", "checkout.session.completed", session, Map.of("account", "acct_other")));
        assertEquals(503, event("evt_mode", "checkout.session.completed", session, Map.of("livemode", true)));
        assertEquals(503, event("evt_version", "checkout.session.completed", session, Map.of("api_version", "wrong")));
        session.put("livemode", true); assertEquals(503, event("evt_object_mode", "checkout.session.completed", session)); assertEquals(0, ledger.count());
        session.put("livemode", false); event("evt_valid", "checkout.session.completed", session);
        var payment = new HashMap<>(payment("pi_first", "available")); payment.put("currency", "eur");
        when(gateway.getPaymentWithBalanceTransaction("pi_first")).thenReturn(payment); settlement.reconcilePendingPayments(); assertEquals(0, available());
        when(gateway.getPlatformAccountId()).thenReturn("acct_other"); settlement.reconcilePendingPayments(); assertEquals(0, available());
    }
    @Test void switchedPlatformCannotConfirmAnEarlierCheckoutInTheWrongAccount() throws Exception {
        var intent = checkout(false); when(gateway.getPlatformAccountId()).thenReturn("acct_other");
        assertThrows(IllegalArgumentException.class, () -> donations.confirmDonationIntent(intent.getId()));
        assertEquals(503, event("evt_new_platform", "checkout.session.completed", session(intent)));
        assertEquals(0, ledger.count()); verify(gateway, never()).getCheckoutSession(intent.getStripeSessionId(), false);
    }
    @Test void concurrentSignedDeliveriesAndSettlementWorkersCreditOnlyOnce() throws Exception {
        var intent = checkout(false); var session = session(intent);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 4; i++) { final int index = i; results.add(executor.submit(() -> event("evt_concurrent_" + index, "checkout.session.completed", session))); }
            for (var result : results) assertEquals(200, result.get(20, java.util.concurrent.TimeUnit.SECONDS));
            when(gateway.getPaymentWithBalanceTransaction("pi_first")).thenReturn(payment("pi_first", "available"));
            var jobs = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) jobs.add(executor.submit(() -> settlement.reconcilePendingPayments()));
            for (var result : jobs) result.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(8445, available()); assertEquals(2, ledger.count());
    }
    @Test void invoiceBeforeCheckoutAndRenewalUseFrozenShareAndEachCashPaymentExactlyOnce() throws Exception {
        var intent = checkout(true); when(gateway.getCheckoutSession(intent.getStripeSessionId(), false)).thenReturn(session(intent));
        cashInvoice("in_first", "pi_first"); assertEquals(200, event("evt_invoice_first", "invoice.paid", invoice(intent, "in_first")));
        assertEquals(1, subscriptions.count()); assertEquals(1234, subscriptions.findById("sub_synthetic").orElseThrow().getPlatformCutBps());
        project.setDonationPlatformCutBps(2500); assertEquals(200, event("evt_checkout_later", "checkout.session.completed", session(intent)));
        cashInvoice("in_renewal", "pi_renewal"); event("evt_renewal", "invoice.paid", invoice(intent, "in_renewal"));
        event("evt_renewal_duplicate", "invoice.paid", invoice(intent, "in_renewal")); settlement.reconcilePendingPayments(); settlement.reconcilePendingPayments();
        assertEquals(16890, available()); assertTrue(credit("pi_renewal").isRecurring()); assertEquals(1234, credit("pi_renewal").getPlatformCents()); assertEquals(4, ledger.count());
    }
    @Test void manualCreditMultipleAndPartialInvoicePaymentsRemainUncredited() throws Exception {
        var intent = checkout(true); recurring.registerCheckout(intent, session(intent)); var invoice = invoice(intent, "in_review");
        List<Map<String, Object>> fixtures = List.of(Map.of("has_more", false, "data", List.of()), Map.of("has_more", true, "data", List.of()),
                Map.of("has_more", false, "data", List.of(Map.of("status", "paid", "amount_paid", 9999, "payment", Map.of("type", "payment_intent", "payment_intent", "pi_partial")))));
        for (var payments : fixtures) {
            when(gateway.getInvoicePayments("in_review")).thenReturn(payments);
            assertEquals(503, event("evt_needs_review", "invoice.paid", invoice)); assertEquals(0, ledger.count());
        }
        assertEquals(0, available());
    }
    @Test void cancellationAndUnpaidEventsFetchCurrentProviderStateWithoutCrediting() throws Exception {
        var intent = checkout(true); recurring.registerCheckout(intent, session(intent));
        subscriptionStatus("active", true); event("evt_cancel_scheduled", "customer.subscription.updated", Map.of("id", "sub_synthetic", "status", "old_payload"));
        assertTrue(subscriptions.findById("sub_synthetic").orElseThrow().isCancelAtPeriodEnd());
        subscriptionStatus("canceled", false); event("evt_deleted", "customer.subscription.deleted", Map.of("id", "sub_synthetic"));
        event("evt_delayed_old_update", "customer.subscription.updated", Map.of("id", "sub_synthetic", "status", "active"));
        assertEquals("canceled", subscriptions.findById("sub_synthetic").orElseThrow().getStatus());
        event("evt_unpaid", "invoice.payment_failed", invoice(intent, "in_failed")); assertEquals(0, ledger.count()); assertEquals(0, available());
    }
}
