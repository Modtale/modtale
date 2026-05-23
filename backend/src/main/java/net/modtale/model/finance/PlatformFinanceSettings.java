package net.modtale.model.finance;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "platform_finance_settings")
public class PlatformFinanceSettings {

    @Id
    private String id = "platform";

    private int adCreatorSplitBps = 9000;
    private int donationPlatformCutBps = 1000;
    private int fundExpiryDays = 365;
    private int defaultAdRevenuePerClickCents = 3;
    private int minPayoutCents = 1000;
    private boolean adTestModeEnabled = false;
    private String currency = "usd";
    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getAdCreatorSplitBps() {
        return adCreatorSplitBps;
    }

    public void setAdCreatorSplitBps(int adCreatorSplitBps) {
        this.adCreatorSplitBps = Math.max(0, Math.min(10000, adCreatorSplitBps));
    }

    public int getDonationPlatformCutBps() {
        return donationPlatformCutBps;
    }

    public void setDonationPlatformCutBps(int donationPlatformCutBps) {
        this.donationPlatformCutBps = Math.max(0, Math.min(10000, donationPlatformCutBps));
    }

    public int getFundExpiryDays() {
        return fundExpiryDays;
    }

    public void setFundExpiryDays(int fundExpiryDays) {
        this.fundExpiryDays = Math.max(30, fundExpiryDays);
    }

    public int getDefaultAdRevenuePerClickCents() {
        return defaultAdRevenuePerClickCents;
    }

    public void setDefaultAdRevenuePerClickCents(int defaultAdRevenuePerClickCents) {
        this.defaultAdRevenuePerClickCents = Math.max(0, defaultAdRevenuePerClickCents);
    }

    public int getMinPayoutCents() {
        return minPayoutCents;
    }

    public void setMinPayoutCents(int minPayoutCents) {
        this.minPayoutCents = Math.max(100, minPayoutCents);
    }

    public boolean isAdTestModeEnabled() {
        return adTestModeEnabled;
    }

    public void setAdTestModeEnabled(boolean adTestModeEnabled) {
        this.adTestModeEnabled = adTestModeEnabled;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
