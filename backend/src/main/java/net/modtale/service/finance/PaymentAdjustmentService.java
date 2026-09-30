package net.modtale.service.finance;

import java.util.List;
import java.util.Map;
import net.modtale.model.finance.FinanceLedgerEntry;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Refund collection never issues bank debits; negative balances offset future earnings only. */
@Service
public class PaymentAdjustmentService {
    private final MongoTemplate mongo;
    private final StripeGatewayService gateway;
    private final FinanceWalletService wallets;
    public PaymentAdjustmentService(MongoTemplate mongo, StripeGatewayService gateway, FinanceWalletService wallets) {
        this.mongo = mongo; this.gateway = gateway; this.wallets = wallets;
    }

    public void synchronizeCharge(String chargeId) { synchronizeCharge(chargeId, chargeId); }

    public void synchronizeCharge(String chargeId, String riskKey) {
        FinanceLedgerEntry original = mongo.findOne(Query.query(Criteria.where("type").is(FinanceLedgerEntry.LedgerType.DONATION)
                .and("metadata.settlement").is("settled").and("metadata.chargeId").is(chargeId)
                .and("metadata.testMode").is(String.valueOf(gateway.isTestMode())).and("metadata.providerAccountId").is(gateway.getPlatformAccountId())), FinanceLedgerEntry.class);
        if (original == null) throw new IllegalArgumentException("Payment settlement is not recorded yet.");
        boolean testMode = Boolean.parseBoolean(original.getMetadata().get("testMode"));
        if (!gateway.getPlatformAccountId().equals(original.getMetadata().get("providerAccountId"))) throw new IllegalArgumentException("Payment account scope changed.");
        wallets.holdForRisk(original.getCreatorId(), original.getCurrency(), testMode, riskKey);
        Map<String, Object> charge = gateway.getCharge(chargeId);
        if (!(charge.get("livemode") instanceof Boolean) || !chargeId.equals(charge.get("id")) || !original.getCurrency().equals(charge.get("currency"))
                || testMode != Boolean.FALSE.equals(charge.get("livemode"))) throw new IllegalArgumentException("Could not verify refund source.");
        String after = null;
        long refunded = 0;
        boolean allFinal = true;
        int pages = 0;
        do {
            Map<String, Object> page = gateway.getChargeRefunds(chargeId, after);
            if (!(page.get("data") instanceof List<?> refunds) || !(page.get("has_more") instanceof Boolean)) throw new IllegalArgumentException("Could not reconcile refunds.");
            for (Object value : refunds) {
                if (!(value instanceof Map<?, ?> refund) || !(refund.get("id") instanceof String refundId)) throw new IllegalArgumentException("Invalid provider refund.");
                after = refundId;
                if (!"succeeded".equals(refund.get("status"))) {
                    if (!"failed".equals(refund.get("status")) && !"canceled".equals(refund.get("status"))) allFinal = false;
                    continue;
                }
                long amount = number(refund.get("amount"));
                if (amount <= 0 || !chargeId.equals(refund.get("charge")) || !original.getCurrency().equals(refund.get("currency"))
                        || !(refund.get("balance_transaction") instanceof Map<?, ?> balance) || !"available".equals(balance.get("status"))) { allFinal = false; continue; }
                long fee = number(balance.get("fee"));
                if (!(balance.get("id") instanceof String balanceId) || !original.getCurrency().equals(balance.get("currency"))
                        || number(balance.get("amount")) != -amount || fee == Long.MIN_VALUE || number(balance.get("net")) != -amount - fee) { allFinal = false; continue; }
                wallets.postRefund(original.getId(), FinanceSourceKey.stripe(testMode, original.getMetadata().get("providerAccountId"), "refund:" + refundId), amount, fee, balanceId, testMode);
                refunded = Math.addExact(refunded, amount);
            }
            if (!Boolean.TRUE.equals(page.get("has_more"))) break;
            if (refunds.isEmpty() || ++pages > 100) throw new IllegalArgumentException("Refund pagination requires review.");
        } while (true);
        if (allFinal && !Boolean.TRUE.equals(charge.get("disputed")) && refunded == number(charge.get("amount_refunded"))) {
            wallets.resolveRisk(original.getCreatorId(), original.getCurrency(), testMode, riskKey);
        }
    }

