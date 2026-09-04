package net.modtale.model.dto.request.finance;

public class UpdateProjectMonetizationRequest {
    private Boolean adsEnabled;
    private Boolean donationsEnabled;
    private Integer suggestedDonationCents;
    private Boolean donationRecurringDefault;
    private Integer donationPlatformCutBps;

    public Boolean getAdsEnabled() {
        return adsEnabled;
    }

    public void setAdsEnabled(Boolean adsEnabled) {
        this.adsEnabled = adsEnabled;
    }

    public Boolean getDonationsEnabled() {
        return donationsEnabled;
    }

    public void setDonationsEnabled(Boolean donationsEnabled) {
        this.donationsEnabled = donationsEnabled;
    }

    public Integer getSuggestedDonationCents() {
        return suggestedDonationCents;
    }

    public void setSuggestedDonationCents(Integer suggestedDonationCents) {
        this.suggestedDonationCents = suggestedDonationCents;
    }

    public Boolean getDonationRecurringDefault() {
        return donationRecurringDefault;
    }

    public void setDonationRecurringDefault(Boolean donationRecurringDefault) {
        this.donationRecurringDefault = donationRecurringDefault;
    }

    public Integer getDonationPlatformCutBps() {
        return donationPlatformCutBps;
    }

    public void setDonationPlatformCutBps(Integer donationPlatformCutBps) {
        this.donationPlatformCutBps = donationPlatformCutBps;
    }
}
