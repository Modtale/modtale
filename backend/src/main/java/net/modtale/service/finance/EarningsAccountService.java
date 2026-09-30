package net.modtale.service.finance;

import net.modtale.model.dto.request.finance.UpdatePlatformFinanceSettingsRequest;
import net.modtale.model.dto.request.finance.UpdateProjectMonetizationRequest;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.finance.PlatformFinanceSettings;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import net.modtale.repository.finance.PlatformFinanceSettingsRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class EarningsAccountService {

    @Autowired private PlatformFinanceSettingsRepository settingsRepository;
    @Autowired private FinanceLedgerEntryRepository ledgerRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectService projectService;
    @Autowired private AccessControlService accessControlService;
    @Autowired private StripeGatewayService stripeGatewayService;
    @Autowired private RevenueOpsSupport core;
    @Autowired private FinanceWalletService wallets;
    @Autowired private CreatorPayoutService payouts;

    @Value("${app.finance.ads.test-mode-enabled:false}")
    private boolean defaultAdTestModeEnabled;

    @Value("${app.finance.stripe.creator-countries:US,CA,GB,CH,AT,BE,BG,HR,CY,CZ,DK,EE,FI,FR,DE,GR,HU,IE,IS,IT,LI,LT,LU,LV,MT,NL,NO,PL,PT,RO,SE,SI,SK,ES}")
    private String creatorCountries;

    public List<String> getCreatorCountries() {
        return java.util.Arrays.stream(creatorCountries.split(",")).map(String::trim).map(String::toUpperCase)
                .filter(value -> value.matches("[A-Z]{2}")).distinct().sorted().toList();
    }

    public PlatformFinanceSettings getSettings() {
        PlatformFinanceSettings settings = settingsRepository.findById("platform").orElseGet(() -> {
            PlatformFinanceSettings defaults = new PlatformFinanceSettings();
            defaults.setId("platform");
            defaults.setAdCreatorSplitBps(7500);
            defaults.setFundExpiryDays(0);
            defaults.setDonationPlatformCutBps(1000);
            defaults.setDefaultAdRevenuePerClickCents(3);
            defaults.setMinPayoutCents(1000);
            defaults.setAdTestModeEnabled(defaultAdTestModeEnabled);
            defaults.setCurrency("usd");
            defaults.setUpdatedAt(LocalDateTime.now());
            return defaults;
        });
        if (settings.getMonetizationPolicyVersion() < 2) {
            // Prospective policy migration only. Historic source-ledger amounts remain untouched.
            settings.setAdCreatorSplitBps(7500);
            settings.setFundExpiryDays(0);
            settings.setMonetizationPolicyVersion(2);
            settings.setUpdatedAt(LocalDateTime.now());
            return settingsRepository.save(settings);
        }
        return settings;
    }

    public Map<String, Object> getCreatorOverview(User requester, String ownerId, String range) {
        User creator = core.resolveFinanceOwner(requester, ownerId, false);
        PlatformFinanceSettings settings = getSettings();
        List<FinanceLedgerEntry> entries = ledgerRepository.findByCreatorId(creator.getId()).stream()
                .filter(FinanceLedgerRules::isReal).collect(Collectors.toList());

        long available = entries.stream()
                .filter(FinanceLedgerRules::isRecognizedRevenue)
                .filter(e -> e.getStatus() == FinanceLedgerEntry.EntryStatus.AVAILABLE)
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        long pending = entries.stream()
                .filter(e -> e.getStatus() == FinanceLedgerEntry.EntryStatus.PENDING)
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        long paidOut = entries.stream()
                .filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.PAYOUT)
                .mapToLong(e -> Math.abs(e.getCreatorCents()))
                .sum();

        long expired = entries.stream()
                .filter(e -> e.getStatus() == FinanceLedgerEntry.EntryStatus.EXPIRED)
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        LocalDateTime expiringSoonThreshold = LocalDateTime.now().plusDays(30);
        long expiringSoon = entries.stream()
                .filter(FinanceLedgerRules::isRecognizedRevenue)
                .filter(e -> e.getStatus() == FinanceLedgerEntry.EntryStatus.AVAILABLE)
                .filter(e -> e.getExpiresAt() != null && e.getExpiresAt().isBefore(expiringSoonThreshold))
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        int days = core.parseRangeDays(range);
        LocalDate start = LocalDate.now().minusDays(days - 1);
        LocalDate end = LocalDate.now();

        Predicate<FinanceLedgerEntry> inRange = e -> e.getCreatedAt() != null && !e.getCreatedAt().toLocalDate().isBefore(start) && !e.getCreatedAt().toLocalDate().isAfter(end);

        long periodAdRevenue = entries.stream()
                .filter(FinanceLedgerRules::isRecognizedRevenue)
                .filter(inRange)
                .filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.AD_CLICK || e.getType() == FinanceLedgerEntry.LedgerType.AD_IMPRESSION)
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        long periodDonationRevenue = entries.stream()
                .filter(FinanceLedgerRules::isRecognizedRevenue)
                .filter(inRange)
                .filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.DONATION)
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        List<Map<String, Object>> earningsChart = core.buildDailySeries(entries, start, end, Set.of(
                        FinanceLedgerEntry.LedgerType.DONATION,
                        FinanceLedgerEntry.LedgerType.AD_CLICK,
                        FinanceLedgerEntry.LedgerType.AD_IMPRESSION
                ),
                FinanceLedgerEntry::getCreatorCents,
                FinanceLedgerRules::isRecognizedRevenue
        );

        List<Map<String, Object>> donationsChart = core.buildDailySeries(entries, start, end, Set.of(FinanceLedgerEntry.LedgerType.DONATION), FinanceLedgerEntry::getCreatorCents, FinanceLedgerRules::isRecognizedRevenue);
        List<Map<String, Object>> adsChart = core.buildDailySeries(entries, start, end, Set.of(FinanceLedgerEntry.LedgerType.AD_CLICK, FinanceLedgerEntry.LedgerType.AD_IMPRESSION), FinanceLedgerEntry::getCreatorCents, FinanceLedgerRules::isRecognizedRevenue);
        List<Map<String, Object>> expiredChart = core.buildDailySeries(entries, start, end, Set.of(FinanceLedgerEntry.LedgerType.EXPIRED_TRANSFER), FinanceLedgerEntry::getPlatformCents, e -> true);

        Map<String, Long> revenueByProject = entries.stream()
                .filter(FinanceLedgerRules::isRecognizedRevenue)
                .filter(e -> e.getProjectId() != null)
                .collect(Collectors.groupingBy(FinanceLedgerEntry::getProjectId, Collectors.summingLong(FinanceLedgerEntry::getCreatorCents)));

        List<Map<String, Object>> monetizationProjects = projectRepository.findByAuthorIdList(creator.getId()).stream()
                .sorted(Comparator.comparing(Project::getUpdatedAt, Comparator.nullsLast(String::compareTo)).reversed())
                .map(project -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", project.getId());
                    row.put("title", project.getTitle());
                    row.put("slug", project.getSlug());
                    row.put("classification", project.getClassification());
                    row.put("adsEnabled", project.isAdsEnabled());
                    row.put("donationsEnabled", project.isDonationsEnabled());
                    row.put("suggestedDonationCents", project.getSuggestedDonationCents());
                    row.put("donationRecurringDefault", project.isDonationRecurringDefault());
                    row.put("donationPlatformCutPercent", project.getDonationPlatformCutBps() / 100.0);
                    row.put("lifetimeRevenueCents", revenueByProject.getOrDefault(project.getId(), 0L));
                    return row;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> payouts = entries.stream()
                .filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.PAYOUT)
                .sorted(Comparator.comparing(FinanceLedgerEntry::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(20)
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", e.getId());
                    item.put("amountCents", Math.abs(e.getCreatorCents()));
                    item.put("createdAt", e.getCreatedAt());
                    item.put("reference", e.getStripeReference());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("ownerId", creator.getId());
        response.put("ownerAccountType", creator.getAccountType().name());
        response.put("currency", settings.getCurrency());
        response.put("fundExpiryDays", 0);
        response.put("fundsExpire", false);
        response.put("adCreatorSplitPercent", settings.getAdCreatorSplitBps() / 100.0);
        response.put("defaultDonationPlatformCutPercent", settings.getDonationPlatformCutBps() / 100.0);
        response.put("availableCents", Math.max(0, wallets.getWallet(creator.getId(), settings.getCurrency(), false).getAvailableCents()));
        response.put("reservedCents", wallets.getWallet(creator.getId(), settings.getCurrency(), false).getReservedCents());
        response.put("adjustmentOwedCents", Math.max(0, -wallets.getWallet(creator.getId(), settings.getCurrency(), false).getAvailableCents()));
        response.put("payoutHold", wallets.getWallet(creator.getId(), settings.getCurrency(), stripeGatewayService.isTestMode()).isPayoutHold());
        response.put("testAvailableCents", wallets.getWallet(creator.getId(), settings.getCurrency(), true).getAvailableCents());
        response.put("pendingCents", Math.max(0, pending));
        response.put("paidOutCents", Math.max(0, paidOut));
        response.put("expiredCents", Math.max(0, expired));
        response.put("expiringSoonCents", 0);
        response.put("withdrawalsEnabled", stripeGatewayService.isOperational());
        response.put("onboardingEnabled", stripeGatewayService.isOperational());
        response.put("onboardingCountries", getCreatorCountries());
        response.put("stripeAccountCountry", creator.getStripeAccountCountry());
        response.put("testMode", stripeGatewayService.isTestMode() || stripeGatewayService.isMockEnabled());
        response.put("availabilityMessage", stripeGatewayService.getAvailabilityMessage());
        response.put("periodAdRevenueCents", periodAdRevenue);
        response.put("periodDonationRevenueCents", periodDonationRevenue);
        response.put("earningsChart", earningsChart);
        response.put("adsChart", adsChart);
        response.put("donationsChart", donationsChart);
        response.put("expiredChart", expiredChart);
        response.put("projects", monetizationProjects);
        response.put("payouts", payouts);
        response.put("payoutRequests", wallets.getRecentRequests(creator.getId(), stripeGatewayService.isTestMode()).stream().map(request -> {
            Map<String, Object> row = new HashMap<>(); row.put("id", request.getId()); row.put("amountCents", request.getAmountCents());
            row.put("status", request.getStatus()); row.put("createdAt", request.getCreatedAt()); row.put("reviewReason", request.getReviewReason());
            return row;
        }).toList());
        response.put("stripeConnected", creator.getStripeConnectAccountId() != null && !creator.getStripeConnectAccountId().isBlank());
        response.put("stripeOnboardingComplete", creator.isStripeOnboardingComplete());
        response.put("stripePayoutsEnabled", creator.isStripePayoutsEnabled());
        response.put("minPayoutCents", settings.getMinPayoutCents());
        response.put("orgPayoutMode", creator.getOrgPayoutMode().name());
        response.put("orgPayoutShares", creator.getOrgPayoutShares());

        return response;
    }

    public Map<String, Object> getAdminOverview(String range) {
        PlatformFinanceSettings settings = getSettings();
        int days = core.parseRangeDays(range);
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();

        List<FinanceLedgerEntry> entries = ledgerRepository.findByCreatedAtBetween(start, end).stream()
                .filter(FinanceLedgerRules::isReal).collect(Collectors.toList());

        long periodPlatformRevenue = entries.stream().filter(FinanceLedgerRules::isRecognizedRevenue).mapToLong(FinanceLedgerEntry::getPlatformCents).sum();
        long periodCreatorRevenue = entries.stream().filter(FinanceLedgerRules::isRecognizedRevenue).mapToLong(FinanceLedgerEntry::getCreatorCents).sum();
        long periodPayouts = entries.stream()
                .filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.PAYOUT)
                .mapToLong(e -> Math.abs(e.getCreatorCents()))
                .sum();

        long periodExpiredReclaimed = entries.stream()
                .filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.EXPIRED_TRANSFER)
                .mapToLong(FinanceLedgerEntry::getPlatformCents)
                .sum();

        LocalDate chartStart = start.toLocalDate();
        LocalDate chartEnd = LocalDate.now();

        List<Map<String, Object>> platformRevenueChart = core.buildDailySeries(entries, chartStart, chartEnd, Set.of(
                        FinanceLedgerEntry.LedgerType.DONATION,
                        FinanceLedgerEntry.LedgerType.AD_CLICK,
                        FinanceLedgerEntry.LedgerType.EXPIRED_TRANSFER,
                        FinanceLedgerEntry.LedgerType.PLATFORM_CUT
                ),
                FinanceLedgerEntry::getPlatformCents,
                FinanceLedgerRules::isRecognizedRevenue
        );

        List<Map<String, Object>> creatorRevenueChart = core.buildDailySeries(entries, chartStart, chartEnd, Set.of(
                        FinanceLedgerEntry.LedgerType.DONATION,
                        FinanceLedgerEntry.LedgerType.AD_CLICK,
                        FinanceLedgerEntry.LedgerType.AD_IMPRESSION
                ),
                FinanceLedgerEntry::getCreatorCents,
                FinanceLedgerRules::isRecognizedRevenue
        );

        Map<String, Long> creatorRevenueMap = entries.stream()
                .filter(FinanceLedgerRules::isRecognizedRevenue)
                .filter(e -> e.getCreatorId() != null)
                .collect(Collectors.groupingBy(FinanceLedgerEntry::getCreatorId, Collectors.summingLong(FinanceLedgerEntry::getCreatorCents)));

        List<Map<String, Object>> topCreators = creatorRevenueMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(25)
                .map(entry -> {
                    User user = userRepository.findById(entry.getKey()).orElse(null);
                    Map<String, Object> item = new HashMap<>();
                    item.put("creatorId", entry.getKey());
                    item.put("username", user != null ? user.getUsername() : "unknown");
                    item.put("revenueCents", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());

        List<FinanceLedgerEntry> allEntries = ledgerRepository.findAll().stream()
                .filter(FinanceLedgerRules::isReal).collect(Collectors.toList());
        long totalCreatorAvailable = allEntries.stream()
                .filter(FinanceLedgerRules::isRecognizedRevenue)
                .filter(e -> e.getStatus() == FinanceLedgerEntry.EntryStatus.AVAILABLE)
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        Map<String, Object> response = new HashMap<>();
        response.put("currency", settings.getCurrency());
        response.put("fundExpiryDays", 0);
        response.put("fundsExpire", false);
        response.put("adCreatorSplitPercent", settings.getAdCreatorSplitBps() / 100.0);
        response.put("donationPlatformCutPercent", settings.getDonationPlatformCutBps() / 100.0);
        response.put("defaultAdRevenuePerClickCents", settings.getDefaultAdRevenuePerClickCents());
        response.put("minPayoutCents", settings.getMinPayoutCents());
        response.put("periodPlatformRevenueCents", periodPlatformRevenue);
        response.put("periodCreatorRevenueCents", periodCreatorRevenue);
        response.put("periodPayoutsCents", periodPayouts);
        response.put("periodExpiredReclaimedCents", periodExpiredReclaimed);
        response.put("totalCreatorAvailableCents", totalCreatorAvailable);
        response.put("platformRevenueChart", platformRevenueChart);
        response.put("creatorRevenueChart", creatorRevenueChart);
        response.put("topCreators", topCreators);
        return response;
    }

    public Map<String, Object> updatePlatformSettings(UpdatePlatformFinanceSettingsRequest request) {
        PlatformFinanceSettings settings = getSettings();
        if (request.getDefaultAdRevenuePerClickCents() != null) {
            settings.setDefaultAdRevenuePerClickCents(request.getDefaultAdRevenuePerClickCents());
        }
        if (request.getMinPayoutCents() != null) {
            settings.setMinPayoutCents(request.getMinPayoutCents());
        }
        settings.setUpdatedAt(LocalDateTime.now());
        settingsRepository.save(settings);
        return Map.of(
                "ok", true,
                "settings", settings
        );
    }

    public Map<String, Object> createStripeOnboardingLink(User requester, String ownerId, String returnPath, String country) {
        User creator = core.resolveFinanceOwner(requester, ownerId, true);
        if (creator.getAccountType() == User.AccountType.ORGANIZATION) {
            core.requireOrganizationOwner(requester, creator);
        }
        if (!stripeGatewayService.isOperational()) throw new IllegalStateException(stripeGatewayService.getAvailabilityMessage());
        String accountId = creator.getStripeConnectAccountId();
        if (accountId == null || accountId.isBlank()) {
            String selectedCountry = country == null ? "" : country.trim().toUpperCase(java.util.Locale.ROOT);
            if (!getCreatorCountries().contains(selectedCountry)) throw new IllegalArgumentException("Select an eligible country for the payout account owner.");
            StripeGatewayService.StripeResult accountResult = stripeGatewayService.createOrSimulateConnectAccount(
                    creator.getEmail(), selectedCountry, false,
                    "connect:" + stripeGatewayService.getPlatformAccountId() + (stripeGatewayService.isTestMode() ? ":test:" : ":live:") + creator.getId() + ":" + selectedCountry);

            if (!accountResult.success()) {
                throw new IllegalStateException("Unable to initialize Stripe account: " + accountResult.error());
            }
            accountId = accountResult.id();
            creator.setStripeConnectAccountId(accountId);
            creator.setStripeAccountCountry(selectedCountry);
            userRepository.save(creator);
        }

        String safePath = "/dashboard/finance";
        StripeGatewayService.StripeResult linkResult = stripeGatewayService.createOrSimulateOnboardingLink(
                accountId,
                safePath,
                stripeGatewayService.isMockEnabled()
        );
        if (!linkResult.success()) {
            throw new IllegalStateException("Unable to create onboarding link: " + linkResult.error());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("onboardingUrl", linkResult.url());
        response.put("simulated", Boolean.TRUE.equals(linkResult.raw().get("simulated")));
        response.put("accountId", accountId);
        response.put("ownerId", creator.getId());
        return response;
    }

    public Map<String, Object> refreshStripeStatus(User requester, String ownerId) {
        User creator = core.resolveFinanceOwner(requester, ownerId, true);
        if (creator.getStripeConnectAccountId() == null || creator.getStripeConnectAccountId().isBlank()) {
            return Map.of(
                    "connected", false,
                    "onboardingComplete", false,
                    "payoutsEnabled", false
            );
        }

        Map<String, Object> status = stripeGatewayService.getAccountStatus(
                creator.getStripeConnectAccountId(),
                stripeGatewayService.isMockEnabled()
        );

        if (status.containsKey("error")) throw new IllegalStateException("Payment provider status is temporarily unavailable. Your saved account status has not changed.");
        boolean detailsSubmitted = core.asBoolean(status.get("details_submitted"));
        boolean payoutsEnabled = core.asBoolean(status.get("payouts_enabled"))
                && status.get("capabilities") instanceof Map<?, ?> capabilities && "active".equals(capabilities.get("transfers"));

        creator.setStripeOnboardingComplete(detailsSubmitted);
        creator.setStripePayoutsEnabled(payoutsEnabled);
        if (status.get("country") != null) {
            creator.setStripeAccountCountry(String.valueOf(status.get("country")));
        }
        userRepository.save(creator);

        Map<String, Object> response = new HashMap<>();
        response.put("connected", true);
        response.put("onboardingComplete", detailsSubmitted);
        response.put("payoutsEnabled", payoutsEnabled);
        response.put("ownerId", creator.getId());

        return response;
    }

    public Map<String, Object> requestPayout(User requester, String ownerId, Long amountCentsInput, String requestKey) {
        User creator = core.resolveFinanceOwner(requester, ownerId, true);
        if (creator.getAccountType() == User.AccountType.ORGANIZATION) {
            core.requireOrganizationOwner(requester, creator);
        }
        PlatformFinanceSettings settings = getSettings();
        return CreatorPayoutService.toResponse(payouts.request(requester, creator, settings.getCurrency(), amountCentsInput, settings.getMinPayoutCents(), requestKey));
    }

    public Map<String, Object> updateProjectMonetization(User requester, Project project, UpdateProjectMonetizationRequest request) {
        core.requireProjectMonetizationOwner(requester, project);
        if (request.getAdsEnabled() != null) {
            project.setAdsEnabled(request.getAdsEnabled());
        }
        if (request.getDonationsEnabled() != null) {
            project.setDonationsEnabled(request.getDonationsEnabled());
        }
        if (request.getSuggestedDonationCents() != null) {
            int clamped = Math.max(100, Math.min(100000, request.getSuggestedDonationCents()));
            project.setSuggestedDonationCents(clamped);
        }
        if (request.getDonationRecurringDefault() != null) {
            project.setDonationRecurringDefault(request.getDonationRecurringDefault());
        }
        if (request.getDonationPlatformCutBps() != null) {
            project.setDonationPlatformCutBps(request.getDonationPlatformCutBps());
        }

        projectRepository.save(project);
        projectService.evictProjectCache(project);

        return Map.of(
                "ok", true,
                "projectId", project.getId(),
                "adsEnabled", project.isAdsEnabled(),
                "donationsEnabled", project.isDonationsEnabled(),
                "suggestedDonationCents", project.getSuggestedDonationCents(),
                "donationRecurringDefault", project.isDonationRecurringDefault(),
                "donationPlatformCutBps", project.getDonationPlatformCutBps()
        );
    }

    public List<Map<String, Object>> getFinanceContexts(User requester) {
        List<Map<String, Object>> contexts = new ArrayList<>();
        Map<String, Object> personal = new LinkedHashMap<>();
        personal.put("id", requester.getId());
        personal.put("username", requester.getUsername());
        personal.put("accountType", requester.getAccountType().name());
        personal.put("isPersonal", true);
        contexts.add(personal);

        for (User org : userRepository.findOrganizationsByMemberId(requester.getId())) {
            if (org.getAccountType() != User.AccountType.ORGANIZATION) continue;
            if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_EDIT_METADATA)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", org.getId());
            row.put("username", org.getUsername());
            row.put("accountType", org.getAccountType().name());
            row.put("isPersonal", false);
            contexts.add(row);
        }
        return contexts;
    }

    public Map<String, Object> getOrgPayoutPolicy(User requester, String orgId) {
        User org = core.resolveFinanceOwner(requester, orgId, true);
        if (org.getAccountType() != User.AccountType.ORGANIZATION) {
            throw new IllegalArgumentException("Finance policy is only available for organizations.");
        }

        List<Map<String, Object>> shares = new ArrayList<>();
        for (User.OrgPayoutShare share : org.getOrgPayoutShares()) {
            if (share.getUserId() == null || share.getUserId().isBlank()) continue;
            User user = userRepository.findById(share.getUserId()).orElse(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", share.getUserId());
            item.put("percent", share.getPercent());
            item.put("username", user != null ? user.getUsername() : "unknown");
            item.put("stripeConnected", user != null && user.getStripeConnectAccountId() != null && !user.getStripeConnectAccountId().isBlank());
            item.put("stripePayoutsEnabled", user != null && user.isStripePayoutsEnabled());
            shares.add(item);
        }

        List<Map<String, Object>> members = new ArrayList<>();
        for (User.OrganizationMember member : org.getOrganizationMembers()) {
            User user = userRepository.findById(member.getUserId()).orElse(null);
            if (user == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", user.getId());
            item.put("username", user.getUsername());
            item.put("stripeConnected", user.getStripeConnectAccountId() != null && !user.getStripeConnectAccountId().isBlank());
            item.put("stripePayoutsEnabled", user.isStripePayoutsEnabled());
            members.add(item);
        }

        return Map.of(
                "orgId", org.getId(),
                "orgName", org.getUsername(),
                "payoutMode", org.getOrgPayoutMode().name(),
                "shares", shares,
                "members", members
        );
    }

    public Map<String, Object> updateOrgPayoutPolicy(User requester, String orgId, String payoutModeRaw, List<Map<String, Object>> sharesRaw) {
        User org = core.resolveFinanceOwner(requester, orgId, true);
        core.requireOrganizationOwner(requester, org);
        if (org.getAccountType() != User.AccountType.ORGANIZATION) {
            throw new IllegalArgumentException("Finance policy is only available for organizations.");
        }

        User.OrgPayoutMode mode;
        try {
            mode = User.OrgPayoutMode.valueOf(String.valueOf(payoutModeRaw));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payout mode.");
        }

        List<User.OrgPayoutShare> parsedShares = new ArrayList<>();
        if (sharesRaw != null) {
            for (Map<String, Object> item : sharesRaw) {
                if (item == null) continue;
                String userId = item.get("userId") == null ? null : String.valueOf(item.get("userId"));
                int percent = core.asInt(item.get("percent"), 0);
                if (userId == null || userId.isBlank() || percent <= 0) continue;
                parsedShares.add(new User.OrgPayoutShare(userId, percent));
            }
        }

        if (mode == User.OrgPayoutMode.DISTRIBUTE_TO_MEMBERS) {
            core.validateOrgPayoutShares(org, parsedShares);
        } else {
            parsedShares = new ArrayList<>();
        }

        org.setOrgPayoutMode(mode);
        org.setOrgPayoutShares(parsedShares);
        userRepository.save(org);

        return getOrgPayoutPolicy(requester, orgId);
    }
}
