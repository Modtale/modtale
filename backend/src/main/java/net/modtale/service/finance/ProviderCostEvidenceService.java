package net.modtale.service.finance;

import java.time.Instant;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import net.modtale.model.finance.ProviderCostEvidence;
import net.modtale.model.user.User;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/** Read-only provider retrieval plus immutable evidence storage; never changes earnings or wallet balances. */
@Service
public class ProviderCostEvidenceService {
    private final StripeGatewayService gateway;
    private final MongoTemplate mongo;
    public ProviderCostEvidenceService(StripeGatewayService gateway, MongoTemplate mongo) { this.gateway = gateway; this.mongo = mongo; }
    public record ImportRequest(@NotBlank @Pattern(regexp = "txn_[A-Za-z0-9]+") String balanceTransactionId,
            @NotBlank @Pattern(regexp = "acct_[A-Za-z0-9]+") String expectedAccountId, @NotNull Boolean expectedTestMode,
            @NotBlank @Size(max = 1000) String reason) {}
    public List<ProviderCostEvidence> list(User actor) {
        AdSettlementStagingService.requireReviewer(actor);
        return mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "recordedAt")).limit(100), ProviderCostEvidence.class);
    }
    public ProviderCostEvidence retrieveAndImport(User actor, ImportRequest input) {
        AdSettlementStagingService.requireReviewer(actor); validate(input);
        boolean testMode = gateway.isTestMode();
        if (!gateway.isReconciliationEnabled() || testMode != input.expectedTestMode()
                || !input.expectedAccountId().equals(gateway.getExpectedPlatformAccountId())
                || !gateway.verifyPlatformAccountId(input.expectedAccountId())) throw new IllegalStateException("The configured provider account and mode must be verified before importing costs.");
        // BalanceTransaction has no livemode field. Verify the authenticated account's balance mode explicitly.
        Map<String, Object> balance = gateway.getBalance();
        if (!"balance".equals(balance.get("object")) || !(balance.get("livemode") instanceof Boolean live) || live == testMode)
            throw new IllegalStateException("The authenticated provider balance mode could not be verified.");
        StripeCostEvidence.Snapshot snapshot = StripeCostEvidence.parse(input.balanceTransactionId(), gateway.getBalanceTransaction(input.balanceTransactionId()), Instant.now());
        if (testMode != gateway.isTestMode() || !input.expectedAccountId().equals(gateway.getExpectedPlatformAccountId()))
            throw new IllegalStateException("Provider scope changed while retrieving costs; start a new verified review.");
        String id = FinanceSourceKey.stripe(testMode, input.expectedAccountId(), "provider-cost:" + snapshot.transactionId());
        String digest = snapshot.digest(input.expectedAccountId(), testMode);
        ProviderCostEvidence evidence = new ProviderCostEvidence(id, input.expectedAccountId(), testMode, snapshot.transactionId(), snapshot.currency(),
                snapshot.type(), snapshot.reportingCategory(), snapshot.source(), snapshot.amount(), snapshot.fee(), snapshot.net(), snapshot.cost(),
                snapshot.created(), snapshot.available(), StripeGatewayService.API_VERSION, digest, "UNALLOCATED", actor.getId(), input.reason().trim(), Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        try { return mongo.insert(evidence); }
        catch (DuplicateKeyException duplicate) {
            ProviderCostEvidence original = mongo.findById(id, ProviderCostEvidence.class);
            if (original == null || !digest.equals(original.evidenceDigest())) throw new IllegalArgumentException("This provider transaction already has conflicting immutable evidence. Reconcile the conflict before any allocation.");
            return original;
        }
    }
    static void validate(ImportRequest input) {
        if (input == null || input.balanceTransactionId() == null || !input.balanceTransactionId().matches("txn_[A-Za-z0-9]+")
                || input.expectedAccountId() == null || !input.expectedAccountId().matches("acct_[A-Za-z0-9]+") || input.expectedTestMode() == null
                || input.reason() == null || input.reason().isBlank() || input.reason().length() > 1000)
            throw new IllegalArgumentException("Supply the exact balance transaction, expected account and test/live mode, and a review reason of 1–1000 characters.");
    }
}
