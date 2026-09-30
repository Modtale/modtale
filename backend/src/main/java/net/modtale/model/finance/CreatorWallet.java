package net.modtale.model.finance;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Materialized balance, updated in the same transaction as its immutable source-ledger entry. */
@Document(collection = "creator_wallets")
public class CreatorWallet {
    @Id private String id;
    private String creatorId;
    private String currency;
    private boolean testMode;
    private long availableCents;
    private long reservedCents;
    private boolean payoutHold;
    private java.util.List<String> openRiskIds = new java.util.ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isTestMode() { return testMode; }
    public void setTestMode(boolean testMode) { this.testMode = testMode; }
    public long getAvailableCents() { return availableCents; }
    public void setAvailableCents(long amount) { availableCents = amount; }
    public long getReservedCents() { return reservedCents; }
    public void setReservedCents(long amount) { reservedCents = amount; }
    public boolean isPayoutHold() { return payoutHold || (openRiskIds != null && !openRiskIds.isEmpty()); }
    public java.util.List<String> getOpenRiskIds() { return openRiskIds; }
    public void setOpenRiskIds(java.util.List<String> ids) { openRiskIds = ids; }
    public void setPayoutHold(boolean payoutHold) { this.payoutHold = payoutHold; }
}
