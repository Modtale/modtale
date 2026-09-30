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
                        || !Objects.equals(existing.getCurrency(), credit.getCurrency())) {
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
                            .and("availableCents").gte(amountCents).and("payoutHold").ne(true)),
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

    public CreatorPayoutRequest getRequest(String id) { return mongo.findById(id, CreatorPayoutRequest.class); }

    public List<CreatorPayoutRequest> getUnfinishedRequests() {
        return mongo.find(Query.query(Criteria.where("status").in(CreatorPayoutRequest.Status.RESERVED, CreatorPayoutRequest.Status.PROCESSING)).limit(100), CreatorPayoutRequest.class);
    }

    public CreatorPayoutRequest markAttempted(String id) {
        return mongo.findAndModify(Query.query(Criteria.where("_id").is(id).and("firstAttemptAt").is(null)
                        .and("status").is(CreatorPayoutRequest.Status.RESERVED)),
                new Update().set("firstAttemptAt", Instant.now()).set("status", CreatorPayoutRequest.Status.PROCESSING),
                FindAndModifyOptions.options().returnNew(true), CreatorPayoutRequest.class);
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
            }
        }
    }
}
