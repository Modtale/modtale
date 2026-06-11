package net.modtale.model.finance;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "finance_ledger_entries")
@CompoundIndexes({
        @CompoundIndex(name = "creator_status_expires_idx", def = "{'creatorId': 1, 'status': 1, 'expiresAt': 1}"),
        @CompoundIndex(name = "created_at_idx", def = "{'createdAt': -1}"),
        @CompoundIndex(name = "type_created_idx", def = "{'type': 1, 'createdAt': -1}")
})
public class FinanceLedgerEntry {

    public enum LedgerType {
        DONATION,
        AD_CLICK,
        AD_IMPRESSION,
        PAYOUT,
        EXPIRED_TRANSFER,
        PLATFORM_CUT,
        MANUAL_ADJUSTMENT
    }

    public enum EntryStatus {
        PENDING,
        AVAILABLE,
        PAID,
        EXPIRED
    }

    @Id
    private String id;

    @Indexed
    private String creatorId;

    @Indexed
    private String projectId;

    @Indexed
    private LedgerType type;

    private long grossCents;
    private long creatorCents;
    private long platformCents;
    private String currency = "usd";

    @Indexed
    private EntryStatus status = EntryStatus.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime availableAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;

    private String stripeReference;
    private String externalReference;
    private boolean recurring;

    private Map<String, String> metadata = new HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public LedgerType getType() {
        return type;
    }

    public void setType(LedgerType type) {
        this.type = type;
    }

    public long getGrossCents() {
        return grossCents;
    }

    public void setGrossCents(long grossCents) {
        this.grossCents = grossCents;
    }

    public long getCreatorCents() {
        return creatorCents;
    }

    public void setCreatorCents(long creatorCents) {
        this.creatorCents = creatorCents;
    }

    public long getPlatformCents() {
        return platformCents;
    }

    public void setPlatformCents(long platformCents) {
        this.platformCents = platformCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public EntryStatus getStatus() {
        return status;
    }

    public void setStatus(EntryStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(LocalDateTime availableAt) {
        this.availableAt = availableAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getStripeReference() {
        return stripeReference;
    }

    public void setStripeReference(String stripeReference) {
        this.stripeReference = stripeReference;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
