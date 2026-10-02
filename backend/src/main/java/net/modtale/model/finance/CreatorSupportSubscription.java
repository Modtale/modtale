package net.modtale.model.finance;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "creator_support_subscriptions")
public class CreatorSupportSubscription {
    @Id private String id;
    private String intentId;
    private String donorUserId;
    private String creatorId;
    private String projectId;
    private String customerId;
    private String providerAccountId;
    private long amountCents;
    private int platformCutBps;
    private String currency;
    private boolean testMode;
    private String status;
    private boolean cancelAtPeriodEnd;
    private Instant updatedAt = Instant.now();
    public String getId() { return id; } public void setId(String value) { id = value; }
    public String getIntentId() { return intentId; } public void setIntentId(String value) { intentId = value; }
    public String getDonorUserId() { return donorUserId; } public void setDonorUserId(String value) { donorUserId = value; }
    public String getCreatorId() { return creatorId; } public void setCreatorId(String value) { creatorId = value; }
    public String getProjectId() { return projectId; } public void setProjectId(String value) { projectId = value; }
    public String getProviderAccountId() { return providerAccountId; } public void setProviderAccountId(String value) { providerAccountId = value; }
    public String getCustomerId() { return customerId; } public void setCustomerId(String value) { customerId = value; }
    public long getAmountCents() { return amountCents; } public void setAmountCents(long value) { amountCents = value; }
    public int getPlatformCutBps() { return platformCutBps; } public void setPlatformCutBps(int value) { platformCutBps = value; }
    public String getCurrency() { return currency; } public void setCurrency(String value) { currency = value; }
    public boolean isTestMode() { return testMode; } public void setTestMode(boolean value) { testMode = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; } public void setCancelAtPeriodEnd(boolean value) { cancelAtPeriodEnd = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}
