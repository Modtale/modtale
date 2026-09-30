package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.modtale.model.finance.CreatorPayoutRequest;
import net.modtale.model.finance.CreatorWallet;
import net.modtale.model.finance.FinanceLedgerEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/** Runs against a disposable replica set, with no application secrets or real payment provider. */
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_MONGO_URI", matches = ".+")
class FinanceWalletIntegrationTest {
    private MongoClient client;
    private MongoTemplate mongo;
    private FinanceWalletService wallets;

    @BeforeEach void setUp() {
        client = MongoClients.create(System.getenv("FINANCE_TEST_MONGO_URI"));
        var factory = new SimpleMongoClientDatabaseFactory(client, "finance_test_" + UUID.randomUUID().toString().replace("-", ""));
        mongo = new MongoTemplate(factory);
        mongo.createCollection(CreatorWallet.class); mongo.createCollection(CreatorPayoutRequest.class); mongo.createCollection(FinanceLedgerEntry.class);
        wallets = new FinanceWalletService(mongo, factory);
    }
    @AfterEach void tearDown() { if (mongo != null) mongo.getDb().drop(); if (client != null) client.close(); }

    private FinanceLedgerEntry credit(String id, long amount) {
        var credit = new FinanceLedgerEntry(); credit.setId(id); credit.setCreatorId("creator"); credit.setGrossCents(amount);
        credit.setCreatorCents(amount); credit.setCurrency("usd"); credit.setType(FinanceLedgerEntry.LedgerType.DONATION);
        credit.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE); credit.getMetadata().put("settlement", "settled");
        return credit;
    }
    private List<CreatorPayoutRequest.Recipient> recipients(long amount) {
        var recipient = new CreatorPayoutRequest.Recipient(); recipient.setUserId("creator"); recipient.setAccountId("acct_test"); recipient.setAmountCents(amount);
        return List.of(recipient);
    }
    @Test void settlementReplayCannotCreditTwiceAndTestBalancesAreIsolated() {
        wallets.postSettledCredit(credit("source-1", 2000), true);
        wallets.postSettledCredit(credit("source-1", 2000), true);
        assertEquals(2000, wallets.getWallet("creator", "usd", true).getAvailableCents());
        assertEquals(0, wallets.getWallet("creator", "usd", false).getAvailableCents());
        assertEquals(1, mongo.getCollection("finance_ledger_entries").countDocuments());
        assertThrows(IllegalStateException.class, () -> wallets.postSettledCredit(credit("source-1", 3000), true));
    }
    @Test void concurrentWithdrawalsCannotSpendTheSameMoney() throws Exception {
        wallets.postSettledCredit(credit("source-1", 1000), true);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> attemptReserve(UUID.randomUUID().toString()));
            var two = executor.submit(() -> attemptReserve(UUID.randomUUID().toString()));
            assertEquals(1, (one.get(20, TimeUnit.SECONDS) ? 1 : 0) + (two.get(20, TimeUnit.SECONDS) ? 1 : 0));
        }
        var wallet = wallets.getWallet("creator", "usd", true);
        assertEquals(0, wallet.getAvailableCents()); assertEquals(1000, wallet.getReservedCents());
        assertEquals(1, mongo.getCollection("creator_payout_requests").countDocuments());
    }
    private boolean attemptReserve(String key) {
        try { wallets.reserve("creator", "creator", "usd", true, key, 1000, 1000, recipients(1000)); return true; }
        catch (IllegalStateException unavailable) { return false; }
    }
    @Test void repeatedRequestAndTransferCompletionAreIdempotentAndDoNotRewriteEarnings() {
        wallets.postSettledCredit(credit("source-1", 2500), true);
        String key = UUID.randomUUID().toString();
        var request = wallets.reserve("creator", "creator", "usd", true, key, 1000, 1000, recipients(1000));
        var replay = wallets.reserve("creator", "creator", "usd", true, key, 1000, 1000, recipients(1000));
        assertEquals(request.getId(), replay.getId());
        assertEquals(1500, wallets.getWallet("creator", "usd", true).getAvailableCents());
        assertEquals(1500, wallets.getTotalAvailable("usd", true));
        assertEquals(0, wallets.getTotalAvailable("usd", false));
        wallets.markAttempted(request.getId());
        assertThrows(IllegalStateException.class, () -> wallets.completeTransfers(request.getId()));
        wallets.recordTransfer(request.getId(), 0, "tr_test");
        wallets.completeTransfers(request.getId()); wallets.completeTransfers(request.getId());
        assertEquals(0, wallets.getWallet("creator", "usd", true).getReservedCents());
        assertEquals(1500, wallets.getWallet("creator", "usd", true).getAvailableCents());
        assertEquals(2500, mongo.findById("source-1", FinanceLedgerEntry.class).getCreatorCents());
        assertEquals(2, mongo.getCollection("finance_ledger_entries").countDocuments());
    }
    @Test void uncertainTransferKeepsItsReservationForReview() {
        wallets.postSettledCredit(credit("source-1", 1000), true);
        var request = wallets.reserve("creator", "creator", "usd", true, UUID.randomUUID().toString(), 1000, 1000, recipients(1000));
        wallets.markAttempted(request.getId()); wallets.requireReview(request.getId(), "Provider outcome could not be reconciled.");
        assertEquals(1000, wallets.getWallet("creator", "usd", true).getReservedCents());
        assertEquals(CreatorPayoutRequest.Status.REQUIRES_REVIEW, wallets.getRequest(request.getId()).getStatus());
        assertThrows(IllegalStateException.class, () -> wallets.completeTransfers(request.getId()));
    }
    @Test void riskArrivingAfterReservationBlocksDispatchAuthorization() {
        wallets.postSettledCredit(credit("source-1", 1000), true);
        var request = wallets.reserve("creator", "creator", "usd", true, UUID.randomUUID().toString(), 1000, 1000, recipients(1000));
        wallets.markAttempted(request.getId());
        wallets.holdForRisk("creator", "usd", true, "dispute:dp_test");
        assertFalse(wallets.authorizeRecipientTransfer(request.getId(), 0));
        assertTrue(wallets.getWallet("creator", "usd", true).isPayoutHold());
        assertEquals(1000, wallets.getWallet("creator", "usd", true).getReservedCents());
    }
    @Test void conflictingReplayCannotCrossModesOrChangeFees() {
        wallets.postSettledCredit(credit("source-1", 1000), true);
        assertThrows(IllegalStateException.class, () -> wallets.postSettledCredit(credit("source-1", 1000), false));
        var changed = credit("source-1", 1000); changed.setProcessorFeeCents(5L);
        assertThrows(IllegalStateException.class, () -> wallets.postSettledCredit(changed, true));
        assertEquals(0, wallets.getWallet("creator", "usd", false).getAvailableCents());
    }
    @Test void fullRefundRetainsOriginalFeeOnlyOnceAndOffsetsFutureEarnings() {
        var original = credit("source-1", 500); original.setCreatorCents(405); original.setPlatformCents(50); original.setProcessorFeeCents(45L);
        wallets.postSettledCredit(original, true);
        wallets.postRefund("source-1", "refund-1", 500, 0, "txn_refund", true);
        wallets.postRefund("source-1", "refund-1", 500, 0, "txn_refund", true);
        assertEquals(-45, wallets.getWallet("creator", "usd", true).getAvailableCents());
        wallets.postSettledCredit(credit("future-earning", 1000), true);
        assertEquals(955, wallets.getWallet("creator", "usd", true).getAvailableCents());
        assertEquals(-50, mongo.findById("refund-1", FinanceLedgerEntry.class).getPlatformCents());
    }
    @Test void partialRefundsConservePlatformRoundingAndNeverDoubleChargeOriginalFees() {
        var original = credit("source-1", 101); original.setCreatorCents(87); original.setPlatformCents(10); original.setProcessorFeeCents(4L);
        wallets.postSettledCredit(original, true);
        wallets.postRefund("source-1", "refund-1", 34, 0, "txn_1", true);
        wallets.postRefund("source-1", "refund-2", 33, 0, "txn_2", true);
        wallets.postRefund("source-1", "refund-3", 34, 0, "txn_3", true);
        assertEquals(-4, wallets.getWallet("creator", "usd", true).getAvailableCents());
        assertThrows(IllegalArgumentException.class, () -> wallets.postRefund("source-1", "refund-too-much", 1, 0, "txn_4", true));
        assertEquals(-4, wallets.getWallet("creator", "usd", true).getAvailableCents());
    }

}
