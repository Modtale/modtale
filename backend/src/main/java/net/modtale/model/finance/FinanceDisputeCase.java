package net.modtale.model.finance;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/** Contains reconciliation facts only, never evidence documents, customer identity, or card data. */
@Document(collection = "finance_dispute_cases")
public record FinanceDisputeCase(@Id String id, String disputeId, String chargeId, String creatorId,
        String currency, boolean testMode, String providerStatus, long disputedCents, Long actualFeeCents,
        String reviewStatus, Instant updatedAt, String providerAccountId, String originalCreditId,
        Long principalMovementCents, long returnedPrincipalCents, boolean evidenceReady, String evidenceDigest,
        List<FinanceDisputeBalance> balanceTransactions, @Version Long version) {}
