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

    public PaymentSettlementService(FinanceLedgerEntryRepository ledger, StripeGatewayService gateway,
            FinanceWalletService wallets, MongoTemplate mongo) {
        this.ledger = ledger; this.gateway = gateway; this.wallets = wallets; this.mongo = mongo;
    }

    @Scheduled(fixedDelayString = "${app.finance.reconciliation-interval-ms:3600000}")
    public void reconcilePendingPayments() {
        if (!gateway.isTestMode()) return;
        for (FinanceLedgerEntry pending : ledger.findTop100ByTypeAndStatusOrderByCreatedAtAsc(
                FinanceLedgerEntry.LedgerType.DONATION, FinanceLedgerEntry.EntryStatus.PENDING)) {
            reconcile(pending);
        }
    }

    public void reconcile(FinanceLedgerEntry pending) {
        String paymentId = pending.getMetadata().get("paymentIntentId");
        if (paymentId == null || Boolean.parseBoolean(pending.getMetadata().get("simulated"))) return;
        Map<String, Object> payment = gateway.getPaymentWithBalanceTransaction(paymentId);
        FinanceLedgerEntry credit = settledCredit(pending, payment);
        if (credit == null) return;
        boolean testMode = Boolean.FALSE.equals(payment.get("livemode"));
        wallets.postSettledCredit(credit, testMode);
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
                || !Boolean.TRUE.equals(charge.get("captured")) || Boolean.TRUE.equals(charge.get("disputed"))
                || number(charge.get("amount_refunded")) != 0 || !(charge.get("balance_transaction") instanceof Map<?, ?> balance)) return null;
        if (!"available".equals(balance.get("status")) || !pending.getCurrency().equals(balance.get("currency"))
                || number(balance.get("amount")) != pending.getGrossCents() || !(balance.get("id") instanceof String balanceId)) return null;
        long fee = number(balance.get("fee"));
        if (fee < 0 || number(balance.get("net")) != pending.getGrossCents() - fee) return null;
        long creatorNet = pending.getGrossCents() - pending.getPlatformCents() - fee;
        // Never silently create a negative creator balance or invent a platform fee subsidy.
        if (creatorNet < 0) return null;
        FinanceLedgerEntry credit = new FinanceLedgerEntry();
        credit.setId("settlement:" + balanceId); credit.setCreatorId(pending.getCreatorId()); credit.setProjectId(pending.getProjectId());
        credit.setType(FinanceLedgerEntry.LedgerType.DONATION); credit.setGrossCents(pending.getGrossCents());
        credit.setPlatformCents(pending.getPlatformCents()); credit.setCreatorGrossCents(pending.getGrossCents() - pending.getPlatformCents());
        credit.setProcessorFeeCents(fee); credit.setCreatorCents(creatorNet); credit.setCurrency(pending.getCurrency());
        credit.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE); credit.setAvailableAt(LocalDateTime.now());
        credit.setExternalReference(pending.getId()); credit.setStripeReference(balanceId); credit.setRecurring(pending.isRecurring());
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
