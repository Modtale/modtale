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
                        || !Objects.equals(existing.getMetadata().get("testMode"), String.valueOf(testMode))) {
                    throw new IllegalStateException("A conflicting settlement already uses this source reference.");
                }
                return null;
            }
            mongo.insert(credit);
            mongo.upsert(Query.query(Criteria.where("_id").is(id)), new Update()
                    .setOnInsert("creatorId", credit.getCreatorId()).setOnInsert("currency", credit.getCurrency())
                    .setOnInsert("testMode", testMode).setOnInsert("reservedCents", 0L).setOnInsert("payoutHold", false)
                    .inc("availableCents", credit.getCreatorCents()), CreatorWallet.class);
            return null;
        });
    }

    public CreatorPayoutRequest reserve(String creatorId, String requesterId, String currency, boolean testMode,
            String requestKey, long amountCents, long minimumCents, List<CreatorPayoutRequest.Recipient> recipients) {
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
                if (existing.getAmountCents() != amountCents || !Objects.equals(existing.getRequestedBy(), requesterId)) {
                    throw new IllegalArgumentException("This request key was already used for a different payout.");
                }
                return existing;
            }
            var wallet = mongo.findAndModify(Query.query(Criteria.where("_id").is(walletId)
                            .and("availableCents").gte(amountCents).and("payoutHold").ne(true).and("openRiskIds.0").exists(false)),
                    new Update().inc("availableCents", -amountCents).inc("reservedCents", amountCents),
                    FindAndModifyOptions.options().returnNew(true), CreatorWallet.class);
            if (wallet == null) throw new IllegalStateException("Insufficient settled funds or the account is on a payout hold.");
            CreatorPayoutRequest request = new CreatorPayoutRequest();
            request.setId(payoutId); request.setWalletId(walletId); request.setCreatorId(creatorId);
            request.setRequestedBy(requesterId); request.setCurrency(currency); request.setTestMode(testMode);
            request.setAmountCents(amountCents); request.setRecipients(recipients);
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
        transact(() -> {
            FinanceLedgerEntry existing = mongo.findById(adjustmentId, FinanceLedgerEntry.class);
            if (existing != null) {
                if (!Objects.equals(existing.getExternalReference(), originalCreditId) || existing.getGrossCents() != -refundedGrossCents
                        || !Objects.equals(existing.getProcessorFeeCents(), providerRefundFeeCents) || existing.getType() != type) throw new IllegalStateException("Conflicting refund source.");
                return null;
            }
            FinanceLedgerEntry original = mongo.findById(originalCreditId, FinanceLedgerEntry.class);
            if (original == null || original.getGrossCents() <= 0 || !Boolean.valueOf(original.getMetadata().get("testMode")).equals(testMode)) {
                throw new IllegalStateException("The original payment must be reconciled before its refund.");
            }
            List<FinanceLedgerEntry> prior = mongo.find(Query.query(Criteria.where("externalReference").is(originalCreditId)
                    .and("type").in(FinanceLedgerEntry.LedgerType.REFUND_ADJUSTMENT, FinanceLedgerEntry.LedgerType.DISPUTE_ADJUSTMENT)), FinanceLedgerEntry.class);
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
            return null;
        });
    }

    public List<CreatorPayoutRequest> getRecentRequests(String creatorId, boolean testMode) {
        return mongo.find(Query.query(Criteria.where("creatorId").is(creatorId).and("testMode").is(testMode))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")).limit(20), CreatorPayoutRequest.class);
    }

    public CreatorPayoutRequest getRequest(String id) { return mongo.findById(id, CreatorPayoutRequest.class); }

    public List<CreatorPayoutRequest> getUnfinishedRequests(boolean testMode) {
        return mongo.find(Query.query(Criteria.where("status").in(CreatorPayoutRequest.Status.RESERVED, CreatorPayoutRequest.Status.PROCESSING).and("testMode").is(testMode)).limit(100), CreatorPayoutRequest.class);
    }

    public CreatorPayoutRequest markAttempted(String id) {
        return mongo.findAndModify(Query.query(Criteria.where("_id").is(id).and("firstAttemptAt").is(null)
                        .and("status").is(CreatorPayoutRequest.Status.RESERVED)),
                new Update().set("firstAttemptAt", Instant.now()).set("status", CreatorPayoutRequest.Status.PROCESSING),
                FindAndModifyOptions.options().returnNew(true), CreatorPayoutRequest.class);
    }

    /** Linearizes each outbound authorization against risk holds on the same wallet document. */
    public boolean authorizeRecipientTransfer(String requestId, int recipientIndex) {
        return Boolean.TRUE.equals(transact(() -> {
            CreatorPayoutRequest request = mongo.findById(requestId, CreatorPayoutRequest.class);
            if (request == null || request.getStatus() != CreatorPayoutRequest.Status.PROCESSING
                    || recipientIndex < 0 || recipientIndex >= request.getRecipients().size()) return false;
            if (request.getRecipients().get(recipientIndex).getTransferId() != null) return false;
            var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(request.getWalletId())
                            .and("payoutHold").ne(true).and("openRiskIds.0").exists(false).and("availableCents").gte(0)),
                    new Update().inc("dispatchAuthorizationSequence", 1L), CreatorWallet.class);
            if (result.getModifiedCount() != 1) return false;
            mongo.updateFirst(Query.query(Criteria.where("_id").is(requestId).and("status").is(CreatorPayoutRequest.Status.PROCESSING)),
                    new Update().set("recipients." + recipientIndex + ".authorizedAt", Instant.now()), CreatorPayoutRequest.class);
            return true;
        }));
    }

    public void recordTransfer(String id, int recipientIndex, String transferId) {
        if (transferId == null || !transferId.startsWith("tr_")) throw new IllegalArgumentException("Invalid provider transfer reference.");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").is(CreatorPayoutRequest.Status.PROCESSING)
                        .and("recipients." + recipientIndex + ".transferId").is(null)),
                new Update().set("recipients." + recipientIndex + ".transferId", transferId), CreatorPayoutRequest.class);
    }

    public void requireReview(String id, String reason) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").in(CreatorPayoutRequest.Status.PROCESSING, CreatorPayoutRequest.Status.RESERVED)),
                new Update().set("status", CreatorPayoutRequest.Status.REQUIRES_REVIEW).set("reviewReason", reason), CreatorPayoutRequest.class);
    }

    public CreatorPayoutRequest completeTransfers(String id) {
        return transact(() -> {
            CreatorPayoutRequest request = mongo.findById(id, CreatorPayoutRequest.class);
            if (request == null) throw new IllegalArgumentException("Payout not found.");
            if (request.getStatus() == CreatorPayoutRequest.Status.TRANSFERRED) return request;
            if (request.getStatus() != CreatorPayoutRequest.Status.PROCESSING || request.getRecipients().stream().anyMatch(r -> r.getTransferId() == null)) {
                throw new IllegalStateException("All recipient transfers must be confirmed before releasing the reservation.");
            }
            var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(request.getWalletId()).and("reservedCents").gte(request.getAmountCents())),
                    new Update().inc("reservedCents", -request.getAmountCents()), CreatorWallet.class);
            if (result.getModifiedCount() != 1) throw new IllegalStateException("Payout reservation needs reconciliation.");
            FinanceLedgerEntry entry = new FinanceLedgerEntry(); entry.setId("payout:" + id);
            entry.setCreatorId(request.getCreatorId()); entry.setType(FinanceLedgerEntry.LedgerType.PAYOUT);
            entry.setCurrency(request.getCurrency()); entry.setCreatorCents(-request.getAmountCents());
            entry.setStatus(FinanceLedgerEntry.EntryStatus.PAID); entry.setCompletedAt(LocalDateTime.now());
            entry.setExternalReference(id); entry.getMetadata().put("testMode", String.valueOf(request.isTestMode()));
            entry.getMetadata().put("paymentStage", "connected_account_transfer");
            entry.setStripeReference(String.join(",", request.getRecipients().stream().map(CreatorPayoutRequest.Recipient::getTransferId).toList()));
            mongo.insert(entry);
            request.setStatus(CreatorPayoutRequest.Status.TRANSFERRED); request.setCompletedAt(Instant.now());
            return mongo.save(request);
        });
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
