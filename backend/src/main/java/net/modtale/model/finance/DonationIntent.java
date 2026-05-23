package net.modtale.model.finance;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "donation_intents")
@CompoundIndexes({
        @CompoundIndex(name = "project_created_idx", def = "{'projectId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "status_expires_idx", def = "{'status': 1, 'expiresAt': 1}")
})
public class DonationIntent {

    public enum DonationStatus {
        PENDING,
        COMPLETED,
        FAILED,
        EXPIRED
    }

    @Id
    private String id;

    @Indexed
    private String projectId;

    @Indexed
    private String creatorId;
    private String donorUserId;
    private boolean guestDonation;

    private long amountCents;
    private long creatorCents;
    private long platformCents;
    private boolean recurring;
    private String currency = "usd";

    @Indexed
    private DonationStatus status = DonationStatus.PENDING;

    private String stripeSessionId;
    private String checkoutUrl;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt = LocalDateTime.now().plusHours(12);

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public String getDonorUserId() {
        return donorUserId;
    }

    public void setDonorUserId(String donorUserId) {
        this.donorUserId = donorUserId;
    }

    public boolean isGuestDonation() {
        return guestDonation;
    }

    public void setGuestDonation(boolean guestDonation) {
        this.guestDonation = guestDonation;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
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

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public DonationStatus getStatus() {
        return status;
    }

    public void setStatus(DonationStatus status) {
        this.status = status;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
