package net.modtale.model.finance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Durable reservation and immutable recipient snapshot for an idempotent transfer operation. */
@Document(collection = "creator_payout_requests")
public class CreatorPayoutRequest {
    public enum Status { RESERVED, PROCESSING, TRANSFERRED, REQUIRES_REVIEW, CANCELLED }
    public static class Recipient {
        private String userId;
        private String accountId;
        private long amountCents;
        private String transferId;
        public String getUserId() { return userId; }
        public void setUserId(String id) { userId = id; }
        public String getAccountId() { return accountId; }
        public void setAccountId(String id) { accountId = id; }
        public long getAmountCents() { return amountCents; }
        public void setAmountCents(long amount) { amountCents = amount; }
        public String getTransferId() { return transferId; }
        public void setTransferId(String id) { transferId = id; }
    }
    @Id private String id;
    private String creatorId;
    private String requestedBy;
    private String walletId;
    private String currency;
    private boolean testMode;
    private long amountCents;
    private Status status = Status.RESERVED;
    private Instant createdAt = Instant.now();
    private Instant firstAttemptAt;
    private Instant completedAt;
    private String reviewReason;
    private List<Recipient> recipients = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String id) { creatorId = id; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String id) { requestedBy = id; }
    public String getWalletId() { return walletId; }
    public void setWalletId(String id) { walletId = id; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isTestMode() { return testMode; }
    public void setTestMode(boolean mode) { testMode = mode; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amount) { amountCents = amount; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getFirstAttemptAt() { return firstAttemptAt; }
    public void setFirstAttemptAt(Instant value) { firstAttemptAt = value; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { completedAt = value; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reason) { reviewReason = reason; }
    public List<Recipient> getRecipients() { return recipients; }
    public void setRecipients(List<Recipient> recipients) { this.recipients = recipients; }
}
