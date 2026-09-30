package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.modtale.model.finance.*;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.query.*;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_MONGO_URI", matches = ".+")
class PayoutReconciliationIntegrationTest extends FinancePipelineFixture {
    private CreatorPayoutService payouts() {
        when(gateway.verifyPlatformAccountId("acct_platform")).thenReturn(true);
        return new CreatorPayoutService(wallets, gateway, mock(UserRepository.class), mock(RevenueOpsSupport.class));
    }
    private CreatorPayoutRequest reserve(long... allocations) {
        var recipients = new ArrayList<CreatorPayoutRequest.Recipient>();
        for (int i = 0; i < allocations.length; i++) {
            var recipient = new CreatorPayoutRequest.Recipient(); recipient.setUserId("recipient" + i);
            recipient.setAccountId("acct_recipient" + i); recipient.setAmountCents(allocations[i]); recipients.add(recipient);
        }
        return wallets.reserve("creator", "creator", "usd", true, UUID.randomUUID().toString(), Arrays.stream(allocations).sum(), 1000, recipients, "acct_platform");
    }
    private Map<String, Object> transfer(CreatorPayoutRequest request, int index, String id) {
        var value = new HashMap<String, Object>(); var recipient = request.getRecipients().get(index);
        value.put("id", id); value.put("object", "transfer"); value.put("livemode", false); value.put("destination", recipient.getAccountId());
        value.put("amount", recipient.getAmountCents()); value.put("currency", request.getCurrency()); value.put("transfer_group", request.getTransferGroup());
        value.put("metadata", CreatorPayoutService.transferMetadata(request, index)); value.put("created", Instant.now().getEpochSecond());
        value.put("reversed", false); value.put("amount_reversed", 0); return value;
    }
    private void authorize(CreatorPayoutRequest request, int index) { wallets.markAttempted(request.getId()); assertTrue(wallets.authorizeRecipientTransfer(request.getId(), index)); }
    @Test void lostProviderResponseRetriesTheExactKeyAndDoesNotMoveSubmissionTime() throws Exception {
        settleOneTime(); var request = reserve(1000); var service = payouts(); var result = new AtomicReference<Map<String, Object>>();
        when(gateway.createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), eq(false), anyString())).thenAnswer(call -> {
            if (result.get() == null) { result.set(transfer(wallets.getRequest(request.getId()), 0, "tr_once")); return new StripeGatewayService.StripeResult(false, null, null, "lost response", Map.of()); }
            return new StripeGatewayService.StripeResult(true, "tr_once", null, null, result.get());
        });
        assertNull(request.getFirstAttemptAt()); service.dispatch(request.getId()); var submitted = wallets.getRequest(request.getId());
        assertEquals(1000, wallets.getWallet("creator", "usd", true).getReservedCents()); assertNotNull(submitted.getFirstAttemptAt());
        service.dispatch(request.getId()); service.dispatch(request.getId()); var completed = wallets.getRequest(request.getId());
        assertEquals(CreatorPayoutRequest.Status.TRANSFERRED, completed.getStatus()); assertEquals(submitted.getFirstAttemptAt(), completed.getFirstAttemptAt());
        assertEquals(submitted.getRecipients().getFirst().getAuthorizedAt(), completed.getRecipients().getFirst().getAuthorizedAt());
        assertEquals(0, wallets.getWallet("creator", "usd", true).getReservedCents()); assertEquals(7445, available());
        verify(gateway, times(2)).createTransfer(anyString(), eq(1000L), eq("usd"), anyString(), anyMap(), eq(false), eq("modtale-payout:" + request.getId() + ":0"));
        assertEquals(1, mongo.getCollection("finance_transfer_receipts").countDocuments());
    }
    @Test void expiredAttemptCanOnlyAdoptAProvenExistingTransferAndPreservesUnrelatedHolds() throws Exception {
        settleOneTime(); var request = reserve(1000); var service = payouts(); authorize(request, 0);
        var evidence = transfer(wallets.getRequest(request.getId()), 0, "tr_found");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(request.getId())), new Update().set("firstAttemptAt", Instant.now().minusSeconds(24 * 3600)), CreatorPayoutRequest.class);
        service.dispatch(request.getId()); assertEquals(CreatorPayoutRequest.Status.REQUIRES_REVIEW, wallets.getRequest(request.getId()).getStatus());
        wallets.holdForRisk("creator", "usd", true, "dp_unrelated"); when(gateway.getTransfer("tr_found")).thenReturn(evidence);
        service.reconcileKnownTransfer(request.getId(), 0, "tr_found", donor, "Confirmed provider request and original transfer group");
        service.reconcileKnownTransfer(request.getId(), 0, "tr_found", donor, "Duplicate review click");
        assertEquals(0, wallets.getWallet("creator", "usd", true).getReservedCents()); assertEquals(7445, available());
        assertEquals(List.of("dp_unrelated"), wallets.getWallet("creator", "usd", true).getOpenRiskIds());
        assertEquals("donor", wallets.getRequest(request.getId()).getRecipients().getFirst().getConfirmedBy());
        verify(gateway, never()).createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), anyBoolean(), anyString());
    }
    @Test void missingAmbiguousOrMismatchedEvidenceNeverReleasesReservedMoney() throws Exception {
        settleOneTime(); var request = reserve(1000); var service = payouts(); authorize(request, 0); wallets.requireReview(request.getId(), "Unknown outcome");
        var valid = transfer(wallets.getRequest(request.getId()), 0, "tr_candidate");
        List<Map<String, Object>> changes = List.of(Map.of("destination", "acct_wrong"), Map.of("amount", 999), Map.of("amount", 1000.5),
                Map.of("currency", "eur"), Map.of("livemode", true), Map.of("object", "charge"), Map.of("id", "tr_other"),
                Map.of("transfer_group", "other"), Map.of("metadata", Map.of()), Map.of("reversed", true), Map.of("amount_reversed", 1), Map.of("created", 1));
        when(gateway.getTransfer("tr_candidate")).thenReturn(Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.reconcileKnownTransfer(request.getId(), 0, "tr_candidate", donor, "Review"));
        for (var change : changes) {
            var invalid = new HashMap<>(valid); invalid.putAll(change); when(gateway.getTransfer("tr_candidate")).thenReturn(invalid);
            assertThrows(IllegalArgumentException.class, () -> service.reconcileKnownTransfer(request.getId(), 0, "tr_candidate", donor, "Review"), change.toString());
        }
        when(gateway.getTransfer("tr_candidate")).thenReturn(valid); when(gateway.verifyPlatformAccountId("acct_platform")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.reconcileKnownTransfer(request.getId(), 0, "tr_candidate", donor, "Review"));
        assertEquals(1000, wallets.getWallet("creator", "usd", true).getReservedCents()); assertEquals(0, mongo.getCollection("finance_transfer_receipts").countDocuments());
    }
    @Test void partialOrganizationSuccessIsAccountedBeforeRemainingRecipientIsReconciled() throws Exception {
        settleOneTime(); var request = reserve(500, 500); var service = payouts();
        when(gateway.createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), eq(false), anyString())).thenAnswer(call -> {
            if ("acct_recipient0".equals(call.getArgument(0))) return new StripeGatewayService.StripeResult(true, "tr_first", null, null, transfer(wallets.getRequest(request.getId()), 0, "tr_first"));
            return new StripeGatewayService.StripeResult(false, null, null, "lost second response", Map.of());
        });
        service.dispatch(request.getId()); assertEquals(500, wallets.getWallet("creator", "usd", true).getReservedCents());
        assertEquals(1, ledger.findAll().stream().filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.PAYOUT).count());
        wallets.holdForRisk("creator", "usd", true, "dp_after_send"); wallets.requireReview(request.getId(), "Second transfer uncertain");
        when(gateway.getTransfer("tr_second")).thenReturn(transfer(wallets.getRequest(request.getId()), 1, "tr_second"));
        service.reconcileKnownTransfer(request.getId(), 1, "tr_second", donor, "Provider confirmed second original request");
        assertEquals(0, wallets.getWallet("creator", "usd", true).getReservedCents()); assertEquals(CreatorPayoutRequest.Status.TRANSFERRED, wallets.getRequest(request.getId()).getStatus());
        assertTrue(wallets.getWallet("creator", "usd", true).isPayoutHold());
        assertEquals(-1000, ledger.findAll().stream().filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.PAYOUT).mapToLong(FinanceLedgerEntry::getCreatorCents).sum());
    }
    @Test void simultaneousAttemptsCannotClaimOneProviderTransferForTwoReservations() throws Exception {
        settleOneTime(); var one = reserve(1000); var two = reserve(1000); authorize(one, 0); authorize(two, 0);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claim(one.getId())); var second = executor.submit(() -> claim(two.getId()));
            assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals(1000, wallets.getWallet("creator", "usd", true).getReservedCents()); assertEquals(1, mongo.getCollection("finance_transfer_receipts").countDocuments());
        assertEquals(1, ledger.findAll().stream().filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.PAYOUT).count());
    }
    private boolean claim(String id) {
        try { wallets.recordTransfer(id, 0, "tr_single", "operator", "Verified fixture"); return true; } catch (RuntimeException conflict) { return false; }
    }
    @Test void riskBetweenReservationAndAuthorizationPreventsOutboundMoneyButKeepsReservation() throws Exception {
        settleOneTime(); var request = reserve(1000); var service = payouts(); wallets.holdForRisk("creator", "usd", true, "dp_before_send");
        service.dispatch(request.getId()); assertEquals(1000, wallets.getWallet("creator", "usd", true).getReservedCents());
        assertNull(wallets.getRequest(request.getId()).getFirstAttemptAt()); verify(gateway, never()).createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), anyBoolean(), anyString());
    }
    @Test void changedPlatformAndLegacyWalletScopeCannotSpendPreviouslyFundedBalances() throws Exception {
        settleOneTime(); var request = reserve(1000); var service = payouts(); when(gateway.verifyPlatformAccountId("acct_platform")).thenReturn(false);
        service.dispatch(request.getId()); assertEquals(CreatorPayoutRequest.Status.REQUIRES_REVIEW, wallets.getRequest(request.getId()).getStatus());
        assertEquals(1000, wallets.getWallet("creator", "usd", true).getReservedCents());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(request.getId())), new Update().unset("providerAccountId"), CreatorPayoutRequest.class);
        assertEquals(request.getId(), wallets.getReviewRequests().getFirst().getId());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(FinanceWalletService.walletId("creator", "usd", true))), new Update().unset("providerAccountId"), CreatorWallet.class);
        assertThrows(IllegalStateException.class, () -> reserve(1000)); assertEquals(7445, available());
        var next = credit("pi_first"); next.setId("new-source");
        assertThrows(IllegalStateException.class, () -> wallets.postSettledCredit(next, true));
        assertFalse(ledger.existsById("new-source"));
    }
}
