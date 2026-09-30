package net.modtale.service.finance;

import java.time.LocalDateTime;
import java.util.Map;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Provider availability and actual fees, rather than elapsed guesses, determine payable credit. */
@Service
public class PaymentSettlementService {
    private final FinanceLedgerEntryRepository ledger;
    private final StripeGatewayService gateway;
    private final FinanceWalletService wallets;
    private final MongoTemplate mongo;
    private final PaymentAdjustmentService adjustments;

    public PaymentSettlementService(FinanceLedgerEntryRepository ledger, StripeGatewayService gateway,
            FinanceWalletService wallets, MongoTemplate mongo, PaymentAdjustmentService adjustments) {
        this.ledger = ledger; this.gateway = gateway; this.wallets = wallets; this.mongo = mongo; this.adjustments = adjustments;
    }

    @Scheduled(fixedDelayString = "${app.finance.reconciliation-interval-ms:3600000}")
    public void reconcilePendingPayments() {
        if (!gateway.isReconciliationEnabled()) return;
        String accountId = gateway.getPlatformAccountId();
        for (FinanceLedgerEntry pending : mongo.find(Query.query(Criteria.where("type").is(FinanceLedgerEntry.LedgerType.DONATION)
                .and("status").is(FinanceLedgerEntry.EntryStatus.PENDING)
                .and("metadata.testMode").is(String.valueOf(gateway.isTestMode()))
                .and("metadata.providerAccountId").is(accountId)).with(org.springframework.data.domain.Sort.by("metadata.lastReconciliationAttemptAt")).limit(100), FinanceLedgerEntry.class)) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(pending.getId())),
                    new Update().set("metadata.lastReconciliationAttemptAt", java.time.Instant.now().toString()), FinanceLedgerEntry.class);
            try { reconcile(pending); }
            catch (IllegalArgumentException | IllegalStateException needsReview) {
                // A conflicting/legacy wallet must not starve unrelated settled payments in this batch.
                mongo.updateFirst(Query.query(Criteria.where("_id").is(pending.getId())),
                        new Update().set("metadata.reconciliationReview", "source_or_wallet_scope_conflict"), FinanceLedgerEntry.class);
            }
        }
    }

    public void reconcile(FinanceLedgerEntry pending) {
        String paymentId = pending.getMetadata().get("paymentIntentId");
        if (paymentId == null || Boolean.parseBoolean(pending.getMetadata().get("simulated"))) return;
        if (!gateway.getPlatformAccountId().equals(pending.getMetadata().get("providerAccountId"))) return;
        Map<String, Object> payment = gateway.getPaymentWithBalanceTransaction(paymentId);
        FinanceLedgerEntry credit = settledCredit(pending, payment);
        if (credit == null) return;
        boolean testMode = Boolean.FALSE.equals(payment.get("livemode"));
        Map<?, ?> charge = (Map<?, ?>) payment.get("latest_charge");
        boolean hasRisk = Boolean.TRUE.equals(charge.get("disputed")) || number(charge.get("amount_refunded")) > 0;
        if (hasRisk) wallets.holdForRisk(credit.getCreatorId(), credit.getCurrency(), testMode, String.valueOf(charge.get("id")));
        wallets.postSettledCredit(credit, testMode);
        if (hasRisk) adjustments.synchronizeCharge(String.valueOf(charge.get("id")));
        // The observation is retained as evidence but is no longer a pending earning estimate.
        // A crash before this update is safe: the credit's source ID and wallet transaction are idempotent.
        mongo.updateFirst(Query.query(Criteria.where("_id").is(pending.getId()).and("status").is(FinanceLedgerEntry.EntryStatus.PENDING)),
                new Update().set("status", FinanceLedgerEntry.EntryStatus.PAID).set("metadata.settlement", "reconciled_observation")
                        .set("metadata.creditId", credit.getId()), FinanceLedgerEntry.class);
    }

    static FinanceLedgerEntry settledCredit(FinanceLedgerEntry pending, Map<String, Object> payment) {
        if (payment == null || !"succeeded".equals(payment.get("status")) || !(payment.get("livemode") instanceof Boolean)) return null;
        if (!pending.getMetadata().get("paymentIntentId").equals(payment.get("id"))) return null;
        if (!Boolean.valueOf(pending.getMetadata().get("testMode")).equals(!Boolean.TRUE.equals(payment.get("livemode")))) return null;
        if (!pending.getCurrency().equals(payment.get("currency")) || number(payment.get("amount_received")) != pending.getGrossCents()) return null;
        if (!(payment.get("latest_charge") instanceof Map<?, ?> charge) || !Boolean.TRUE.equals(charge.get("paid"))
                || !Boolean.TRUE.equals(charge.get("captured")) || !(charge.get("balance_transaction") instanceof Map<?, ?> balance)) return null;
        if (!"available".equals(balance.get("status")) || !pending.getCurrency().equals(balance.get("currency"))
                || number(balance.get("amount")) != pending.getGrossCents() || !(balance.get("id") instanceof String balanceId)) return null;
        long fee = number(balance.get("fee"));
        if (fee < 0 || number(balance.get("net")) != pending.getGrossCents() - fee) return null;
        long creatorNet = pending.getGrossCents() - pending.getPlatformCents() - fee;
        // Never silently create a negative creator balance or invent a platform fee subsidy.
        if (creatorNet < 0) return null;
        FinanceLedgerEntry credit = new FinanceLedgerEntry();
        credit.setId(FinanceSourceKey.stripe(Boolean.FALSE.equals(payment.get("livemode")), pending.getMetadata().get("providerAccountId"), "settlement:" + balanceId)); credit.setCreatorId(pending.getCreatorId()); credit.setProjectId(pending.getProjectId());
        credit.setType(FinanceLedgerEntry.LedgerType.DONATION); credit.setGrossCents(pending.getGrossCents());
        credit.setPlatformCents(pending.getPlatformCents()); credit.setCreatorGrossCents(pending.getGrossCents() - pending.getPlatformCents());
        credit.setProcessorFeeCents(fee); credit.setCreatorCents(creatorNet); credit.setCurrency(pending.getCurrency());
        credit.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE); credit.setAvailableAt(LocalDateTime.now());
        credit.setExternalReference(pending.getId()); credit.setStripeReference(balanceId); credit.setRecurring(pending.isRecurring());
        credit.getMetadata().put("providerAccountId", pending.getMetadata().get("providerAccountId"));
        credit.getMetadata().put("settlement", "settled"); credit.getMetadata().put("paymentIntentId", String.valueOf(payment.get("id")));
        credit.getMetadata().put("testMode", String.valueOf(Boolean.FALSE.equals(payment.get("livemode"))));
        credit.getMetadata().put("chargeId", String.valueOf(charge.get("id")));
        return credit;
    }

    private static long number(Object value) {
        if (!(value instanceof Number)) return Long.MIN_VALUE;
        try { return new java.math.BigDecimal(value.toString()).longValueExact(); }
        catch (ArithmeticException invalid) { return Long.MIN_VALUE; }
    }
}
