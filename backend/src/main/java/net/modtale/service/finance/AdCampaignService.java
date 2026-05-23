package net.modtale.service.finance;

import net.modtale.model.finance.AdCampaign;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.finance.PlatformFinanceSettings;
import net.modtale.model.project.Project;
import net.modtale.repository.finance.AdCampaignRepository;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import net.modtale.service.project.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdCampaignService {

    @Autowired private EarningsAccountService financeAccountService;
    @Autowired private AdCampaignRepository adCampaignRepository;
    @Autowired private FinanceLedgerEntryRepository ledgerRepository;
    @Autowired private ProjectService projectService;
    @Autowired private RevenueOpsSupport core;

    @Value("${app.finance.ads.test-mode-enabled:false}")
    private boolean defaultAdTestModeEnabled;

    public Map<String, Object> getAdSlotForProject(String projectId, String placementRaw) {
        if (defaultAdTestModeEnabled) {
            Map<String, Object> testSlot = resolveAdSlot(projectId, true, placementRaw);
            Object enabled = testSlot.get("enabled");
            if (enabled instanceof Boolean b && b) {
                return testSlot;
            }
        }
        return resolveAdSlot(projectId, false, placementRaw);
    }

    public Map<String, Object> getTestAdSlotForProject(String projectId, String placementRaw) {
        if (!defaultAdTestModeEnabled) {
            return Map.of("enabled", false, "reason", "TEST_MODE_DISABLED");
        }
        return resolveAdSlot(projectId, true, placementRaw);
    }

    public List<Map<String, Object>> getAdCampaigns() {
        return adCampaignRepository.findAll().stream()
                .sorted(Comparator.comparing(AdCampaign::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .map(core::toCampaignMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> createAdCampaign(Map<String, Object> payload) {
        AdCampaign campaign = new AdCampaign();
        applyCampaignPayload(campaign, payload, true);
        campaign.setCreatedAt(LocalDateTime.now());
        campaign.setUpdatedAt(LocalDateTime.now());
        return core.toCampaignMap(adCampaignRepository.save(campaign));
    }

    public Map<String, Object> updateAdCampaign(String campaignId, Map<String, Object> payload) {
        AdCampaign campaign = adCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Ad campaign not found"));
        applyCampaignPayload(campaign, payload, false);
        campaign.setUpdatedAt(LocalDateTime.now());
        return core.toCampaignMap(adCampaignRepository.save(campaign));
    }

    public Map<String, Object> setCampaignActiveState(String campaignId, boolean active) {
        AdCampaign campaign = adCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Ad campaign not found"));
        campaign.setActive(active);
        campaign.setUpdatedAt(LocalDateTime.now());
        return core.toCampaignMap(adCampaignRepository.save(campaign));
    }

    public void trackAdImpression(String campaignId, String projectId, String clientIp) {
        if (!core.shouldTrackEvent("impression", campaignId, projectId, clientIp)) return;

        FinanceLedgerEntry entry = new FinanceLedgerEntry();
        entry.setCreatorId(core.resolveCreatorId(projectId));
        entry.setProjectId(projectId);
        entry.setType(FinanceLedgerEntry.LedgerType.AD_IMPRESSION);
        entry.setGrossCents(0);
        entry.setCreatorCents(0);
        entry.setPlatformCents(0);
        entry.setCurrency(financeAccountService.getSettings().getCurrency());
        entry.setStatus(FinanceLedgerEntry.EntryStatus.PAID);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setAvailableAt(LocalDateTime.now());
        entry.setCompletedAt(LocalDateTime.now());
        entry.getMetadata().put("campaignId", campaignId);
        entry.getMetadata().put("tracked", "aggregate_only");
        ledgerRepository.save(entry);
    }

    public String registerAdClickAndResolveUrl(String campaignId, String projectId, String clientIp) {
        AdCampaign campaign = adCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Ad campaign not found"));

        String targetUrl = core.appendAffiliateParams(campaign.getTargetUrl(), campaign.getAffiliateParam(), campaign.getAffiliateCode());

        Project project = projectService.getProjectById(projectId);
        if (project == null || !project.isAdsEnabled()) {
            return targetUrl;
        }

        if (!core.shouldTrackEvent("click", campaignId, projectId, clientIp)) {
            return targetUrl;
        }

        PlatformFinanceSettings settings = financeAccountService.getSettings();
        long gross = campaign.getBaseRevenuePerClickCents() > 0
                ? campaign.getBaseRevenuePerClickCents()
                : settings.getDefaultAdRevenuePerClickCents();

        long creatorCut = Math.round((gross * settings.getAdCreatorSplitBps()) / 10000.0);
        long platformCut = gross - creatorCut;

        FinanceLedgerEntry entry = new FinanceLedgerEntry();
        entry.setCreatorId(project.getAuthorId());
        entry.setProjectId(project.getId());
        entry.setType(FinanceLedgerEntry.LedgerType.AD_CLICK);
        entry.setGrossCents(gross);
        entry.setCreatorCents(creatorCut);
        entry.setPlatformCents(platformCut);
        entry.setCurrency(settings.getCurrency());
        entry.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setAvailableAt(LocalDateTime.now());
        entry.setExpiresAt(LocalDateTime.now().plusDays(settings.getFundExpiryDays()));
        entry.getMetadata().put("campaignId", campaign.getId());
        entry.getMetadata().put("providerType", campaign.getProviderType().name());
        entry.getMetadata().put("tracked", "aggregate_only");
        ledgerRepository.save(entry);

        return targetUrl;
    }

    private Map<String, Object> resolveAdSlot(String projectId, boolean testOnly, String placementRaw) {
        Project project = projectService.getProjectById(projectId);
        if (project == null || !project.isAdsEnabled()) {
            return Map.of(
                    "enabled", false,
                    "reason", project == null ? "PROJECT_NOT_FOUND" : "CREATOR_DISABLED"
            );
        }

        List<AdCampaign> activeCampaigns = testOnly
                ? adCampaignRepository.findByActiveTrueAndTestCampaignTrue()
                : adCampaignRepository.findByActiveTrueAndTestCampaignFalse();

        AdCampaign.AdPlacement placement = core.parsePlacement(placementRaw);

        List<AdCampaign> candidates = activeCampaigns.stream()
                .filter(AdCampaign::isPrivacyRespecting)
                .filter(AdCampaign::isNonIntrusive)
                .filter(campaign -> campaign.getAllowedClassifications() == null
                        || campaign.getAllowedClassifications().isEmpty()
                        || (project.getClassification() != null && campaign.getAllowedClassifications().contains(project.getClassification().name())))
                .filter(campaign -> !testOnly || core.hasRenderableCreativeForPlacement(campaign, placement))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return Map.of("enabled", false, "reason", "NO_ACTIVE_CAMPAIGNS");
        }

        AdCampaign chosen = core.weightedPick(candidates);
        AdCampaign.AdCreative creative = core.chooseCreative(chosen, placement);
        String imageUrl = creative != null && creative.getImageUrl() != null && !creative.getImageUrl().isBlank()
                ? creative.getImageUrl()
                : chosen.getImageUrl();

        Map<String, Object> ad = new HashMap<>();
        ad.put("enabled", true);
        ad.put("campaignId", chosen.getId());
        ad.put("providerType", chosen.getProviderType());
        ad.put("providerName", chosen.getProviderName());
        ad.put("sponsorName", chosen.getSponsorName());
        ad.put("headline", chosen.getHeadline());
        ad.put("body", chosen.getBody());
        ad.put("callToAction", chosen.getCallToAction());
        ad.put("imageUrl", imageUrl);
        ad.put("placement", placement.name());
        ad.put("creativeAltText", creative != null ? creative.getAltText() : null);
        ad.put("clickUrl", "/api/v1/finance/ads/click/" + chosen.getId() + "?projectId=" + project.getId());
        ad.put("testCampaign", chosen.isTestCampaign());
        ad.put("privacyLabel", "Privacy-respecting ad: no personal profile tracking.");
        ad.put("creatorRevenueSharePercent", financeAccountService.getSettings().getAdCreatorSplitBps() / 100.0);
        return ad;
    }

    private void applyCampaignPayload(AdCampaign campaign, Map<String, Object> payload, boolean isCreate) {
        if (payload == null) {
            throw new IllegalArgumentException("Campaign payload is required.");
        }

        String name = core.asString(payload.get("name"));
        if (isCreate && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("Campaign name is required.");
        }
        if (name != null && !name.isBlank()) campaign.setName(name);

        String providerTypeRaw = core.asString(payload.get("providerType"));
        if (providerTypeRaw != null && !providerTypeRaw.isBlank()) {
            try {
                campaign.setProviderType(AdCampaign.ProviderType.valueOf(providerTypeRaw));
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Invalid campaign provider type.");
            }
        }

        if (payload.containsKey("providerName")) campaign.setProviderName(core.asString(payload.get("providerName")));
        if (payload.containsKey("providerPlacementKey")) campaign.setProviderPlacementKey(core.asString(payload.get("providerPlacementKey")));
        if (payload.containsKey("sponsorName")) campaign.setSponsorName(core.asString(payload.get("sponsorName")));
        if (payload.containsKey("headline")) campaign.setHeadline(core.asString(payload.get("headline")));
        if (payload.containsKey("body")) campaign.setBody(core.asString(payload.get("body")));
        if (payload.containsKey("callToAction")) campaign.setCallToAction(core.asString(payload.get("callToAction")));
        if (payload.containsKey("imageUrl")) campaign.setImageUrl(core.asString(payload.get("imageUrl")));
        if (payload.containsKey("targetUrl")) campaign.setTargetUrl(core.asString(payload.get("targetUrl")));
        if (payload.containsKey("affiliateParam")) campaign.setAffiliateParam(core.asString(payload.get("affiliateParam")));
        if (payload.containsKey("affiliateCode")) campaign.setAffiliateCode(core.asString(payload.get("affiliateCode")));
        if (payload.containsKey("active")) campaign.setActive(core.asBoolean(payload.get("active")));
        if (payload.containsKey("privacyRespecting")) campaign.setPrivacyRespecting(core.asBoolean(payload.get("privacyRespecting")));
        if (payload.containsKey("nonIntrusive")) campaign.setNonIntrusive(core.asBoolean(payload.get("nonIntrusive")));
        if (payload.containsKey("testCampaign")) campaign.setTestCampaign(core.asBoolean(payload.get("testCampaign")));
        if (payload.containsKey("baseRevenuePerClickCents")) campaign.setBaseRevenuePerClickCents(Math.max(0, core.asInt(payload.get("baseRevenuePerClickCents"), campaign.getBaseRevenuePerClickCents())));
        if (payload.containsKey("weight")) campaign.setWeight(Math.max(1, core.asInt(payload.get("weight"), campaign.getWeight())));

        if (payload.containsKey("allowedClassifications")) {
            Object raw = payload.get("allowedClassifications");
            List<String> classifications = new ArrayList<>();
            if (raw instanceof List<?> list) {
                for (Object value : list) {
                    if (value == null) continue;
                    String normalized = String.valueOf(value).trim().toUpperCase();
                    if (!normalized.isBlank()) classifications.add(normalized);
                }
            }
            campaign.setAllowedClassifications(classifications);
        }

        if (payload.containsKey("creatives")) {
            Object raw = payload.get("creatives");
            List<AdCampaign.AdCreative> creatives = new ArrayList<>();
            if (raw instanceof List<?> list) {
                for (Object entry : list) {
                    if (!(entry instanceof Map<?, ?> map)) continue;
                    String imageUrl = core.asString(map.get("imageUrl"));
                    if (imageUrl == null || imageUrl.isBlank()) continue;
                    AdCampaign.AdCreative creative = new AdCampaign.AdCreative();
                    creative.setImageUrl(imageUrl);
                    creative.setAltText(core.asString(map.get("altText")));
                    try {
                        String placementRaw = core.asString(map.get("placement"));
                        if (placementRaw != null && !placementRaw.isBlank()) {
                            creative.setPlacement(AdCampaign.AdPlacement.valueOf(placementRaw.trim().toUpperCase()));
                        }
                    } catch (Exception ignored) {}
                    creatives.add(creative);
                }
            }
            campaign.setCreatives(creatives);
        }
    }
}
