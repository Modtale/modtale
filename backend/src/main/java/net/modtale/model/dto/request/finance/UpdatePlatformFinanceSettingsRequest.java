package net.modtale.model.dto.request.finance;

public class UpdatePlatformFinanceSettingsRequest {
    private Integer defaultAdRevenuePerClickCents;
    private Integer minPayoutCents;

    public Integer getDefaultAdRevenuePerClickCents() {
        return defaultAdRevenuePerClickCents;
    }

    public void setDefaultAdRevenuePerClickCents(Integer defaultAdRevenuePerClickCents) {
        this.defaultAdRevenuePerClickCents = defaultAdRevenuePerClickCents;
    }

    public Integer getMinPayoutCents() {
        return minPayoutCents;
    }

    public void setMinPayoutCents(Integer minPayoutCents) {
        this.minPayoutCents = minPayoutCents;
    }

}
