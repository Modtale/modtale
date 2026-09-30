package net.modtale.model.finance;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Immutable, evidence-bound operator decision. Fee amounts are cumulative targets, not repeated charges. */
@Document(collection = "finance_dispute_resolutions")
public record FinanceDisputeResolution(@Id String id, String caseId, String disputeId, String evidenceDigest,
        String providerAccountId, boolean testMode, String providerStatus, long actualFeeCents,
        long creatorFeeCents, String reviewerId, String reason, Instant createdAt, String currency, long disputedCents,
        long principalMovementCents, long returnedPrincipalCents, java.util.List<FinanceDisputeBalance> balanceTransactions) {}
