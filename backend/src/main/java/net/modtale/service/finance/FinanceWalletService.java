package net.modtale.service.finance;

import com.mongodb.MongoException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.modtale.model.finance.CreatorPayoutRequest;
import net.modtale.model.finance.CreatorWallet;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.finance.FinanceTransferReceipt;
import net.modtale.model.finance.FinanceDisputeCase;
import net.modtale.model.finance.FinanceDisputeResolution;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Requires MongoDB replica-set transactions. Standalone databases fail closed before moving money. */
@Service
public class FinanceWalletService {
    private final MongoTemplate mongo;
    private final TransactionTemplate transactions;

    public FinanceWalletService(MongoTemplate mongo, MongoDatabaseFactory databaseFactory) {
        this.mongo = mongo;
        this.transactions = new TransactionTemplate(new MongoTransactionManager(databaseFactory));
    }

    public static String walletId(String creatorId, String currency, boolean testMode) {
        if (creatorId == null || creatorId.isBlank() || creatorId.contains(":")) throw new IllegalArgumentException("Invalid creator.");
        if (currency == null || !currency.matches("[A-Za-z]{3}")) throw new IllegalArgumentException("Invalid currency.");
        return (testMode ? "test:" : "live:") + creatorId + ":" + currency.toLowerCase(Locale.ROOT);
    }

    public CreatorWallet getWallet(String creatorId, String currency, boolean testMode) {
        String id = walletId(creatorId, currency, testMode);
        CreatorWallet wallet = mongo.findById(id, CreatorWallet.class);
        if (wallet != null) return wallet;
        wallet = new CreatorWallet(); wallet.setId(id); wallet.setCreatorId(creatorId);
        wallet.setCurrency(currency); wallet.setTestMode(testMode);
        return wallet;
    }

