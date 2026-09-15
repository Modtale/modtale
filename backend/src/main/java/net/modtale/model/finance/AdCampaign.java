package net.modtale.model.finance;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "ad_campaigns")
public class AdCampaign {

    public enum ProviderType {
        GENERIC_PROVIDER,
        CUSTOM_AFFILIATE
    }

    public enum AdPlacement {
        SIDEBAR_CARD,
        WIDE_BANNER,
        TALL_BANNER
    }

    public static class AdCreative {
        private AdPlacement placement = AdPlacement.SIDEBAR_CARD;
        private String imageUrl;
        private String altText;

        public AdPlacement getPlacement() {
            return placement;
        }

        public void setPlacement(AdPlacement placement) {
            this.placement = placement;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getAltText() {
            return altText;
        }

        public void setAltText(String altText) {
            this.altText = altText;
        }
    }

    @Id
    private String id;

    @Indexed
    private String name;

    @Indexed
    private boolean active = true;

    private ProviderType providerType = ProviderType.CUSTOM_AFFILIATE;

    private String providerName;
    private String providerPlacementKey;

    private String sponsorName;
    private String headline;
    private String body;
    private String callToAction = "Learn more";
    private String imageUrl;
    private List<AdCreative> creatives = new ArrayList<>();

    private String targetUrl;
    private String affiliateParam = "ref";
    private String affiliateCode;

    private int baseRevenuePerClickCents = 3;
    private int weight = 100;
    private boolean testCampaign = false;

    private boolean privacyRespecting = true;
    private boolean nonIntrusive = true;

    private List<String> allowedClassifications = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderPlacementKey() {
        return providerPlacementKey;
    }

    public void setProviderPlacementKey(String providerPlacementKey) {
        this.providerPlacementKey = providerPlacementKey;
    }

    public String getSponsorName() {
        return sponsorName;
    }

    public void setSponsorName(String sponsorName) {
        this.sponsorName = sponsorName;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCallToAction() {
        return callToAction;
    }

    public void setCallToAction(String callToAction) {
        this.callToAction = callToAction;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public List<AdCreative> getCreatives() {
        return creatives;
    }

    public void setCreatives(List<AdCreative> creatives) {
        this.creatives = creatives == null ? new ArrayList<>() : creatives;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getAffiliateParam() {
        return affiliateParam;
    }

    public void setAffiliateParam(String affiliateParam) {
        this.affiliateParam = affiliateParam;
    }

    public String getAffiliateCode() {
        return affiliateCode;
    }

    public void setAffiliateCode(String affiliateCode) {
        this.affiliateCode = affiliateCode;
    }

    public int getBaseRevenuePerClickCents() {
        return baseRevenuePerClickCents;
    }

    public void setBaseRevenuePerClickCents(int baseRevenuePerClickCents) {
        this.baseRevenuePerClickCents = baseRevenuePerClickCents;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean isTestCampaign() {
        return testCampaign;
    }

    public void setTestCampaign(boolean testCampaign) {
        this.testCampaign = testCampaign;
    }

    public boolean isPrivacyRespecting() {
        return privacyRespecting;
    }

    public void setPrivacyRespecting(boolean privacyRespecting) {
        this.privacyRespecting = privacyRespecting;
    }

    public boolean isNonIntrusive() {
        return nonIntrusive;
    }

    public void setNonIntrusive(boolean nonIntrusive) {
        this.nonIntrusive = nonIntrusive;
    }

    public List<String> getAllowedClassifications() {
        return allowedClassifications;
    }

    public void setAllowedClassifications(List<String> allowedClassifications) {
        this.allowedClassifications = allowedClassifications;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
