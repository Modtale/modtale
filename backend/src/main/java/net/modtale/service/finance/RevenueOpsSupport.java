package net.modtale.service.finance;

import net.modtale.model.finance.AdCampaign;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.ProjectService;
import net.modtale.service.security.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

@Component
public class RevenueOpsSupport {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectService projectService;
    @Autowired AccessControlService accessControlService;
    @Autowired StripeGatewayService stripeGatewayService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final Map<String, LocalDateTime> adDebounce = new ConcurrentHashMap<>();

    public List<Map<String, Object>> buildDailySeries(
            List<FinanceLedgerEntry> source,
            LocalDate start,
            LocalDate end,
            Set<FinanceLedgerEntry.LedgerType> allowedTypes,
            ToLongFunction<FinanceLedgerEntry> mapper,
            Predicate<FinanceLedgerEntry> extraFilter
    ) {
        Map<LocalDate, Long> buckets = new HashMap<>();
        for (FinanceLedgerEntry entry : source) {
            if (entry.getCreatedAt() == null) continue;
            LocalDate day = entry.getCreatedAt().toLocalDate();
            if (day.isBefore(start) || day.isAfter(end)) continue;
            if (!allowedTypes.contains(entry.getType())) continue;
            if (!extraFilter.test(entry)) continue;
            buckets.merge(day, mapper.applyAsLong(entry), Long::sum);
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            Map<String, Object> row = new HashMap<>();
            row.put("date", day.format(DATE_FMT));
            row.put("count", buckets.getOrDefault(day, 0L));
            response.add(row);
        }
        return response;
    }

    public int parseRangeDays(String range) {
        if ("7d".equalsIgnoreCase(range)) return 7;
        if ("90d".equalsIgnoreCase(range)) return 90;
        if ("1y".equalsIgnoreCase(range)) return 365;
        return 30;
    }

    public AdCampaign weightedPick(List<AdCampaign> campaigns) {
        int total = campaigns.stream().mapToInt(c -> Math.max(1, c.getWeight())).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);

