package net.modtale.service.finance;

import net.modtale.model.finance.DonationIntent;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.finance.PlatformFinanceSettings;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.finance.DonationIntentRepository;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import net.modtale.service.project.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class DonationCheckoutService {

    @Autowired private EarningsAccountService financeAccountService;
    @Autowired private DonationIntentRepository donationIntentRepository;
    @Autowired private FinanceLedgerEntryRepository ledgerRepository;
    @Autowired private ProjectService projectService;
    @Autowired private StripeGatewayService stripeGatewayService;
    @Autowired private RevenueOpsSupport core;

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
        response.put("currency", financeAccountService.getSettings().getCurrency());
        response.put("minimumDonationCents", 100);
        return response;
    }

    public Map<String, Object> createDonationCheckout(String projectId, long amountCents, boolean recurring, User donor, boolean guestCheckout) {
        Project project = projectService.getProjectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found");
        }
        if (!project.isDonationsEnabled()) {
            throw new IllegalStateException("Donations are disabled by this creator for this project.");
        }

        PlatformFinanceSettings settings = financeAccountService.getSettings();
        long normalizedAmount = Math.max(100, Math.min(100000, amountCents));

        long platformCut = Math.round((normalizedAmount * project.getDonationPlatformCutBps()) / 10000.0);
        long creatorCut = normalizedAmount - platformCut;

        DonationIntent intent = new DonationIntent();
        intent.setProjectId(project.getId());
        intent.setCreatorId(project.getAuthorId());
        intent.setDonorUserId(donor != null ? donor.getId() : null);
        intent.setGuestDonation(guestCheckout || donor == null);
        intent.setAmountCents(normalizedAmount);
        intent.setCreatorCents(creatorCut);
        intent.setPlatformCents(platformCut);
        intent.setRecurring(recurring);
        intent.setCurrency(settings.getCurrency());
        intent.setStatus(DonationIntent.DonationStatus.PENDING);
        intent = donationIntentRepository.save(intent);

        String projectPath = projectService.getProjectLink(project);
        String successUrl = core.normalizeFrontendUrl() + projectPath + "?donation_intent=" + intent.getId() + "&donation_status=success";
        String cancelUrl = core.normalizeFrontendUrl() + projectPath + "?donation_intent=" + intent.getId() + "&donation_status=cancel";

        StripeGatewayService.StripeResult session = stripeGatewayService.createOrSimulateDonationCheckout(
                intent.getId(),
                project.getTitle(),
                normalizedAmount,
                recurring,
                successUrl,
                cancelUrl,
                settings.getCurrency(),
                settings.isMockStripeEnabled()
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

        Map<String, Object> session = stripeGatewayService.getCheckoutSession(
                intent.getStripeSessionId(),
                financeAccountService.getSettings().isMockStripeEnabled()
        );
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

        PlatformFinanceSettings settings = financeAccountService.getSettings();

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
}
