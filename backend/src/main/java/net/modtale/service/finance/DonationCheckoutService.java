package net.modtale.service.finance;

import net.modtale.model.finance.DonationIntent;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.finance.PlatformFinanceSettings;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.finance.DonationIntentRepository;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import net.modtale.service.project.query.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import java.util.UUID;

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
    @Autowired private RecurringSupportService recurringSupport;

    public Map<String, Object> getDonationConfig(String projectId) {
        Project project = projectService.getProjectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("projectId", project.getId());
        response.put("donationsEnabled", project.isDonationsEnabled());
        response.put("checkoutEnabled", project.isDonationsEnabled() && stripeGatewayService.isCheckoutAvailable());
        response.put("recurringEnabled", stripeGatewayService.isOperational());
        response.put("testMode", stripeGatewayService.isTestMode() || stripeGatewayService.isMockEnabled());
        response.put("availabilityMessage", stripeGatewayService.getAvailabilityMessage());
        response.put("suggestedDonationCents", Math.max(100, project.getSuggestedDonationCents()));
        response.put("donationRecurringDefault", false);
        response.put("donationPlatformCutPercent", project.getDonationPlatformCutBps() / 100.0);
        response.put("currency", financeAccountService.getSettings().getCurrency());
        response.put("minimumDonationCents", FinanceAmounts.MIN_SUPPORT_CENTS);
        response.put("maximumDonationCents", FinanceAmounts.MAX_SUPPORT_CENTS);
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

        if (!stripeGatewayService.isCheckoutAvailable()) {
            throw new IllegalStateException(stripeGatewayService.getAvailabilityMessage());
        }
        if (recurring && (donor == null || guestCheckout || !stripeGatewayService.isOperational())) throw new IllegalArgumentException("Sign in to start monthly support.");
        PlatformFinanceSettings settings = financeAccountService.getSettings();
        if (!"usd".equalsIgnoreCase(settings.getCurrency())) {
            throw new IllegalStateException("This checkout currently supports USD only.");
        }
        long normalizedAmount = FinanceAmounts.validateSupportAmount(amountCents);

        long platformCut = FinanceAmounts.share(normalizedAmount, project.getDonationPlatformCutBps());
        long creatorCut = normalizedAmount - platformCut;

        DonationIntent intent = new DonationIntent();
        intent.setId(UUID.randomUUID().toString());
        if (!stripeGatewayService.isMockEnabled()) intent.setStripePlatformAccountId(stripeGatewayService.getPlatformAccountId());
        intent.setProjectId(project.getId());
        intent.setCreatorId(project.getAuthorId());
        intent.setDonorUserId(donor != null ? donor.getId() : null);
        intent.setGuestDonation(guestCheckout || donor == null);
        intent.setAmountCents(normalizedAmount);
        intent.setCreatorCents(creatorCut);
        intent.setPlatformCents(platformCut);
        intent.setRecurring(recurring);
        intent.setPlatformCutBps(project.getDonationPlatformCutBps());
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
                stripeGatewayService.isMockEnabled()
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
                stripeGatewayService.isMockEnabled()
        );
        if (isMatchingPaidSession(intent, session)) {
            if (intent.isRecurring()) recurringSupport.registerCheckout(intent, session);
            else if (isVerifiedPayment(intent, session)) completeDonationIntent(intent, session);
            return Map.of("ok", true, "status", "COMPLETED");
        }

        return Map.of("ok", false, "status", "PENDING");
    }

    private void completeDonationIntent(DonationIntent intent, Map<String, Object> sessionData) {
        if (intent.getStatus() == DonationIntent.DonationStatus.COMPLETED) return;

        FinanceLedgerEntry entry = new FinanceLedgerEntry();
        // MongoDB's unique _id makes repeated/concurrent confirmation and webhook delivery safe.
        // Insert before marking the intent complete: retries repair a crash between these writes.
        entry.setId(FinanceSourceKey.stripe(Boolean.FALSE.equals(sessionData.get("livemode")), intent.getStripePlatformAccountId(), "checkout:" + intent.getStripeSessionId()));
        entry.setCreatorId(intent.getCreatorId());
        entry.setProjectId(intent.getProjectId());
        entry.setType(FinanceLedgerEntry.LedgerType.DONATION);
        entry.setGrossCents(intent.getAmountCents());
        entry.setCreatorCents(intent.getCreatorCents());
        entry.setPlatformCents(intent.getPlatformCents());
        entry.setCurrency(intent.getCurrency());
        entry.setStatus(FinanceLedgerEntry.EntryStatus.PENDING);
        entry.setCreatedAt(LocalDateTime.now());

        entry.setRecurring(intent.isRecurring());
        entry.setStripeReference(intent.getStripeSessionId());
        entry.setExternalReference(intent.getId());
        if (sessionData != null && sessionData.get("simulated") != null) {
            entry.getMetadata().put("simulated", String.valueOf(sessionData.get("simulated")));
        }
        entry.getMetadata().put("settlement", "awaiting_reconciliation");
        entry.getMetadata().put("providerAccountId", intent.getStripePlatformAccountId());
        Object paymentIntent = sessionData.get("payment_intent");
        if (paymentIntent instanceof String paymentId && paymentId.startsWith("pi_")) entry.getMetadata().put("paymentIntentId", paymentId);
        entry.setCreatorGrossCents(intent.getCreatorCents());
        entry.getMetadata().put("testMode", String.valueOf(!Boolean.TRUE.equals(sessionData.get("livemode"))));
        try {
            ledgerRepository.insert(entry);
        } catch (DuplicateKeyException duplicate) {
            // A concurrent delivery already inserted this exact payment. Never overwrite it.
        }
        intent.setStatus(DonationIntent.DonationStatus.COMPLETED);
        intent.setCompletedAt(LocalDateTime.now());
        donationIntentRepository.save(intent);
    }

    public void handlePaidCheckout(Map<String, Object> session) {
        String sessionId = String.valueOf(session.get("id"));
        DonationIntent intent = donationIntentRepository.findByStripeSessionId(sessionId).orElse(null);
        if (intent == null) throw new IllegalArgumentException("Checkout session is not recorded yet.");
        if (!"paid".equals(session.get("payment_status"))) return;
        if (!isMatchingPaidSession(intent, session)) throw new IllegalArgumentException("Paid checkout does not match its recorded intent.");
        if (intent.isRecurring()) recurringSupport.registerCheckout(intent, session);
        else if (isVerifiedPayment(intent, session)) completeDonationIntent(intent, session);
    }

    static boolean isVerifiedPayment(DonationIntent intent, Map<String, Object> session) {
        return session != null && !intent.isRecurring() && "payment".equals(session.get("mode")) && isMatchingPaidSession(intent, session);
    }

    static boolean isMatchingPaidSession(DonationIntent intent, Map<String, Object> session) {
        if (session == null || Boolean.TRUE.equals(session.get("simulated"))) return false;
        if (!(session.get("livemode") instanceof Boolean)) return false;
        if (!"paid".equals(session.get("payment_status")) || !"complete".equals(session.get("status"))) return false;
        if (!(intent.isRecurring() ? "subscription" : "payment").equals(session.get("mode"))) return false;
        if (intent.getStripeSessionId() == null || !intent.getStripeSessionId().equals(session.get("id"))) return false;
        if (!intent.getCurrency().equalsIgnoreCase(String.valueOf(session.get("currency")))) return false;
        if (!(session.get("amount_total") instanceof Number amount)) return false;
        try { if (new java.math.BigDecimal(amount.toString()).longValueExact() != intent.getAmountCents()) return false; }
        catch (ArithmeticException invalid) { return false; }
        if (!(session.get("metadata") instanceof Map<?, ?> metadata) || !intent.getId().equals(metadata.get("intentId"))) return false;
        return true;
    }
}