    public void synchronizeDispute(String disputeId, String chargeId) {
        FinanceLedgerEntry original = mongo.findOne(Query.query(Criteria.where("type").is(FinanceLedgerEntry.LedgerType.DONATION)
                .and("metadata.settlement").is("settled").and("metadata.chargeId").is(chargeId)
                .and("metadata.testMode").is(String.valueOf(gateway.isTestMode())).and("metadata.providerAccountId").is(gateway.getPlatformAccountId())), FinanceLedgerEntry.class);
        if (original == null) throw new IllegalArgumentException("Payment settlement is not recorded yet.");
        boolean testMode = Boolean.parseBoolean(original.getMetadata().get("testMode"));
        String accountId = original.getMetadata().get("providerAccountId");
        if (!gateway.getPlatformAccountId().equals(accountId)) throw new IllegalArgumentException("Payment account scope changed.");
        wallets.holdForRisk(original.getCreatorId(), original.getCurrency(), testMode, disputeId);
        Map<String, Object> dispute = gateway.getDispute(disputeId);
        if (!(dispute.get("livemode") instanceof Boolean) || !disputeId.equals(dispute.get("id")) || !chargeId.equals(dispute.get("charge"))
                || !original.getCurrency().equals(dispute.get("currency")) || testMode != Boolean.FALSE.equals(dispute.get("livemode"))) {
            throw new IllegalArgumentException("Could not verify dispute source.");
        }
        long amount = number(dispute.get("amount"));
        if (amount <= 0 || amount > original.getGrossCents()) throw new IllegalArgumentException("Dispute amount needs review.");
        String status = String.valueOf(dispute.get("status"));
        Long fee = null;
        if (dispute.get("balance_transactions") instanceof List<?> balances) {
            long total = 0; boolean known = true;
            for (Object value : balances) {
                if (!(value instanceof Map<?, ?> balance) || number(balance.get("fee")) == Long.MIN_VALUE
                        || !original.getCurrency().equals(balance.get("currency"))) { known = false; break; }
                total = Math.addExact(total, number(balance.get("fee")));
            }
            if (known) fee = total;
        }
        String caseId = FinanceSourceKey.stripe(testMode, accountId, "dispute-case:" + disputeId);
        if ("lost".equals(status)) wallets.postDisputePrincipal(original.getId(), FinanceSourceKey.stripe(testMode, accountId, "dispute-principal:" + disputeId), amount, disputeId, testMode);
        // Fees are visible for explicit policy review. Never invent a fine or debit a bank account.
        mongo.save(new net.modtale.model.finance.FinanceDisputeCase(caseId, disputeId, chargeId, original.getCreatorId(),
                original.getCurrency(), testMode, status, amount, fee, "POLICY_REVIEW_REQUIRED", java.time.Instant.now()));
    }

    public List<net.modtale.model.finance.FinanceDisputeCase> getDisputeCases() {
        return mongo.find(new Query().with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt")).limit(100), net.modtale.model.finance.FinanceDisputeCase.class);
    }

    @Scheduled(fixedDelayString = "${app.finance.reconciliation-interval-ms:3600000}")
    public void reconcileHeldCharges() {
        if (!gateway.isReconciliationEnabled()) return;
        for (var wallet : mongo.find(Query.query(Criteria.where("testMode").is(gateway.isTestMode()).and("openRiskIds.0").exists(true)).limit(100), net.modtale.model.finance.CreatorWallet.class)) {
            for (String id : wallet.getOpenRiskIds()) if (id.startsWith("ch_") || id.startsWith("evt_")) {
                String chargeId = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                try { synchronizeCharge(chargeId, id); } catch (IllegalArgumentException pending) { /* Keep the precise hold until provider data is final. */ }
            }
        }
    }

    static long number(Object value) {
        if (!(value instanceof Number)) return Long.MIN_VALUE;
        try { return new java.math.BigDecimal(value.toString()).longValueExact(); } catch (ArithmeticException invalid) { return Long.MIN_VALUE; }
    }
}
