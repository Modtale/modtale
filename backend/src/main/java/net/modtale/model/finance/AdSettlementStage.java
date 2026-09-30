package net.modtale.model.finance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Review evidence only. This collection is never a funding source for creator wallets. */
@Document(collection = "ad_settlement_stages")
public record AdSettlementStage(@Id String id, String provider, String providerAccount, String reportId,
        String depositId, String currency, int revision, String status, List<Snapshot> snapshots,
        List<AuditEvent> audit, Instant updatedAt) {
    public record Activity(String projectId, String creatorId, String title, long pageviews, long launcherDownloads,
            long frontendDownloads, long apiDownloads, long provisionalPoints, long provisionalCreatorCents,
            String eligibilityNote) {}
    public record Snapshot(int revision, LocalDate from, LocalDate through, long reportedCollectedCents,
            String reportSha256, String inputDigest, String activityDigest, int creatorShareBps,
            long provisionalCreatorPoolCents, long provisionalPlatformCents, long unallocatedCreatorCents,
            String activityRule, List<Activity> projects, Instant capturedAt) {}
    public record AuditEvent(String operationId, String action, int revision, String actorId, String reason,
            String inputDigest, Instant createdAt) {}
}
