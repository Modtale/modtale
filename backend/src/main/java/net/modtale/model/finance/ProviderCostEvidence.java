package net.modtale.model.finance;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Immutable provider cost evidence. It has no creator attribution or wallet effect. */
@Document(collection = "provider_cost_evidence")
public record ProviderCostEvidence(@Id String id, String providerAccountId, boolean testMode,
        String balanceTransactionId, String currency, String providerType, String reportingCategory, String source,
        long amountMinorUnits, long feeMinorUnits, long netMinorUnits, long costMinorUnits,
        Instant providerCreatedAt, Instant availableAt, String apiVersion, String evidenceDigest,
        String allocationStatus, String recordedBy, String reason, Instant recordedAt) {}