        int cursor = 0;
        for (AdCampaign campaign : campaigns) {
            cursor += Math.max(1, campaign.getWeight());
            if (roll < cursor) {
                return campaign;
            }
        }
        return campaigns.get(0);
    }

    public AdCampaign.AdPlacement parsePlacement(String placementRaw) {
        if (placementRaw == null || placementRaw.isBlank()) return AdCampaign.AdPlacement.SIDEBAR_CARD;
        try {
            return AdCampaign.AdPlacement.valueOf(placementRaw.trim().toUpperCase());
        } catch (Exception ignored) {
            return AdCampaign.AdPlacement.SIDEBAR_CARD;
        }
    }

    public AdCampaign.AdCreative chooseCreative(AdCampaign campaign, AdCampaign.AdPlacement placement) {
        if (campaign.getCreatives() == null || campaign.getCreatives().isEmpty()) return null;
        for (AdCampaign.AdCreative creative : campaign.getCreatives()) {
            if (creative == null || creative.getImageUrl() == null || creative.getImageUrl().isBlank()) continue;
            AdCampaign.AdPlacement creativePlacement = creative.getPlacement() == null
                    ? AdCampaign.AdPlacement.SIDEBAR_CARD
                    : creative.getPlacement();
            if (creativePlacement == placement) return creative;
        }
        for (AdCampaign.AdCreative creative : campaign.getCreatives()) {
            if (creative != null && creative.getImageUrl() != null && !creative.getImageUrl().isBlank()) {
                return creative;
            }
        }
        return null;
    }

    public boolean hasRenderableCreativeForPlacement(AdCampaign campaign, AdCampaign.AdPlacement placement) {
        if (campaign == null || campaign.getCreatives() == null) return false;
        for (AdCampaign.AdCreative creative : campaign.getCreatives()) {
            if (creative == null) continue;
            AdCampaign.AdPlacement creativePlacement = creative.getPlacement() == null
                    ? AdCampaign.AdPlacement.SIDEBAR_CARD
                    : creative.getPlacement();
            if (creativePlacement == placement && creative.getImageUrl() != null && !creative.getImageUrl().isBlank()) {
                return true;
            }
        }
        return false;
    }

    public String appendAffiliateParams(String targetUrl, String param, String code) {
        if (targetUrl == null || targetUrl.isBlank()) return normalizeFrontendUrl();
        if (code == null || code.isBlank()) return targetUrl;
        String queryParam = (param == null || param.isBlank()) ? "ref" : param;

        try {
            return UriComponentsBuilder.fromUriString(targetUrl)
                    .queryParam(queryParam, code)
                    .build(true)
                    .toUriString();
        } catch (Exception e) {
            String separator = targetUrl.contains("?") ? "&" : "?";
            return targetUrl + separator + queryParam + "=" + code;
        }
    }

    public String resolveCreatorId(String projectId) {
        if (projectId == null) return null;
        Project project = projectService.getProjectById(projectId);
        return project == null ? null : project.getAuthorId();
    }

    public boolean shouldTrackEvent(String type, String campaignId, String projectId, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return true;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(20);
        adDebounce.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));

        String key = type + ":" + campaignId + ":" + projectId + ":" + clientIp;
        LocalDateTime last = adDebounce.get(key);
        if (last != null && last.isAfter(cutoff)) {
            return false;
        }

        adDebounce.put(key, now);
        return true;
    }

    public User resolveFinanceOwner(User requester, String ownerId, boolean requireOrgFinanceManage) {
        if (requester == null) {
            throw new SecurityException("Authentication required.");
        }

        String targetId = (ownerId == null || ownerId.isBlank()) ? requester.getId() : ownerId;
        if (requester.getId().equals(targetId)) {
            return requester;
        }

        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Owner account not found."));

        if (target.getAccountType() != User.AccountType.ORGANIZATION) {
            throw new SecurityException("You can only manage your own personal finance account.");
        }

        if (requireOrgFinanceManage && !accessControlService.hasOrgPermission(target, requester.getId(), ApiKey.ApiPermission.ORG_EDIT_METADATA)) {
            throw new SecurityException("Missing organization permission for finance management.");
        }

        if (!requireOrgFinanceManage && !accessControlService.hasOrgPermission(target, requester.getId(), ApiKey.ApiPermission.ORG_MEMBER_READ)) {
            throw new SecurityException("Missing organization permission for finance access.");
        }

        return target;
    }

    public List<String> executePayoutTransfers(User creator, long totalAmountCents, String currency, boolean forceMock) {
        if (creator.getAccountType() == User.AccountType.ORGANIZATION
                && creator.getOrgPayoutMode() == User.OrgPayoutMode.DISTRIBUTE_TO_MEMBERS) {
            List<User.OrgPayoutShare> shares = creator.getOrgPayoutShares() == null ? new ArrayList<>() : creator.getOrgPayoutShares();
            validateOrgPayoutShares(creator, shares);
            List<String> refs = new ArrayList<>();

            long allocated = 0;
            for (int i = 0; i < shares.size(); i++) {
                User.OrgPayoutShare share = shares.get(i);
                User member = userRepository.findById(share.getUserId())
                        .orElseThrow(() -> new IllegalStateException("Organization member not found: " + share.getUserId()));
                ensureStripePayoutReady(member);

                long amount = (i == shares.size() - 1)
                        ? (totalAmountCents - allocated)
                        : Math.round((totalAmountCents * share.getPercent()) / 100.0);
                allocated += amount;
                if (amount <= 0) continue;

                StripeGatewayService.StripeResult result = stripeGatewayService.createOrSimulateTransfer(
                        member.getStripeConnectAccountId(),
                        amount,
                        currency,
                        "Modtale organization payout (" + creator.getUsername() + ")",
                        Map.of(
                                "organizationId", creator.getId(),
                                "memberUserId", member.getId(),
                                "source", "modtale_finance"
                        ),
                        forceMock
                );
                if (!result.success()) {
                    throw new IllegalStateException("Stripe payout failed for " + member.getUsername() + ": " + result.error());
                }
                refs.add(result.id());
            }

            if (refs.isEmpty()) {
                throw new IllegalStateException("No payout recipients were resolved from organization payout shares.");
            }

            return refs;
        }

        ensureStripePayoutReady(creator);
        StripeGatewayService.StripeResult payoutResult = stripeGatewayService.createOrSimulateTransfer(
                creator.getStripeConnectAccountId(),
                totalAmountCents,
                currency,
                "Modtale creator payout",
                Map.of("creatorId", creator.getId(), "source", "modtale_finance"),
                forceMock
        );

        if (!payoutResult.success()) {
            throw new IllegalStateException("Stripe payout failed: " + payoutResult.error());
        }

        return List.of(payoutResult.id());
    }

    public void ensureStripePayoutReady(User accountOwner) {
        if (accountOwner.getStripeConnectAccountId() == null || accountOwner.getStripeConnectAccountId().isBlank()) {
            throw new IllegalStateException("Connect Stripe first before requesting payouts.");
        }
        if (stripeGatewayService.isEnabled() && !accountOwner.isStripePayoutsEnabled()) {
            throw new IllegalStateException("Stripe onboarding is incomplete for " + accountOwner.getUsername() + ". Refresh Stripe status after onboarding.");
        }
    }

    public void validateOrgPayoutShares(User org, List<User.OrgPayoutShare> shares) {
        if (shares == null || shares.isEmpty()) {
            throw new IllegalArgumentException("At least one payout share is required for distributed payouts.");
        }

        Set<String> memberIds = org.getOrganizationMembers().stream()
                .map(User.OrganizationMember::getUserId)
                .collect(Collectors.toSet());

        int totalPercent = 0;
        for (User.OrgPayoutShare share : shares) {
            if (share.getUserId() == null || share.getUserId().isBlank()) {
                throw new IllegalArgumentException("All payout shares must include a userId.");
            }
            if (!memberIds.contains(share.getUserId())) {
                throw new IllegalArgumentException("Payout share includes a non-member user.");
            }
            if (share.getPercent() <= 0) {
                throw new IllegalArgumentException("Payout share percentages must be greater than zero.");
            }
            totalPercent += share.getPercent();
        }

        if (totalPercent != 100) {
            throw new IllegalArgumentException("Organization payout shares must total exactly 100%.");
        }
    }

    public void requireProjectMonetizationOwner(User requester, Project project) {
        if (requester == null) {
            throw new SecurityException("Authentication required.");
        }
        if (accessControlService.isSuperAdmin(requester)) {
            return;
        }
        if (project == null || project.getAuthorId() == null || project.getAuthorId().isBlank()) {
            throw new SecurityException("Project ownership could not be verified.");
        }

        User owner = userRepository.findById(project.getAuthorId()).orElse(null);
        if (owner == null) {
            throw new SecurityException("Project owner was not found.");
        }

        if (owner.getAccountType() == User.AccountType.ORGANIZATION) {
            requireOrganizationOwner(requester, owner);
            return;
        }

        if (!owner.getId().equals(requester.getId())) {
            throw new SecurityException("Only the project owner can update monetization policies.");
        }
    }

    public void requireOrganizationOwner(User requester, User organization) {
        if (requester == null || organization == null) {
            throw new SecurityException("Organization ownership could not be verified.");
        }
        if (accessControlService.isSuperAdmin(requester)) {
            return;
        }
        if (organization.getAccountType() != User.AccountType.ORGANIZATION) {
            throw new SecurityException("Target account is not an organization.");
        }

        User.OrganizationMember requesterMembership = organization.getOrganizationMembers().stream()
                .filter(member -> requester.getId().equals(member.getUserId()))
                .findFirst()
                .orElse(null);
        if (requesterMembership == null) {
            throw new SecurityException("Only organization owners can update monetization policies.");
        }

        if (requesterMembership.getRoleId() != null) {
            User.OrganizationRole role = organization.getOrganizationRoles().stream()
                    .filter(r -> requesterMembership.getRoleId().equals(r.getId()))
                    .findFirst()
                    .orElse(null);
            if (role != null && role.isOwner()) {
                return;
            }
        }

        String legacyRole = requesterMembership.getRole();
        if (legacyRole != null && "OWNER".equalsIgnoreCase(legacyRole)) {
            return;
        }

        throw new SecurityException("Only organization owners can update monetization policies.");
    }

    public Map<String, Object> toCampaignMap(AdCampaign campaign) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", campaign.getId());
        item.put("name", campaign.getName());
        item.put("active", campaign.isActive());
        item.put("testCampaign", campaign.isTestCampaign());
        item.put("providerType", campaign.getProviderType());
        item.put("providerName", campaign.getProviderName());
        item.put("providerPlacementKey", campaign.getProviderPlacementKey());
        item.put("sponsorName", campaign.getSponsorName());
        item.put("headline", campaign.getHeadline());
        item.put("body", campaign.getBody());
        item.put("callToAction", campaign.getCallToAction());
        item.put("imageUrl", campaign.getImageUrl());
        item.put("creatives", campaign.getCreatives());
        item.put("targetUrl", campaign.getTargetUrl());
        item.put("affiliateParam", campaign.getAffiliateParam());
        item.put("affiliateCode", campaign.getAffiliateCode());
        item.put("baseRevenuePerClickCents", campaign.getBaseRevenuePerClickCents());
        item.put("weight", campaign.getWeight());
        item.put("privacyRespecting", campaign.isPrivacyRespecting());
        item.put("nonIntrusive", campaign.isNonIntrusive());
        item.put("allowedClassifications", campaign.getAllowedClassifications());
        item.put("createdAt", campaign.getCreatedAt());
        item.put("updatedAt", campaign.getUpdatedAt());
        return item;
    }

    public String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public int asInt(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    public boolean asBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value == null) return false;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public String normalizeFrontendUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) return "http://localhost:5173";
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }
}