    public long getTotalAvailable(String currency, boolean testMode) {
        var aggregation = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                org.springframework.data.mongodb.core.aggregation.Aggregation.match(Criteria.where("currency").is(currency)
                        .and("testMode").is(testMode).and("availableCents").gt(0)),
                org.springframework.data.mongodb.core.aggregation.Aggregation.group().sum("availableCents").as("amount"));
        var result = mongo.aggregate(aggregation, CreatorWallet.class, org.bson.Document.class).getUniqueMappedResult();
        return result == null ? 0 : ((Number) result.get("amount")).longValue();
    }

    public void postSettledCredit(FinanceLedgerEntry credit, boolean testMode) {
        if (credit.getId() == null || credit.getId().isBlank() || credit.getCreatorCents() < 0
                || credit.getStatus() != FinanceLedgerEntry.EntryStatus.AVAILABLE
                || !"settled".equals(credit.getMetadata().get("settlement"))) {
            throw new IllegalArgumentException("Only final, source-identified settled credits can fund a wallet.");
        }
        String id = walletId(credit.getCreatorId(), credit.getCurrency(), testMode);
        credit.getMetadata().put("testMode", String.valueOf(testMode));
        transact(() -> {
            FinanceLedgerEntry existing = mongo.findById(credit.getId(), FinanceLedgerEntry.class);
            if (existing != null) {
                if (!Objects.equals(existing.getCreatorId(), credit.getCreatorId())
                        || existing.getCreatorCents() != credit.getCreatorCents()
                        || existing.getGrossCents() != credit.getGrossCents()
                        || !Objects.equals(existing.getCurrency(), credit.getCurrency())
                        || existing.getPlatformCents() != credit.getPlatformCents()
                        || !Objects.equals(existing.getProcessorFeeCents(), credit.getProcessorFeeCents())
                        || !Objects.equals(existing.getCreatorGrossCents(), credit.getCreatorGrossCents())
                        || !Objects.equals(existing.getProjectId(), credit.getProjectId())
                        || !Objects.equals(existing.getExternalReference(), credit.getExternalReference())
                        || existing.getType() != credit.getType() || existing.getStatus() != credit.getStatus()
                        || !Objects.equals(existing.getMetadata().get("providerAccountId"), credit.getMetadata().get("providerAccountId"))
                        || !Objects.equals(existing.getMetadata().get("testMode"), String.valueOf(testMode))) {
                    throw new IllegalStateException("A conflicting settlement already uses this source reference.");
                }
                return null;
            }
            String accountId = credit.getMetadata().get("providerAccountId");
            if (accountId == null || !accountId.matches("acct_[A-Za-z0-9]+")) throw new IllegalArgumentException("Verified funding account is required.");
            CreatorWallet wallet = mongo.findById(id, CreatorWallet.class);
            if (wallet != null && (!Objects.equals(accountId, wallet.getProviderAccountId())
                    && (wallet.getProviderAccountId() != null || wallet.getAvailableCents() != 0 || wallet.getReservedCents() != 0))) {
                throw new IllegalStateException("Wallet funding scope needs reconciliation before accepting another account's funds.");
            }
            mongo.insert(credit);
            mongo.upsert(Query.query(Criteria.where("_id").is(id)), new Update()
                    .setOnInsert("creatorId", credit.getCreatorId()).setOnInsert("currency", credit.getCurrency())
                    .setOnInsert("testMode", testMode).setOnInsert("reservedCents", 0L).setOnInsert("payoutHold", false)
                    .set("providerAccountId", accountId).inc("availableCents", credit.getCreatorCents()), CreatorWallet.class);
            return null;
        });
    }

    public CreatorPayoutRequest reserve(String creatorId, String requesterId, String currency, boolean testMode,
            String requestKey, long amountCents, long minimumCents, List<CreatorPayoutRequest.Recipient> recipients, String providerAccountId) {
        if (providerAccountId == null || !providerAccountId.matches("acct_[A-Za-z0-9]+")) throw new IllegalArgumentException("Verified payout account is required.");
        try { UUID.fromString(requestKey); } catch (RuntimeException invalid) { throw new IllegalArgumentException("A valid payout request key is required."); }
        if (amountCents < minimumCents || minimumCents < 1) throw new IllegalArgumentException("Payout amount is below the minimum.");
        if (recipients == null || recipients.isEmpty()) throw new IllegalArgumentException("No verified payout recipients.");
        long total = 0;
        var accountIds = new java.util.HashSet<String>();
        for (var recipient : recipients) {
            if (recipient.getAccountId() == null || !recipient.getAccountId().startsWith("acct_")
                    || !accountIds.add(recipient.getAccountId()) || recipient.getAmountCents() <= 0) throw new IllegalArgumentException("Invalid payout recipient.");
            total = Math.addExact(total, recipient.getAmountCents());
        }
        if (total != amountCents) throw new IllegalArgumentException("Recipient allocations must equal the reserved payout.");
        String walletId = walletId(creatorId, currency, testMode);
        String payoutId = walletId + ":" + requestKey;
        return transact(() -> {
            CreatorPayoutRequest existing = mongo.findById(payoutId, CreatorPayoutRequest.class);
            if (existing != null) {
                if (existing.getAmountCents() != amountCents || !Objects.equals(existing.getRequestedBy(), requesterId)
                        || !Objects.equals(existing.getProviderAccountId(), providerAccountId)) {
                    throw new IllegalArgumentException("This request key was already used for a different payout.");
                }
                return existing;
            }
            var wallet = mongo.findAndModify(Query.query(Criteria.where("_id").is(walletId)
                            .and("providerAccountId").is(providerAccountId).and("availableCents").gte(amountCents).and("payoutHold").ne(true).and("openRiskIds.0").exists(false)),
                    new Update().inc("availableCents", -amountCents).inc("reservedCents", amountCents),
                    FindAndModifyOptions.options().returnNew(true), CreatorWallet.class);
            if (wallet == null) throw new IllegalStateException("Insufficient settled funds or the account is on a payout hold.");
            CreatorPayoutRequest request = new CreatorPayoutRequest();
            request.setId(payoutId); request.setWalletId(walletId); request.setCreatorId(creatorId);
            request.setRequestedBy(requesterId); request.setCurrency(currency); request.setTestMode(testMode);
            request.setAmountCents(amountCents); request.setProviderAccountId(providerAccountId);
            request.setTransferGroup("modtale_" + UUID.randomUUID().toString().replace("-", ""));
            for (var recipient : recipients) recipient.setCorrelationId(UUID.randomUUID().toString());
            request.setRecipients(recipients);
            return mongo.insert(request);
        });
    }

    public void holdForRisk(String creatorId, String currency, boolean testMode, String riskId) {
        String id = walletId(creatorId, currency, testMode);
        mongo.upsert(Query.query(Criteria.where("_id").is(id)), new Update()
                .setOnInsert("creatorId", creatorId).setOnInsert("currency", currency).setOnInsert("testMode", testMode)
                .setOnInsert("availableCents", 0L).setOnInsert("reservedCents", 0L).setOnInsert("payoutHold", false)
                .addToSet("openRiskIds", riskId), CreatorWallet.class);
    }

    public void resolveRisk(String creatorId, String currency, boolean testMode, String riskId) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(walletId(creatorId, currency, testMode))),
                new Update().pull("openRiskIds", riskId), CreatorWallet.class);
    }

    /** Reverses refunded principal once; provider refund fees/rebates are additional actual amounts. */
    public void postRefund(String originalCreditId, String adjustmentId, long refundedGrossCents,
            long providerRefundFeeCents, String providerReference, boolean testMode) {
        postPrincipalAdjustment(originalCreditId, adjustmentId, refundedGrossCents, providerRefundFeeCents, providerReference, testMode, FinanceLedgerEntry.LedgerType.REFUND_ADJUSTMENT);
    }

    public void postDisputePrincipal(String originalCreditId, String adjustmentId, long disputedCents, String providerReference, boolean testMode) {
        postPrincipalAdjustment(originalCreditId, adjustmentId, disputedCents, 0, providerReference, testMode, FinanceLedgerEntry.LedgerType.DISPUTE_ADJUSTMENT);
    }

    private void postPrincipalAdjustment(String originalCreditId, String adjustmentId, long refundedGrossCents,
            long providerRefundFeeCents, String providerReference, boolean testMode, FinanceLedgerEntry.LedgerType type) {
        if (adjustmentId == null || refundedGrossCents <= 0) throw new IllegalArgumentException("Invalid principal adjustment.");
        transact(() -> { postPrincipalAdjustmentInTransaction(originalCreditId, adjustmentId, refundedGrossCents, providerRefundFeeCents, providerReference, testMode, type); return null; });
    }

    private void postPrincipalAdjustmentInTransaction(String originalCreditId, String adjustmentId, long refundedGrossCents,
            long providerRefundFeeCents, String providerReference, boolean testMode, FinanceLedgerEntry.LedgerType type) {
        FinanceLedgerEntry existing = mongo.findById(adjustmentId, FinanceLedgerEntry.class);
        if (existing != null) {
            if (!Objects.equals(existing.getExternalReference(), originalCreditId) || existing.getGrossCents() != -refundedGrossCents
                    || !Objects.equals(existing.getProcessorFeeCents(), providerRefundFeeCents) || existing.getType() != type) throw new IllegalStateException("Conflicting refund source.");
            return;
        }
        FinanceLedgerEntry original = mongo.findById(originalCreditId, FinanceLedgerEntry.class);
        if (original == null || original.getGrossCents() <= 0 || !Boolean.valueOf(original.getMetadata().get("testMode")).equals(testMode)) {
            throw new IllegalStateException("The original payment must be reconciled before its refund.");
        }
        List<FinanceLedgerEntry> prior = mongo.find(Query.query(Criteria.where("externalReference").is(originalCreditId)
                .and("type").in(FinanceLedgerEntry.LedgerType.REFUND_ADJUSTMENT, FinanceLedgerEntry.LedgerType.DISPUTE_ADJUSTMENT, FinanceLedgerEntry.LedgerType.DISPUTE_REVERSAL)), FinanceLedgerEntry.class);
        long previousGross = prior.stream().mapToLong(entry -> -entry.getGrossCents()).sum();
        long previousPlatform = prior.stream().mapToLong(entry -> -entry.getPlatformCents()).sum();
        long cumulativeGross = Math.addExact(previousGross, refundedGrossCents);
        if (cumulativeGross > original.getGrossCents()) throw new IllegalArgumentException("Refunds exceed the recorded charge.");
        long platformTotal = java.math.BigInteger.valueOf(original.getPlatformCents()).multiply(java.math.BigInteger.valueOf(cumulativeGross))
                .add(java.math.BigInteger.valueOf(original.getGrossCents() / 2)).divide(java.math.BigInteger.valueOf(original.getGrossCents())).longValueExact();
        long platformReversal = platformTotal - previousPlatform;
        long creatorAdjustment = Math.subtractExact(-refundedGrossCents + platformReversal, providerRefundFeeCents);
        FinanceLedgerEntry adjustment = new FinanceLedgerEntry(); adjustment.setId(adjustmentId);
        adjustment.setCreatorId(original.getCreatorId()); adjustment.setProjectId(original.getProjectId());
        adjustment.setType(type); adjustment.setGrossCents(-refundedGrossCents);
        adjustment.setCreatorCents(creatorAdjustment); adjustment.setPlatformCents(-platformReversal); adjustment.setProcessorFeeCents(providerRefundFeeCents);
        adjustment.setCurrency(original.getCurrency()); adjustment.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE);
        adjustment.setExternalReference(originalCreditId); adjustment.setStripeReference(providerReference);
        adjustment.getMetadata().put("settlement", "settled"); adjustment.getMetadata().put("testMode", String.valueOf(testMode));
        adjustment.getMetadata().put("adjustmentReason", type == FinanceLedgerEntry.LedgerType.REFUND_ADJUSTMENT ? "provider_confirmed_refund" : "provider_confirmed_dispute_loss");
        mongo.insert(adjustment);
        var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(walletId(original.getCreatorId(), original.getCurrency(), testMode))),
                new Update().inc("availableCents", creatorAdjustment), CreatorWallet.class);
        if (result.getMatchedCount() != 1) throw new IllegalStateException("Original creator wallet not found.");
        return;
    }

    /** Marks a new observation in the same transaction as its hold, fencing older reviews. */
    public FinanceDisputeCase beginDisputeRefresh(String caseId, String disputeId, String chargeId, FinanceLedgerEntry original, boolean testMode) {
        return transact(() -> {
            String account = original.getMetadata().get("providerAccountId");
            FinanceDisputeCase existing = mongo.findById(caseId, FinanceDisputeCase.class);
            if (existing != null && (!Objects.equals(existing.originalCreditId(), original.getId()) || !Objects.equals(existing.providerAccountId(), account))) throw new IllegalArgumentException("Dispute binding changed.");
            holdForRisk(original.getCreatorId(), original.getCurrency(), testMode, disputeId);
            return mongo.findAndModify(Query.query(Criteria.where("_id").is(caseId)), new Update()
                    .setOnInsert("disputeId", disputeId).setOnInsert("chargeId", chargeId).setOnInsert("creatorId", original.getCreatorId())
                    .setOnInsert("currency", original.getCurrency()).setOnInsert("testMode", testMode).setOnInsert("providerAccountId", account)
                    .setOnInsert("originalCreditId", original.getId()).setOnInsert("balanceTransactions", List.of())
                    .setOnInsert("disputedCents", 0L).setOnInsert("returnedPrincipalCents", 0L).setOnInsert("evidenceReady", false).setOnInsert("providerStatus", "pending")
                    .set("reviewStatus", "REFRESHING").set("updatedAt", Instant.now()).inc("version", 1L),
                    FindAndModifyOptions.options().upsert(true).returnNew(true), FinanceDisputeCase.class);
        });
    }

    public void recordDisputeLoss(String caseId, String digest) {
        transact(() -> {
            FinanceDisputeCase dispute = requireCurrentDispute(caseId, digest);
            if (!"lost".equals(dispute.providerStatus())) throw new IllegalArgumentException("Dispute is no longer a verified loss.");
            if (mongo.exists(Query.query(Criteria.where("_id").is(FinanceSourceKey.stripe(dispute.testMode(), dispute.providerAccountId(), "dispute-return:" + dispute.disputeId()))), FinanceLedgerEntry.class)) throw new IllegalArgumentException("A reopened principal movement requires separate review.");
            postPrincipalAdjustmentInTransaction(dispute.originalCreditId(), FinanceSourceKey.stripe(dispute.testMode(), dispute.providerAccountId(), "dispute-principal:" + dispute.disputeId()),
                    dispute.disputedCents(), 0, dispute.disputeId(), dispute.testMode(), FinanceLedgerEntry.LedgerType.DISPUTE_ADJUSTMENT);
            mongo.updateFirst(Query.query(Criteria.where("_id").is(caseId)), new Update().inc("version", 1L), FinanceDisputeCase.class);
            return null;
        });
    }

    private FinanceDisputeCase requireCurrentDispute(String caseId, String digest) {
        FinanceDisputeCase dispute = mongo.findById(caseId, FinanceDisputeCase.class);
        if (dispute == null || !Objects.equals(digest, dispute.evidenceDigest()) || !dispute.evidenceReady()
                || !List.of("POLICY_REVIEW_REQUIRED", "RESOLVED").contains(dispute.reviewStatus()) || dispute.principalMovementCents() == null || dispute.actualFeeCents() == null || dispute.actualFeeCents() < 0) {
            throw new IllegalArgumentException("Dispute evidence is incomplete or changed; refresh the review.");
        }
        return dispute;
    }

    public void clearReviewedDisputeRisk(String caseId, String digest) {
        transact(() -> {
            FinanceDisputeCase dispute = requireCurrentDispute(caseId, digest);
            if (!mongo.exists(Query.query(Criteria.where("_id").is(caseId + ":" + digest)), FinanceDisputeResolution.class)) throw new IllegalArgumentException("Dispute policy review is missing.");
            resolveRisk(dispute.creatorId(), dispute.currency(), dispute.testMode(), dispute.disputeId());
            mongo.updateFirst(Query.query(Criteria.where("_id").is(caseId)), new Update().inc("version", 1L), FinanceDisputeCase.class);
            return null;
        });
    }

    /** Explicit cumulative fee allocation and any proven late-win restoration are one immutable transaction. */
    public FinanceDisputeResolution resolveDispute(String caseId, String digest, long creatorFeeCents, String reviewer, String reason) {
        return transact(() -> {
            FinanceDisputeCase dispute = requireCurrentDispute(caseId, digest);
            if (creatorFeeCents < 0 || creatorFeeCents > dispute.actualFeeCents()) throw new IllegalArgumentException("Creator fees must be explicitly allocated within verified actual costs.");
            String resolutionId = caseId + ":" + digest;
            FinanceDisputeResolution priorResolution = mongo.findById(resolutionId, FinanceDisputeResolution.class);
            if (priorResolution != null) {
                if (priorResolution.creatorFeeCents() != creatorFeeCents) throw new IllegalArgumentException("This evidence already has a different immutable fee decision.");
                resolveRisk(dispute.creatorId(), dispute.currency(), dispute.testMode(), dispute.disputeId());
                mongo.updateFirst(Query.query(Criteria.where("_id").is(caseId)), new Update().set("reviewStatus", "RESOLVED").inc("version", 1L), FinanceDisputeCase.class);
                return priorResolution;
            }
            FinanceLedgerEntry original = mongo.findById(dispute.originalCreditId(), FinanceLedgerEntry.class);
            if (original == null || !Objects.equals(original.getMetadata().get("providerAccountId"), dispute.providerAccountId())) throw new IllegalArgumentException("Original funding evidence changed.");
            CreatorWallet wallet = getWallet(dispute.creatorId(), dispute.currency(), dispute.testMode());
            if (!Objects.equals(wallet.getProviderAccountId(), dispute.providerAccountId())) throw new IllegalArgumentException("Wallet funding scope requires separate reconciliation.");
            String lossId = FinanceSourceKey.stripe(dispute.testMode(), dispute.providerAccountId(), "dispute-principal:" + dispute.disputeId());
            String returnId = FinanceSourceKey.stripe(dispute.testMode(), dispute.providerAccountId(), "dispute-return:" + dispute.disputeId());
            FinanceLedgerEntry loss = mongo.findById(lossId, FinanceLedgerEntry.class);
            FinanceLedgerEntry returned = mongo.findById(returnId, FinanceLedgerEntry.class);
            long creatorChange = 0;
            if ("lost".equals(dispute.providerStatus())) {
                if (loss == null || returned != null || loss.getGrossCents() != -dispute.disputedCents()) throw new IllegalArgumentException("Loss principal needs reconciliation before closing its hold.");
            } else if (loss != null && returned == null) {
                if (!"won".equals(dispute.providerStatus()) || dispute.principalMovementCents() != 0 || dispute.returnedPrincipalCents() < dispute.disputedCents()) throw new IllegalArgumentException("No settled principal-return evidence is available.");
                FinanceLedgerEntry reversal = disputeAdjustment(original, dispute, returnId, FinanceLedgerEntry.LedgerType.DISPUTE_REVERSAL);
                reversal.setGrossCents(-loss.getGrossCents()); reversal.setCreatorCents(-loss.getCreatorCents()); reversal.setPlatformCents(-loss.getPlatformCents()); reversal.setProcessorFeeCents(0L);
                mongo.insert(reversal); creatorChange = Math.addExact(creatorChange, reversal.getCreatorCents());
            }
            List<FinanceLedgerEntry> feeHistory = mongo.find(Query.query(Criteria.where("type").is(FinanceLedgerEntry.LedgerType.DISPUTE_FEE_ADJUSTMENT)
                    .and("metadata.disputeCaseId").is(caseId)), FinanceLedgerEntry.class);
            long priorCreatorFees = 0, priorActualFees = 0;
            for (var fee : feeHistory) { priorCreatorFees = Math.addExact(priorCreatorFees, -fee.getCreatorCents()); priorActualFees = Math.addExact(priorActualFees, fee.getProcessorFeeCents()); }
            long creatorDelta = Math.subtractExact(creatorFeeCents, priorCreatorFees);
            long actualDelta = Math.subtractExact(dispute.actualFeeCents(), priorActualFees);
            if (creatorDelta != 0 || actualDelta != 0) {
                FinanceLedgerEntry fee = disputeAdjustment(original, dispute, resolutionId + ":fee", FinanceLedgerEntry.LedgerType.DISPUTE_FEE_ADJUSTMENT);
                fee.setGrossCents(0); fee.setCreatorCents(-creatorDelta); fee.setPlatformCents(Math.negateExact(Math.subtractExact(actualDelta, creatorDelta))); fee.setProcessorFeeCents(actualDelta);
                mongo.insert(fee); creatorChange = Math.subtractExact(creatorChange, creatorDelta);
            }
            var decision = new FinanceDisputeResolution(resolutionId, caseId, dispute.disputeId(), digest, dispute.providerAccountId(), dispute.testMode(),
                    dispute.providerStatus(), dispute.actualFeeCents(), creatorFeeCents, reviewer, reason, Instant.now(), dispute.currency(), dispute.disputedCents(),
                    dispute.principalMovementCents(), dispute.returnedPrincipalCents(), List.copyOf(dispute.balanceTransactions()));
            mongo.insert(decision);
            var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(wallet.getId()).and("providerAccountId").is(dispute.providerAccountId())),
                    new Update().inc("availableCents", creatorChange).pull("openRiskIds", dispute.disputeId()), CreatorWallet.class);
            if (result.getMatchedCount() != 1) throw new IllegalStateException("Wallet scope changed during dispute resolution.");
            mongo.updateFirst(Query.query(Criteria.where("_id").is(caseId)), new Update().set("reviewStatus", "RESOLVED").inc("version", 1L), FinanceDisputeCase.class);
            return decision;
        });
    }

    private FinanceLedgerEntry disputeAdjustment(FinanceLedgerEntry original, FinanceDisputeCase dispute, String id, FinanceLedgerEntry.LedgerType type) {
        FinanceLedgerEntry entry = new FinanceLedgerEntry(); entry.setId(id); entry.setCreatorId(original.getCreatorId()); entry.setProjectId(original.getProjectId());
        entry.setType(type); entry.setCurrency(original.getCurrency()); entry.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE);
        entry.setExternalReference(original.getId()); entry.setStripeReference(dispute.disputeId());
        entry.getMetadata().put("settlement", "settled"); entry.getMetadata().put("testMode", String.valueOf(dispute.testMode()));
        entry.getMetadata().put("providerAccountId", dispute.providerAccountId()); entry.getMetadata().put("disputeCaseId", dispute.id());
        entry.getMetadata().put("evidenceDigest", dispute.evidenceDigest()); return entry;
    }

    public List<CreatorPayoutRequest> getRecentRequests(String creatorId, boolean testMode) {
        return mongo.find(Query.query(Criteria.where("creatorId").is(creatorId).and("testMode").is(testMode))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")).limit(20), CreatorPayoutRequest.class);
    }

    public CreatorPayoutRequest getRequest(String id) { return mongo.findById(id, CreatorPayoutRequest.class); }

    public List<CreatorPayoutRequest> getUnfinishedRequests(boolean testMode) {
        return mongo.find(Query.query(Criteria.where("status").in(CreatorPayoutRequest.Status.RESERVED, CreatorPayoutRequest.Status.PROCESSING).and("testMode").is(testMode)).with(org.springframework.data.domain.Sort.by("lastDispatchAttemptAt", "createdAt")).limit(100), CreatorPayoutRequest.class);
    }

    public void noteDispatchAttempt(String id) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").in(CreatorPayoutRequest.Status.RESERVED, CreatorPayoutRequest.Status.PROCESSING)),
                new Update().set("lastDispatchAttemptAt", Instant.now()), CreatorPayoutRequest.class);
    }

    public CreatorPayoutRequest markAttempted(String id) {
        return mongo.findAndModify(Query.query(Criteria.where("_id").is(id).and("firstAttemptAt").is(null)
                        .and("status").is(CreatorPayoutRequest.Status.RESERVED)),
                new Update().set("status", CreatorPayoutRequest.Status.PROCESSING),
                FindAndModifyOptions.options().returnNew(true), CreatorPayoutRequest.class);
    }

    /** Linearizes each outbound authorization against risk holds on the same wallet document. */
    public boolean authorizeRecipientTransfer(String requestId, int recipientIndex) {
        return Boolean.TRUE.equals(transact(() -> {
            CreatorPayoutRequest request = mongo.findById(requestId, CreatorPayoutRequest.class);
            if (request == null || request.getStatus() != CreatorPayoutRequest.Status.PROCESSING
                    || recipientIndex < 0 || recipientIndex >= request.getRecipients().size()) return false;
            var recipient = request.getRecipients().get(recipientIndex);
            if (recipient.getTransferId() != null || request.getProviderAccountId() == null || request.getTransferGroup() == null
                    || recipient.getCorrelationId() == null) return false;
            Instant now = Instant.now();
            if (recipient.getAuthorizedAt() != null && recipient.getAuthorizedAt().isBefore(now.minus(java.time.Duration.ofHours(23)))) return false;
            var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(request.getWalletId())
                            .and("providerAccountId").is(request.getProviderAccountId()).and("payoutHold").ne(true).and("openRiskIds.0").exists(false).and("availableCents").gte(0)),
                    new Update().inc("dispatchAuthorizationSequence", 1L), CreatorWallet.class);
            if (result.getModifiedCount() != 1) return false;
            Update authorization = new Update();
            if (recipient.getAuthorizedAt() == null) authorization.set("recipients." + recipientIndex + ".authorizedAt", now);
            if (request.getFirstAttemptAt() == null) authorization.set("firstAttemptAt", now);
            if (!authorization.getUpdateObject().isEmpty()) mongo.updateFirst(Query.query(Criteria.where("_id").is(requestId)), authorization, CreatorPayoutRequest.class);
            return true;
        }));
    }

    /** Books confirmed outgoing money even if a newer risk hold interrupted the request. No hold is cleared. */
    public void recordTransfer(String id, int recipientIndex, String transferId, String actor, String reason) {
        if (transferId == null || !transferId.matches("tr_[A-Za-z0-9]+") || actor == null || reason == null) throw new IllegalArgumentException("Verified transfer evidence is required.");
        transact(() -> {
            CreatorPayoutRequest request = mongo.findById(id, CreatorPayoutRequest.class);
            if (request == null || recipientIndex < 0 || recipientIndex >= request.getRecipients().size()
                    || request.getProviderAccountId() == null || request.getTransferGroup() == null) throw new IllegalArgumentException("Payout scope needs reconciliation.");
            var recipient = request.getRecipients().get(recipientIndex);
            if (recipient.getAuthorizedAt() == null) throw new IllegalArgumentException("No recorded outbound authorization exists for this recipient.");
            String receiptId = FinanceSourceKey.stripe(request.isTestMode(), request.getProviderAccountId(), "transfer:" + transferId);
            var prior = mongo.findById(receiptId, FinanceTransferReceipt.class);
            if (prior != null) {
                if (!id.equals(prior.payoutRequestId()) || recipientIndex != prior.recipientIndex()) throw new IllegalStateException("This transfer is already claimed by another payout.");
                if (!transferId.equals(recipient.getTransferId())) throw new IllegalStateException("Transfer receipt and payout require reconciliation.");
                return null;
            }
            if (recipient.getTransferId() != null) throw new IllegalStateException("This recipient already has a different transfer.");
            if (request.getStatus() != CreatorPayoutRequest.Status.PROCESSING && request.getStatus() != CreatorPayoutRequest.Status.REQUIRES_REVIEW) throw new IllegalStateException("Payout is not awaiting transfer confirmation.");
            Instant now = Instant.now();
            mongo.insert(new FinanceTransferReceipt(receiptId, id, recipientIndex, transferId, request.getProviderAccountId(), request.isTestMode(),
                    recipient.getAccountId(), recipient.getAmountCents(), request.getCurrency(), actor, reason, now));
            var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(request.getWalletId())
                            .and("providerAccountId").is(request.getProviderAccountId()).and("reservedCents").gte(recipient.getAmountCents())),
                    new Update().inc("reservedCents", -recipient.getAmountCents()), CreatorWallet.class);
            if (result.getModifiedCount() != 1) throw new IllegalStateException("Payout reservation needs reconciliation.");
            FinanceLedgerEntry entry = new FinanceLedgerEntry(); entry.setId("payout:" + id + ":" + recipientIndex);
            entry.setCreatorId(request.getCreatorId()); entry.setType(FinanceLedgerEntry.LedgerType.PAYOUT);
            entry.setCurrency(request.getCurrency()); entry.setCreatorCents(-recipient.getAmountCents());
            entry.setStatus(FinanceLedgerEntry.EntryStatus.PAID); entry.setCompletedAt(LocalDateTime.now()); entry.setExternalReference(id);
            entry.getMetadata().put("testMode", String.valueOf(request.isTestMode())); entry.getMetadata().put("providerAccountId", request.getProviderAccountId());
            entry.getMetadata().put("paymentStage", "connected_account_transfer"); entry.setStripeReference(transferId); mongo.insert(entry);
            recipient.setTransferId(transferId); recipient.setConfirmedBy(actor); recipient.setConfirmationReason(reason); recipient.setConfirmedAt(now);
            if (request.getRecipients().stream().allMatch(r -> r.getTransferId() != null)) { request.setStatus(CreatorPayoutRequest.Status.TRANSFERRED); request.setCompletedAt(now); }
            mongo.save(request); return null;
        });
    }

    public void requireReview(String id, String reason) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").in(CreatorPayoutRequest.Status.PROCESSING, CreatorPayoutRequest.Status.RESERVED)),
                new Update().set("status", CreatorPayoutRequest.Status.REQUIRES_REVIEW).set("reviewReason", reason), CreatorPayoutRequest.class);
    }

    /** A stale dispatch failure must not pause recipients already confirmed by another worker. */
    public void requireRecipientReview(String id, int recipientIndex, String reason) {
        if (recipientIndex < 0) throw new IllegalArgumentException("Payout recipient not found.");
        transact(() -> {
            CreatorPayoutRequest request = mongo.findById(id, CreatorPayoutRequest.class);
            if (request == null || request.getStatus() != CreatorPayoutRequest.Status.PROCESSING
                    || recipientIndex >= request.getRecipients().size() || request.getRecipients().get(recipientIndex).getTransferId() != null) return null;
            // Indexed null predicates do not reliably select one array element. Read the exact
            // recipient, then write this same document so concurrent confirmations force a retry.
            mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").is(CreatorPayoutRequest.Status.PROCESSING)),
                    new Update().set("status", CreatorPayoutRequest.Status.REQUIRES_REVIEW).set("reviewReason", reason), CreatorPayoutRequest.class);
            return null;
        });
    }

    public CreatorPayoutRequest completeTransfers(String id) {
        CreatorPayoutRequest request = getRequest(id);
        if (request == null) throw new IllegalArgumentException("Payout not found.");
        if (request.getStatus() != CreatorPayoutRequest.Status.TRANSFERRED || request.getRecipients().stream().anyMatch(r -> r.getTransferId() == null)) {
            throw new IllegalStateException("All recipient transfers must be independently confirmed.");
        }
        return request;
    }

    public List<CreatorPayoutRequest> getReviewRequests() {
        return mongo.find(Query.query(Criteria.where("status").in(CreatorPayoutRequest.Status.PROCESSING, CreatorPayoutRequest.Status.REQUIRES_REVIEW))
                .with(org.springframework.data.domain.Sort.by("createdAt")).limit(100), CreatorPayoutRequest.class);
    }

    private <T> T transact(Supplier<T> work) {
        for (int attempt = 0; ; attempt++) {
            try { return transactions.execute(status -> work.get()); }
            catch (RuntimeException failure) {
                Throwable root = failure;
                boolean retryable = false;
                while (root != null) {
                    if (root instanceof MongoException mongoFailure && (mongoFailure.hasErrorLabel("TransientTransactionError")
                            || mongoFailure.hasErrorLabel("UnknownTransactionCommitResult"))) retryable = true;
                    root = root.getCause();
                }
                if (!retryable || attempt >= 4) throw failure;
                try { Thread.sleep(5L << attempt); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Financial transaction retry interrupted.", interrupted); }
            }
        }
    }
}
