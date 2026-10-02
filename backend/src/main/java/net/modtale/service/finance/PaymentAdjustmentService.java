package net.modtale.service.finance;

import java.util.List;
import java.util.Map;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.finance.FinanceDisputeCase;
import net.modtale.model.finance.FinanceDisputeResolution;
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
        if (allFinal && refunded == number(charge.get("amount_refunded"))
                && (!Boolean.TRUE.equals(charge.get("disputed")) || reconcileAllChargeDisputes(original, chargeId))) {
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
        String caseId = FinanceSourceKey.stripe(testMode, accountId, "dispute-case:" + disputeId);
        FinanceDisputeCase previous = wallets.beginDisputeRefresh(caseId, disputeId, chargeId, original, testMode);
        Map<String, Object> dispute = gateway.getDispute(disputeId);
        if (!(dispute.get("livemode") instanceof Boolean) || !disputeId.equals(dispute.get("id")) || !chargeId.equals(dispute.get("charge"))
                || !original.getCurrency().equals(dispute.get("currency")) || testMode != Boolean.FALSE.equals(dispute.get("livemode"))) {
            throw new IllegalArgumentException("Could not verify dispute source.");
        }
        long amount = number(dispute.get("amount"));
        if (amount <= 0 || amount > original.getGrossCents()) throw new IllegalArgumentException("Dispute amount needs review.");
        String status = String.valueOf(dispute.get("status"));
        DisputeEvidence evidence = DisputeEvidence.read(accountId, testMode, disputeId, chargeId, original.getCurrency(), amount, status, dispute.get("balance_transactions"));
        boolean eligible = evidence.ready() && evidence.actualFeeCents() != null && evidence.actualFeeCents() >= 0
                && (("lost".equals(status) && evidence.principalMovementCents() == -amount)
                || ("won".equals(status) && evidence.principalMovementCents() == 0 && evidence.returnedPrincipalCents() >= amount)
                || (List.of("warning_closed", "prevented").contains(status) && evidence.principalMovementCents() == 0));
        if ("lost".equals(status) && mongo.exists(Query.query(Criteria.where("_id").is(FinanceSourceKey.stripe(testMode, accountId, "dispute-return:" + disputeId))), FinanceLedgerEntry.class)) eligible = false;
        String resolutionId = caseId + ":" + evidence.digest();
        String reviewStatus = eligible ? (mongo.exists(Query.query(Criteria.where("_id").is(resolutionId)), FinanceDisputeResolution.class) ? "RESOLVED" : "POLICY_REVIEW_REQUIRED") : "EVIDENCE_PENDING";
        try {
            mongo.save(new FinanceDisputeCase(caseId, disputeId, chargeId, original.getCreatorId(), original.getCurrency(), testMode, status, amount,
                    evidence.actualFeeCents(), reviewStatus, java.time.Instant.now(), accountId, original.getId(), evidence.principalMovementCents(),
                    evidence.returnedPrincipalCents(), eligible, evidence.digest(), evidence.balances(), previous == null ? null : previous.version()));
        } catch (org.springframework.dao.OptimisticLockingFailureException | org.springframework.dao.DuplicateKeyException changed) {
            throw new IllegalArgumentException("Dispute evidence changed during reconciliation; retry the canonical lookup.");
        }
        if (eligible && "lost".equals(status)) wallets.recordDisputeLoss(caseId, evidence.digest());
        if ("RESOLVED".equals(reviewStatus)) wallets.clearReviewedDisputeRisk(caseId, evidence.digest());
    }

    private boolean reconcileAllChargeDisputes(FinanceLedgerEntry original, String chargeId) {
        String after = null; int pages = 0; boolean reviewed = true; var seen = new java.util.HashSet<String>();
        do {
            Map<String, Object> page = gateway.getChargeDisputes(chargeId, after);
            if (!(page.get("data") instanceof List<?> values) || !(page.get("has_more") instanceof Boolean)) throw new IllegalArgumentException("Could not enumerate charge disputes.");
            for (Object value : values) {
                if (!(value instanceof Map<?, ?> dispute) || !(dispute.get("id") instanceof String id) || !chargeId.equals(dispute.get("charge"))
                        || !(dispute.get("livemode") instanceof Boolean) || gateway.isTestMode() != Boolean.FALSE.equals(dispute.get("livemode"))) throw new IllegalArgumentException("Dispute list source needs review.");
                after = id;
                if (!seen.add(id)) continue;
                synchronizeDispute(id, chargeId);
                FinanceDisputeCase current = mongo.findById(FinanceSourceKey.stripe(gateway.isTestMode(), original.getMetadata().get("providerAccountId"), "dispute-case:" + id), FinanceDisputeCase.class);
                if (current == null || !"RESOLVED".equals(current.reviewStatus())) reviewed = false;
            }
            if (!Boolean.TRUE.equals(page.get("has_more"))) break;
            if (values.isEmpty() || ++pages > 100) throw new IllegalArgumentException("Dispute pagination requires review.");
        } while (true);
        return !seen.isEmpty() && reviewed;
    }

    public FinanceDisputeResolution resolveCase(String caseId, String expectedEvidenceDigest, long creatorFeeCents,
            net.modtale.model.user.User reviewer, String reason) {
        FinanceDisputeCase before = mongo.findById(caseId, FinanceDisputeCase.class);
        if (before == null) throw new IllegalArgumentException("Dispute case not found.");
        if (reviewer == null || reviewer.getId() == null || reason == null || reason.isBlank() || reason.length() > 1000) throw new IllegalArgumentException("A reviewer and policy reason are required.");
        if (!gateway.isReconciliationEnabled() || gateway.isTestMode() != before.testMode() || !gateway.verifyPlatformAccountId(before.providerAccountId())) throw new IllegalArgumentException("Dispute provider account or mode does not match.");
        synchronizeDispute(before.disputeId(), before.chargeId());
        FinanceDisputeResolution resolution = wallets.resolveDispute(caseId, expectedEvidenceDigest, creatorFeeCents, reviewer.getId(), reason.trim());
        // Generic charge/refund holds are independent. Repair only already-existing holds after enumerating all related disputes.
        for (String risk : wallets.getWallet(before.creatorId(), before.currency(), before.testMode()).getOpenRiskIds()) {
            if (risk.equals(before.chargeId()) || risk.endsWith(":" + before.chargeId())) {
                try { synchronizeCharge(before.chargeId(), risk); } catch (IllegalArgumentException | IllegalStateException pending) { /* Retain this precise hold. */ }
            }
        }
        return resolution;
    }

    public List<net.modtale.model.finance.FinanceDisputeCase> getDisputeCases() {
        return mongo.find(new Query().with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt")).limit(100), net.modtale.model.finance.FinanceDisputeCase.class);
    }

    public FinanceDisputeCase refreshCase(String caseId) {
        FinanceDisputeCase current = mongo.findById(caseId, FinanceDisputeCase.class);
        if (current == null) throw new IllegalArgumentException("Dispute case not found.");
        if (!gateway.isReconciliationEnabled() || gateway.isTestMode() != current.testMode() || !gateway.verifyPlatformAccountId(current.providerAccountId())) throw new IllegalArgumentException("Dispute provider account or mode does not match.");
        synchronizeDispute(current.disputeId(), current.chargeId());
        return mongo.findById(caseId, FinanceDisputeCase.class);
    }

    public List<FinanceDisputeResolution> getDisputeDecisions(String caseId) {
        return mongo.find(Query.query(Criteria.where("caseId").is(caseId))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")).limit(100), FinanceDisputeResolution.class);
    }

    @Scheduled(fixedDelayString = "${app.finance.reconciliation-interval-ms:3600000}")
    public void reconcileHeldCharges() {
        if (!gateway.isReconciliationEnabled()) return;
        for (FinanceDisputeCase dispute : mongo.find(Query.query(Criteria.where("testMode").is(gateway.isTestMode())
                .and("providerAccountId").is(gateway.getPlatformAccountId())).with(org.springframework.data.domain.Sort.by("updatedAt")).limit(100), FinanceDisputeCase.class)) {
            try { synchronizeDispute(dispute.disputeId(), dispute.chargeId()); } catch (IllegalArgumentException | IllegalStateException pending) { /* Includes late wins; do not treat a recorded loss as irreversible. */ }
        }
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
