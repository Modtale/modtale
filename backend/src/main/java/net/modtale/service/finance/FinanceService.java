package net.modtale.service.finance;

import net.modtale.model.dto.request.finance.UpdatePlatformFinanceSettingsRequest;
import net.modtale.model.dto.request.finance.UpdateProjectMonetizationRequest;
import net.modtale.model.finance.AdCampaign;
import net.modtale.model.finance.DonationIntent;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.finance.PlatformFinanceSettings;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.finance.AdCampaignRepository;
import net.modtale.repository.finance.DonationIntentRepository;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import net.modtale.repository.finance.PlatformFinanceSettingsRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.ProjectService;
import net.modtale.service.security.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class FinanceService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired private PlatformFinanceSettingsRepository settingsRepository;
    @Autowired private FinanceLedgerEntryRepository ledgerRepository;
    @Autowired private DonationIntentRepository donationIntentRepository;
    @Autowired private AdCampaignRepository adCampaignRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectService projectService;
    @Autowired private AccessControlService accessControlService;
    @Autowired private StripeGatewayService stripeGatewayService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.finance.ads.test-mode-enabled:false}")
    private boolean defaultAdTestModeEnabled;

    private final Map<String, LocalDateTime> adDebounce = new ConcurrentHashMap<>();

    public PlatformFinanceSettings getSettings() {
        return settingsRepository.findById("platform").orElseGet(() -> {
            PlatformFinanceSettings defaults = new PlatformFinanceSettings();
            defaults.setId("platform");
            defaults.setAdCreatorSplitBps(9000);
            defaults.setFundExpiryDays(365);
            defaults.setDonationPlatformCutBps(1000);
            defaults.setDefaultAdRevenuePerClickCents(3);
            defaults.setMinPayoutCents(1000);
            defaults.setAdTestModeEnabled(defaultAdTestModeEnabled);
            defaults.setCurrency("usd");
            defaults.setUpdatedAt(LocalDateTime.now());
            return settingsRepository.save(defaults);
        });
    }

    public Map<String, Object> getCreatorOverview(User requester, String ownerId, String range) {
        User creator = resolveFinanceOwner(requester, ownerId, false);
        PlatformFinanceSettings settings = getSettings();
        List<FinanceLedgerEntry> entries = ledgerRepository.findByCreatorId(creator.getId());

        long available = entries.stream()
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
                .filter(e -> e.getStatus() == FinanceLedgerEntry.EntryStatus.AVAILABLE)
                .filter(e -> e.getExpiresAt() != null && e.getExpiresAt().isBefore(expiringSoonThreshold))
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        int days = parseRangeDays(range);
        LocalDate start = LocalDate.now().minusDays(days - 1);
        LocalDate end = LocalDate.now();

        Predicate<FinanceLedgerEntry> inRange = e -> e.getCreatedAt() != null && !e.getCreatedAt().toLocalDate().isBefore(start) && !e.getCreatedAt().toLocalDate().isAfter(end);

        long periodAdRevenue = entries.stream()
                .filter(inRange)
                .filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.AD_CLICK || e.getType() == FinanceLedgerEntry.LedgerType.AD_IMPRESSION)
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        long periodDonationRevenue = entries.stream()
                .filter(inRange)
                .filter(e -> e.getType() == FinanceLedgerEntry.LedgerType.DONATION)
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        List<Map<String, Object>> earningsChart = buildDailySeries(entries, start, end, Set.of(
                        FinanceLedgerEntry.LedgerType.DONATION,
                        FinanceLedgerEntry.LedgerType.AD_CLICK,
                        FinanceLedgerEntry.LedgerType.AD_IMPRESSION
                ),
                FinanceLedgerEntry::getCreatorCents,
                e -> e.getStatus() != FinanceLedgerEntry.EntryStatus.PENDING
        );

        List<Map<String, Object>> donationsChart = buildDailySeries(entries, start, end, Set.of(FinanceLedgerEntry.LedgerType.DONATION), FinanceLedgerEntry::getCreatorCents, e -> true);
        List<Map<String, Object>> adsChart = buildDailySeries(entries, start, end, Set.of(FinanceLedgerEntry.LedgerType.AD_CLICK, FinanceLedgerEntry.LedgerType.AD_IMPRESSION), FinanceLedgerEntry::getCreatorCents, e -> true);
        List<Map<String, Object>> expiredChart = buildDailySeries(entries, start, end, Set.of(FinanceLedgerEntry.LedgerType.EXPIRED_TRANSFER), FinanceLedgerEntry::getPlatformCents, e -> true);

        Map<String, Long> revenueByProject = entries.stream()
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
        response.put("fundExpiryDays", settings.getFundExpiryDays());
        response.put("adCreatorSplitPercent", settings.getAdCreatorSplitBps() / 100.0);
        response.put("defaultDonationPlatformCutPercent", settings.getDonationPlatformCutBps() / 100.0);
        response.put("availableCents", Math.max(0, available));
        response.put("pendingCents", Math.max(0, pending));
        response.put("paidOutCents", Math.max(0, paidOut));
        response.put("expiredCents", Math.max(0, expired));
        response.put("expiringSoonCents", Math.max(0, expiringSoon));
        response.put("periodAdRevenueCents", periodAdRevenue);
        response.put("periodDonationRevenueCents", periodDonationRevenue);
        response.put("earningsChart", earningsChart);
        response.put("adsChart", adsChart);
        response.put("donationsChart", donationsChart);
        response.put("expiredChart", expiredChart);
        response.put("projects", monetizationProjects);
        response.put("payouts", payouts);
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
        int days = parseRangeDays(range);
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();

        List<FinanceLedgerEntry> entries = ledgerRepository.findByCreatedAtBetween(start, end);

        long periodPlatformRevenue = entries.stream().mapToLong(FinanceLedgerEntry::getPlatformCents).sum();
        long periodCreatorRevenue = entries.stream().mapToLong(FinanceLedgerEntry::getCreatorCents).sum();
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

        List<Map<String, Object>> platformRevenueChart = buildDailySeries(entries, chartStart, chartEnd, Set.of(
                        FinanceLedgerEntry.LedgerType.DONATION,
                        FinanceLedgerEntry.LedgerType.AD_CLICK,
                        FinanceLedgerEntry.LedgerType.EXPIRED_TRANSFER,
                        FinanceLedgerEntry.LedgerType.PLATFORM_CUT
                ),
                FinanceLedgerEntry::getPlatformCents,
                e -> true
        );

        List<Map<String, Object>> creatorRevenueChart = buildDailySeries(entries, chartStart, chartEnd, Set.of(
                        FinanceLedgerEntry.LedgerType.DONATION,
                        FinanceLedgerEntry.LedgerType.AD_CLICK,
                        FinanceLedgerEntry.LedgerType.AD_IMPRESSION
                ),
                FinanceLedgerEntry::getCreatorCents,
                e -> true
        );

        Map<String, Long> creatorRevenueMap = entries.stream()
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

        List<FinanceLedgerEntry> allEntries = ledgerRepository.findAll();
        long totalCreatorAvailable = allEntries.stream()
                .filter(e -> e.getStatus() == FinanceLedgerEntry.EntryStatus.AVAILABLE)
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        Map<String, Object> response = new HashMap<>();
        response.put("currency", settings.getCurrency());
        response.put("fundExpiryDays", settings.getFundExpiryDays());
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

    public Map<String, Object> createStripeOnboardingLink(User requester, String ownerId, String returnPath) {
        User creator = resolveFinanceOwner(requester, ownerId, true);
        if (creator.getAccountType() == User.AccountType.ORGANIZATION) {
            requireOrganizationOwner(requester, creator);
        }
        String accountId = creator.getStripeConnectAccountId();
        if (accountId == null || accountId.isBlank()) {
            StripeGatewayService.StripeResult accountResult = stripeGatewayService.createOrSimulateConnectAccount(creator.getEmail(), "US");
            if (!accountResult.success()) {
                throw new IllegalStateException("Unable to initialize Stripe account: " + accountResult.error());
            }
            accountId = accountResult.id();
            creator.setStripeConnectAccountId(accountId);
            creator.setStripeAccountCountry("US");
            userRepository.save(creator);
        }

        String safePath = (returnPath == null || returnPath.isBlank()) ? "/dashboard/finance" : returnPath;
        StripeGatewayService.StripeResult linkResult = stripeGatewayService.createOrSimulateOnboardingLink(accountId, safePath);
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
        User creator = resolveFinanceOwner(requester, ownerId, true);
        if (creator.getStripeConnectAccountId() == null || creator.getStripeConnectAccountId().isBlank()) {
            return Map.of(
                    "connected", false,
                    "onboardingComplete", false,
                    "payoutsEnabled", false
            );
        }

        Map<String, Object> status = stripeGatewayService.getAccountStatus(creator.getStripeConnectAccountId());

        boolean detailsSubmitted = asBoolean(status.get("details_submitted"));
        boolean payoutsEnabled = asBoolean(status.get("payouts_enabled"));

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
        response.put("raw", status);
        return response;
    }

    public Map<String, Object> requestPayout(User requester, String ownerId, Long amountCentsInput) {
        User creator = resolveFinanceOwner(requester, ownerId, true);
        if (creator.getAccountType() == User.AccountType.ORGANIZATION) {
            requireOrganizationOwner(requester, creator);
        }
        PlatformFinanceSettings settings = getSettings();

        long availableBalance = ledgerRepository.findByCreatorIdAndStatus(creator.getId(), FinanceLedgerEntry.EntryStatus.AVAILABLE).stream()
                .mapToLong(FinanceLedgerEntry::getCreatorCents)
                .sum();

        long targetAmount = amountCentsInput == null ? availableBalance : amountCentsInput;
        targetAmount = Math.min(targetAmount, availableBalance);

        if (targetAmount < settings.getMinPayoutCents()) {
            throw new IllegalStateException("Minimum payout is " + settings.getMinPayoutCents() + " cents.");
        }

        List<String> payoutReferences = executePayoutTransfers(creator, targetAmount, settings);

        long remaining = targetAmount;
        List<FinanceLedgerEntry> eligibleEntries = ledgerRepository.findByCreatorIdAndStatusOrderByCreatedAtAsc(creator.getId(), FinanceLedgerEntry.EntryStatus.AVAILABLE);
        List<FinanceLedgerEntry> changed = new ArrayList<>();

        for (FinanceLedgerEntry entry : eligibleEntries) {
            if (remaining <= 0) break;
            long value = entry.getCreatorCents();
            if (value <= 0) continue;
            if (value <= remaining) {
                entry.setStatus(FinanceLedgerEntry.EntryStatus.PAID);
                entry.setCompletedAt(LocalDateTime.now());
                changed.add(entry);
                remaining -= value;
            } else {
                entry.setCreatorCents(value - remaining);
                changed.add(entry);
                remaining = 0;
            }
        }

        if (!changed.isEmpty()) {
            ledgerRepository.saveAll(changed);
        }

        FinanceLedgerEntry payoutEntry = new FinanceLedgerEntry();
        payoutEntry.setCreatorId(creator.getId());
        payoutEntry.setType(FinanceLedgerEntry.LedgerType.PAYOUT);
        payoutEntry.setGrossCents(targetAmount);
        payoutEntry.setCreatorCents(-targetAmount);
        payoutEntry.setPlatformCents(0);
        payoutEntry.setCurrency(settings.getCurrency());
        payoutEntry.setStatus(FinanceLedgerEntry.EntryStatus.PAID);
        payoutEntry.setCreatedAt(LocalDateTime.now());
        payoutEntry.setCompletedAt(LocalDateTime.now());
        payoutEntry.setStripeReference(String.join(",", payoutReferences));
        payoutEntry.getMetadata().put("payoutMode", creator.getOrgPayoutMode().name());
        payoutEntry.getMetadata().put("recipientCount", String.valueOf(payoutReferences.size()));
        ledgerRepository.save(payoutEntry);

        return Map.of(
                "ok", true,
                "payoutReference", payoutEntry.getStripeReference(),
                "payoutReferences", payoutReferences,
                "ownerId", creator.getId(),
                "amountCents", targetAmount,
                "recipientCount", payoutReferences.size()
        );
    }

    public Map<String, Object> updateProjectMonetization(User requester, Project project, UpdateProjectMonetizationRequest request) {
        requireProjectMonetizationOwner(requester, project);
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

    public Map<String, Object> getDonationConfig(String projectId) {
        Project project = projectService.getProjectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("projectId", project.getId());
        response.put("donationsEnabled", project.isDonationsEnabled());
        response.put("suggestedDonationCents", Math.max(100, project.getSuggestedDonationCents()));
        response.put("donationRecurringDefault", project.isDonationRecurringDefault());
        response.put("donationPlatformCutPercent", project.getDonationPlatformCutBps() / 100.0);
        response.put("currency", getSettings().getCurrency());
        response.put("minimumDonationCents", 100);
        return response;
    }

    public Map<String, Object> createDonationCheckout(String projectId, long amountCents, boolean recurring) {
        Project project = projectService.getProjectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found");
        }
        if (!project.isDonationsEnabled()) {
            throw new IllegalStateException("Donations are disabled by this creator for this project.");
        }

        PlatformFinanceSettings settings = getSettings();
        long normalizedAmount = Math.max(100, Math.min(100000, amountCents));

        long platformCut = Math.round((normalizedAmount * project.getDonationPlatformCutBps()) / 10000.0);
        long creatorCut = normalizedAmount - platformCut;

        DonationIntent intent = new DonationIntent();
        intent.setProjectId(project.getId());
        intent.setCreatorId(project.getAuthorId());
        intent.setAmountCents(normalizedAmount);
        intent.setCreatorCents(creatorCut);
        intent.setPlatformCents(platformCut);
        intent.setRecurring(recurring);
        intent.setCurrency(settings.getCurrency());
        intent.setStatus(DonationIntent.DonationStatus.PENDING);
        intent = donationIntentRepository.save(intent);

        String projectPath = projectService.getProjectLink(project);
        String successUrl = normalizeFrontendUrl() + projectPath + "?donation_intent=" + intent.getId() + "&donation_status=success";
        String cancelUrl = normalizeFrontendUrl() + projectPath + "?donation_intent=" + intent.getId() + "&donation_status=cancel";

        StripeGatewayService.StripeResult session = stripeGatewayService.createOrSimulateDonationCheckout(
                intent.getId(),
                project.getTitle(),
                normalizedAmount,
                recurring,
                successUrl,
                cancelUrl,
                settings.getCurrency()
        );

        if (!session.success()) {
            intent.setStatus(DonationIntent.DonationStatus.FAILED);
            donationIntentRepository.save(intent);
            throw new IllegalStateException("Unable to create donation checkout: " + session.error());
        }

        intent.setStripeSessionId(session.id());
        intent.setCheckoutUrl(session.url());
        donationIntentRepository.save(intent);

        boolean simulated = Boolean.TRUE.equals(session.raw().get("simulated"));
        if (simulated) {
            completeDonationIntent(intent, Map.of("simulated", true, "status", "complete", "payment_status", "paid"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("intentId", intent.getId());
        response.put("checkoutUrl", session.url());
        response.put("simulated", simulated);
        response.put("creatorCents", creatorCut);
        response.put("platformCents", platformCut);
        return response;
    }

    public Map<String, Object> confirmDonationIntent(String intentId) {
        DonationIntent intent = donationIntentRepository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Donation intent not found"));

        if (intent.getStatus() == DonationIntent.DonationStatus.COMPLETED) {
            return Map.of("ok", true, "status", "COMPLETED");
        }

        if (intent.getStatus() == DonationIntent.DonationStatus.FAILED || intent.getStatus() == DonationIntent.DonationStatus.EXPIRED) {
            return Map.of("ok", false, "status", intent.getStatus().name());
        }

        Map<String, Object> session = stripeGatewayService.getCheckoutSession(intent.getStripeSessionId());
        String paymentStatus = session.get("payment_status") == null ? "" : String.valueOf(session.get("payment_status"));
        String status = session.get("status") == null ? "" : String.valueOf(session.get("status"));

        if ("paid".equalsIgnoreCase(paymentStatus) || "complete".equalsIgnoreCase(status)) {
            completeDonationIntent(intent, session);
            return Map.of("ok", true, "status", "COMPLETED");
        }

        return Map.of("ok", false, "status", "PENDING");
    }

    private void completeDonationIntent(DonationIntent intent, Map<String, Object> sessionData) {
        if (intent.getStatus() == DonationIntent.DonationStatus.COMPLETED) return;

        PlatformFinanceSettings settings = getSettings();

        intent.setStatus(DonationIntent.DonationStatus.COMPLETED);
        intent.setCompletedAt(LocalDateTime.now());
        donationIntentRepository.save(intent);

        FinanceLedgerEntry entry = new FinanceLedgerEntry();
        entry.setCreatorId(intent.getCreatorId());
        entry.setProjectId(intent.getProjectId());
        entry.setType(FinanceLedgerEntry.LedgerType.DONATION);
        entry.setGrossCents(intent.getAmountCents());
        entry.setCreatorCents(intent.getCreatorCents());
        entry.setPlatformCents(intent.getPlatformCents());
        entry.setCurrency(intent.getCurrency());
        entry.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setAvailableAt(LocalDateTime.now());
        entry.setExpiresAt(LocalDateTime.now().plusDays(settings.getFundExpiryDays()));
        entry.setRecurring(intent.isRecurring());
        entry.setStripeReference(intent.getStripeSessionId());
        entry.setExternalReference(intent.getId());
        if (sessionData != null && sessionData.get("simulated") != null) {
            entry.getMetadata().put("simulated", String.valueOf(sessionData.get("simulated")));
        }
        ledgerRepository.save(entry);
    }

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
        User org = resolveFinanceOwner(requester, orgId, true);
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
        User org = resolveFinanceOwner(requester, orgId, true);
        requireOrganizationOwner(requester, org);
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
                int percent = asInt(item.get("percent"), 0);
                if (userId == null || userId.isBlank() || percent <= 0) continue;
                parsedShares.add(new User.OrgPayoutShare(userId, percent));
            }
        }

        if (mode == User.OrgPayoutMode.DISTRIBUTE_TO_MEMBERS) {
            validateOrgPayoutShares(org, parsedShares);
        } else {
            parsedShares = new ArrayList<>();
        }

        org.setOrgPayoutMode(mode);
        org.setOrgPayoutShares(parsedShares);
        userRepository.save(org);

        return getOrgPayoutPolicy(requester, orgId);
    }

    public List<Map<String, Object>> getAdCampaigns() {
        return adCampaignRepository.findAll().stream()
                .sorted(Comparator.comparing(AdCampaign::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .map(this::toCampaignMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> createAdCampaign(Map<String, Object> payload) {
        AdCampaign campaign = new AdCampaign();
        applyCampaignPayload(campaign, payload, true);
        campaign.setCreatedAt(LocalDateTime.now());
        campaign.setUpdatedAt(LocalDateTime.now());
        return toCampaignMap(adCampaignRepository.save(campaign));
    }

    public Map<String, Object> updateAdCampaign(String campaignId, Map<String, Object> payload) {
        AdCampaign campaign = adCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Ad campaign not found"));
        applyCampaignPayload(campaign, payload, false);
        campaign.setUpdatedAt(LocalDateTime.now());
        return toCampaignMap(adCampaignRepository.save(campaign));
    }

    public Map<String, Object> setCampaignActiveState(String campaignId, boolean active) {
        AdCampaign campaign = adCampaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Ad campaign not found"));
        campaign.setActive(active);
        campaign.setUpdatedAt(LocalDateTime.now());
        return toCampaignMap(adCampaignRepository.save(campaign));
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

        AdCampaign.AdPlacement placement = parsePlacement(placementRaw);

        List<AdCampaign> candidates = activeCampaigns.stream()
                .filter(AdCampaign::isPrivacyRespecting)
                .filter(AdCampaign::isNonIntrusive)
                .filter(campaign -> campaign.getAllowedClassifications() == null
                        || campaign.getAllowedClassifications().isEmpty()
                        || (project.getClassification() != null && campaign.getAllowedClassifications().contains(project.getClassification().name())))
                .filter(campaign -> !testOnly || hasRenderableCreativeForPlacement(campaign, placement))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return Map.of("enabled", false, "reason", "NO_ACTIVE_CAMPAIGNS");
        }

        AdCampaign chosen = weightedPick(candidates);
        AdCampaign.AdCreative creative = chooseCreative(chosen, placement);
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
        ad.put("creatorRevenueSharePercent", getSettings().getAdCreatorSplitBps() / 100.0);
        return ad;
    }

    public void trackAdImpression(String campaignId, String projectId, String clientIp) {
        if (!shouldTrackEvent("impression", campaignId, projectId, clientIp)) return;

        FinanceLedgerEntry entry = new FinanceLedgerEntry();
        entry.setCreatorId(resolveCreatorId(projectId));
        entry.setProjectId(projectId);
        entry.setType(FinanceLedgerEntry.LedgerType.AD_IMPRESSION);
        entry.setGrossCents(0);
        entry.setCreatorCents(0);
        entry.setPlatformCents(0);
        entry.setCurrency(getSettings().getCurrency());
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

        String targetUrl = appendAffiliateParams(campaign.getTargetUrl(), campaign.getAffiliateParam(), campaign.getAffiliateCode());

        Project project = projectService.getProjectById(projectId);
        if (project == null || !project.isAdsEnabled()) {
            return targetUrl;
        }

        if (!shouldTrackEvent("click", campaignId, projectId, clientIp)) {
            return targetUrl;
        }

        PlatformFinanceSettings settings = getSettings();
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

    public List<Map<String, Object>> getPublicDailyRevenue(int days) {
        int safeDays = Math.max(1, Math.min(365, days));
        LocalDate start = LocalDate.now().minusDays(safeDays - 1);
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = LocalDateTime.now();

        List<FinanceLedgerEntry> entries = ledgerRepository.findByCreatedAtBetween(startAt, endAt);
        Map<LocalDate, long[]> buckets = new HashMap<>();

        for (FinanceLedgerEntry entry : entries) {
            if (entry.getCreatedAt() == null) continue;
            LocalDate date = entry.getCreatedAt().toLocalDate();
            long[] sums = buckets.computeIfAbsent(date, key -> new long[3]);
            sums[0] += Math.max(0, entry.getGrossCents());
            sums[1] += Math.max(0, entry.getCreatorCents());
            sums[2] += Math.max(0, entry.getPlatformCents());
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(LocalDate.now()); day = day.plusDays(1)) {
            long[] sums = buckets.getOrDefault(day, new long[3]);
            Map<String, Object> item = new HashMap<>();
            item.put("date", day.format(DATE_FMT));
            item.put("grossCents", sums[0]);
            item.put("creatorCents", sums[1]);
            item.put("platformCents", sums[2]);
            response.add(item);
        }

        return response;
    }

    @Scheduled(cron = "0 30 0 * * *")
    public void expireCreatorFunds() {
        PlatformFinanceSettings settings = getSettings();
        LocalDateTime now = LocalDateTime.now();

        List<FinanceLedgerEntry> expiringEntries = ledgerRepository.findByStatusAndExpiresAtBefore(FinanceLedgerEntry.EntryStatus.AVAILABLE, now);
        if (expiringEntries.isEmpty()) return;

        List<FinanceLedgerEntry> updates = new ArrayList<>();
        List<FinanceLedgerEntry> transferEntries = new ArrayList<>();

        for (FinanceLedgerEntry entry : expiringEntries) {
            if (entry.getCreatorCents() <= 0) continue;

            entry.setStatus(FinanceLedgerEntry.EntryStatus.EXPIRED);
            entry.setCompletedAt(now);
            updates.add(entry);

            FinanceLedgerEntry transfer = new FinanceLedgerEntry();
            transfer.setType(FinanceLedgerEntry.LedgerType.EXPIRED_TRANSFER);
            transfer.setProjectId(entry.getProjectId());
            transfer.setGrossCents(entry.getCreatorCents());
            transfer.setCreatorCents(0);
            transfer.setPlatformCents(entry.getCreatorCents());
            transfer.setCurrency(settings.getCurrency());
            transfer.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE);
            transfer.setCreatedAt(now);
            transfer.setAvailableAt(now);
            transfer.setExpiresAt(now.plusYears(50));
            transfer.setExternalReference(entry.getId());
            transfer.getMetadata().put("reason", "creator_funds_expired_after_365_days");
            transferEntries.add(transfer);
        }

        if (!updates.isEmpty()) ledgerRepository.saveAll(updates);
        if (!transferEntries.isEmpty()) ledgerRepository.saveAll(transferEntries);

        donationIntentRepository.findByStatusAndExpiresAtBefore(DonationIntent.DonationStatus.PENDING, now)
                .forEach(intent -> {
                    intent.setStatus(DonationIntent.DonationStatus.EXPIRED);
                    donationIntentRepository.save(intent);
                });
    }

    private List<Map<String, Object>> buildDailySeries(
            List<FinanceLedgerEntry> source,
            LocalDate start,
            LocalDate end,
            Set<FinanceLedgerEntry.LedgerType> allowedTypes,
            java.util.function.ToLongFunction<FinanceLedgerEntry> mapper,
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

    private int parseRangeDays(String range) {
        if ("7d".equalsIgnoreCase(range)) return 7;
        if ("90d".equalsIgnoreCase(range)) return 90;
        if ("1y".equalsIgnoreCase(range)) return 365;
        return 30;
    }

    private AdCampaign weightedPick(List<AdCampaign> campaigns) {
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

    private AdCampaign.AdPlacement parsePlacement(String placementRaw) {
        if (placementRaw == null || placementRaw.isBlank()) return AdCampaign.AdPlacement.SIDEBAR_CARD;
        try {
            return AdCampaign.AdPlacement.valueOf(placementRaw.trim().toUpperCase());
        } catch (Exception ignored) {
            return AdCampaign.AdPlacement.SIDEBAR_CARD;
        }
    }

    private AdCampaign.AdCreative chooseCreative(AdCampaign campaign, AdCampaign.AdPlacement placement) {
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

    private boolean hasRenderableCreativeForPlacement(AdCampaign campaign, AdCampaign.AdPlacement placement) {
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

    private String appendAffiliateParams(String targetUrl, String param, String code) {
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

    private String resolveCreatorId(String projectId) {
        if (projectId == null) return null;
        Project project = projectService.getProjectById(projectId);
        return project == null ? null : project.getAuthorId();
    }

    private boolean shouldTrackEvent(String type, String campaignId, String projectId, String clientIp) {
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

    private User resolveFinanceOwner(User requester, String ownerId, boolean requireOrgFinanceManage) {
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

    private List<String> executePayoutTransfers(User creator, long totalAmountCents, PlatformFinanceSettings settings) {
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
                        settings.getCurrency(),
                        "Modtale organization payout (" + creator.getUsername() + ")",
                        Map.of(
                                "organizationId", creator.getId(),
                                "memberUserId", member.getId(),
                                "source", "modtale_finance"
                        )
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
                settings.getCurrency(),
                "Modtale creator payout",
                Map.of("creatorId", creator.getId(), "source", "modtale_finance")
        );

        if (!payoutResult.success()) {
            throw new IllegalStateException("Stripe payout failed: " + payoutResult.error());
        }

        return List.of(payoutResult.id());
    }

    private void ensureStripePayoutReady(User accountOwner) {
        if (accountOwner.getStripeConnectAccountId() == null || accountOwner.getStripeConnectAccountId().isBlank()) {
            throw new IllegalStateException("Connect Stripe first before requesting payouts.");
        }
        if (stripeGatewayService.isEnabled() && !accountOwner.isStripePayoutsEnabled()) {
            throw new IllegalStateException("Stripe onboarding is incomplete for " + accountOwner.getUsername() + ". Refresh Stripe status after onboarding.");
        }
    }

    private void validateOrgPayoutShares(User org, List<User.OrgPayoutShare> shares) {
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

    private void requireProjectMonetizationOwner(User requester, Project project) {
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

    private void requireOrganizationOwner(User requester, User organization) {
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

    private void applyCampaignPayload(AdCampaign campaign, Map<String, Object> payload, boolean isCreate) {
        if (payload == null) {
            throw new IllegalArgumentException("Campaign payload is required.");
        }

        String name = asString(payload.get("name"));
        if (isCreate && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("Campaign name is required.");
        }
        if (name != null && !name.isBlank()) campaign.setName(name);

        String providerTypeRaw = asString(payload.get("providerType"));
        if (providerTypeRaw != null && !providerTypeRaw.isBlank()) {
            try {
                campaign.setProviderType(AdCampaign.ProviderType.valueOf(providerTypeRaw));
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Invalid campaign provider type.");
            }
        }

        if (payload.containsKey("providerName")) campaign.setProviderName(asString(payload.get("providerName")));
        if (payload.containsKey("providerPlacementKey")) campaign.setProviderPlacementKey(asString(payload.get("providerPlacementKey")));
        if (payload.containsKey("sponsorName")) campaign.setSponsorName(asString(payload.get("sponsorName")));
        if (payload.containsKey("headline")) campaign.setHeadline(asString(payload.get("headline")));
        if (payload.containsKey("body")) campaign.setBody(asString(payload.get("body")));
        if (payload.containsKey("callToAction")) campaign.setCallToAction(asString(payload.get("callToAction")));
        if (payload.containsKey("imageUrl")) campaign.setImageUrl(asString(payload.get("imageUrl")));
        if (payload.containsKey("targetUrl")) campaign.setTargetUrl(asString(payload.get("targetUrl")));
        if (payload.containsKey("affiliateParam")) campaign.setAffiliateParam(asString(payload.get("affiliateParam")));
        if (payload.containsKey("affiliateCode")) campaign.setAffiliateCode(asString(payload.get("affiliateCode")));
        if (payload.containsKey("active")) campaign.setActive(asBoolean(payload.get("active")));
        if (payload.containsKey("privacyRespecting")) campaign.setPrivacyRespecting(asBoolean(payload.get("privacyRespecting")));
        if (payload.containsKey("nonIntrusive")) campaign.setNonIntrusive(asBoolean(payload.get("nonIntrusive")));
        if (payload.containsKey("testCampaign")) campaign.setTestCampaign(asBoolean(payload.get("testCampaign")));
        if (payload.containsKey("baseRevenuePerClickCents")) campaign.setBaseRevenuePerClickCents(Math.max(0, asInt(payload.get("baseRevenuePerClickCents"), campaign.getBaseRevenuePerClickCents())));
        if (payload.containsKey("weight")) campaign.setWeight(Math.max(1, asInt(payload.get("weight"), campaign.getWeight())));

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
                    String imageUrl = asString(map.get("imageUrl"));
                    if (imageUrl == null || imageUrl.isBlank()) continue;
                    AdCampaign.AdCreative creative = new AdCampaign.AdCreative();
                    creative.setImageUrl(imageUrl);
                    creative.setAltText(asString(map.get("altText")));
                    try {
                        String placementRaw = asString(map.get("placement"));
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

    private Map<String, Object> toCampaignMap(AdCampaign campaign) {
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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value == null) return false;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String normalizeFrontendUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) return "http://localhost:5173";
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }
}
