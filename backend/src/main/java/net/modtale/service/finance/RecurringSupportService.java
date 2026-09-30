package net.modtale.service.finance;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.modtale.model.finance.CreatorSupportSubscription;
import net.modtale.model.finance.DonationIntent;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.user.User;
import net.modtale.repository.finance.CreatorSupportSubscriptionRepository;
import net.modtale.repository.finance.DonationIntentRepository;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Monthly support is accounted per cash-paid invoice, never just per checkout or subscription. */
@Service
public class RecurringSupportService {
    private final CreatorSupportSubscriptionRepository subscriptions;
    private final DonationIntentRepository intents;
    private final FinanceLedgerEntryRepository ledger;
    private final StripeGatewayService gateway;
    private final RevenueOpsSupport core;
    private final net.modtale.service.project.query.ProjectService projects;

    public RecurringSupportService(CreatorSupportSubscriptionRepository subscriptions, DonationIntentRepository intents,
            FinanceLedgerEntryRepository ledger, StripeGatewayService gateway, RevenueOpsSupport core, net.modtale.service.project.query.ProjectService projects) {
        this.subscriptions = subscriptions; this.intents = intents; this.ledger = ledger; this.gateway = gateway; this.core = core; this.projects = projects;
    }

    public void registerCheckout(DonationIntent intent, Map<String, Object> session) {
        if (!DonationCheckoutService.isMatchingPaidSession(intent, session) || !intent.isRecurring() || intent.getDonorUserId() == null || !"subscription".equals(session.get("mode"))
                || !"complete".equals(session.get("status")) || !"paid".equals(session.get("payment_status"))
                || !intent.getStripeSessionId().equals(session.get("id"))
                || !(session.get("subscription") instanceof String subscriptionId) || !subscriptionId.startsWith("sub_")
                || !(session.get("customer") instanceof String customerId) || !customerId.startsWith("cus_")) throw new IllegalArgumentException("Subscription checkout is not ready.");
        if (subscriptions.existsById(subscriptionId)) return;
        CreatorSupportSubscription subscription = new CreatorSupportSubscription();
        subscription.setId(subscriptionId); subscription.setIntentId(intent.getId()); subscription.setDonorUserId(intent.getDonorUserId());
        subscription.setCreatorId(intent.getCreatorId()); subscription.setProjectId(intent.getProjectId()); subscription.setCustomerId(customerId);
        subscription.setProviderAccountId(intent.getStripePlatformAccountId());
        subscription.setAmountCents(intent.getAmountCents()); subscription.setPlatformCutBps(intent.getPlatformCutBps()); subscription.setCurrency(intent.getCurrency());
        subscription.setTestMode(Boolean.FALSE.equals(session.get("livemode"))); subscription.setStatus("active");
        try { subscriptions.insert(subscription); } catch (DuplicateKeyException duplicate) { /* another event recorded this checkout */ }
        intent.setStatus(DonationIntent.DonationStatus.COMPLETED); intents.save(intent);
    }

    public void handlePaidInvoice(Map<String, Object> invoice) {
        if (!(invoice.get("id") instanceof String invoiceId) || !invoiceId.startsWith("in_") || !"paid".equals(invoice.get("status"))) return;
        if (!(invoice.get("parent") instanceof Map<?, ?> parent) || !"subscription_details".equals(parent.get("type")) || !(parent.get("subscription_details") instanceof Map<?, ?> details)
                || !(details.get("subscription") instanceof String subscriptionId)) return;
        CreatorSupportSubscription subscription = subscriptions.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            if (!(details.get("metadata") instanceof Map<?, ?> metadata) || !(metadata.get("intentId") instanceof String intentId)) return;
            DonationIntent intent = intents.findById(intentId).orElseThrow(() -> new IllegalArgumentException("Subscription checkout is not recorded yet."));
            if (intent.getStripeSessionId() == null) throw new IllegalArgumentException("Subscription checkout is not recorded yet.");
            Map<String, Object> checkout = gateway.getCheckoutSession(intent.getStripeSessionId(), false);
            if (!subscriptionId.equals(checkout.get("subscription"))) throw new IllegalArgumentException("Subscription invoice does not match checkout.");
            registerCheckout(intent, checkout);
            subscription = subscriptions.findById(subscriptionId).orElseThrow();
        }
        if (!(invoice.get("livemode") instanceof Boolean) || subscription.isTestMode() != gateway.isTestMode()
                || !subscription.getProviderAccountId().equals(gateway.getPlatformAccountId())
                || !subscription.getCurrency().equals(invoice.get("currency")) || !subscription.getCustomerId().equals(invoice.get("customer"))
                || subscription.isTestMode() != Boolean.FALSE.equals(invoice.get("livemode"))
                || number(invoice.get("amount_paid")) != subscription.getAmountCents()) throw new IllegalArgumentException("Subscription invoice needs reconciliation.");
        Map<String, Object> payments = gateway.getInvoicePayments(invoiceId);
        // This product creates one fixed-price cash payment per month. Partial/multiple/credit-funded invoices are held.
        if (!Boolean.FALSE.equals(payments.get("has_more")) || !(payments.get("data") instanceof List<?> rows) || rows.size() != 1
                || !(rows.getFirst() instanceof Map<?, ?> payment) || !"paid".equals(payment.get("status"))
                || number(payment.get("amount_paid")) != subscription.getAmountCents()
                || !(payment.get("payment") instanceof Map<?, ?> source) || !"payment_intent".equals(source.get("type"))
                || !(source.get("payment_intent") instanceof String paymentId)) throw new IllegalArgumentException("Subscription payment needs reconciliation.");
        FinanceLedgerEntry observation = new FinanceLedgerEntry(); observation.setId(FinanceSourceKey.stripe(subscription.isTestMode(), subscription.getProviderAccountId(), "invoice:" + invoiceId));
        observation.setCreatorId(subscription.getCreatorId()); observation.setProjectId(subscription.getProjectId());
        observation.setType(FinanceLedgerEntry.LedgerType.DONATION); observation.setRecurring(true); observation.setCurrency(subscription.getCurrency());
        observation.setGrossCents(subscription.getAmountCents());
        long platform = FinanceAmounts.share(subscription.getAmountCents(), subscription.getPlatformCutBps());
        observation.setPlatformCents(platform); observation.setCreatorCents(subscription.getAmountCents() - platform);
        observation.setCreatorGrossCents(subscription.getAmountCents() - platform); observation.setStripeReference(invoiceId);
        observation.setStatus(FinanceLedgerEntry.EntryStatus.PENDING); observation.setExternalReference(subscriptionId);
        observation.getMetadata().put("providerAccountId", subscription.getProviderAccountId());
        observation.getMetadata().put("settlement", "awaiting_reconciliation"); observation.getMetadata().put("paymentIntentId", paymentId);
        observation.getMetadata().put("testMode", String.valueOf(subscription.isTestMode()));
        try { ledger.insert(observation); } catch (DuplicateKeyException duplicate) { /* invoice replay */ }
        refreshSubscription(subscriptionId);
    }

    public void refreshSubscription(String id) {
        CreatorSupportSubscription subscription = subscriptions.findById(id).orElse(null);
        if (subscription == null) return;
        if (subscription.isTestMode() != gateway.isTestMode() || !subscription.getProviderAccountId().equals(gateway.getPlatformAccountId())) {
            throw new IllegalArgumentException("Subscription account mode or scope changed.");
        }
        Map<String, Object> current = gateway.getSubscription(id);
        if (!(current.get("livemode") instanceof Boolean) || subscription.isTestMode() != Boolean.FALSE.equals(current.get("livemode"))
                || !id.equals(current.get("id")) || !subscription.getCustomerId().equals(current.get("customer"))
                || !(current.get("status") instanceof String)) {
            throw new IllegalArgumentException("Could not refresh subscription state.");
        }
        subscription.setStatus(String.valueOf(current.get("status")));
        subscription.setCancelAtPeriodEnd(Boolean.TRUE.equals(current.get("cancel_at_period_end")));
        subscription.setUpdatedAt(Instant.now()); subscriptions.save(subscription);
    }

    public List<Map<String, Object>> listForDonor(User donor) {
        return subscriptions.findByDonorUserId(donor.getId()).stream().map(subscription -> {
            var project = projects.getProjectById(subscription.getProjectId());
            var result = new java.util.HashMap<String, Object>();
            result.put("id", subscription.getId()); result.put("projectId", subscription.getProjectId());
            result.put("projectTitle", project == null ? "Unavailable project" : project.getTitle());
            result.put("projectUrl", project == null ? "" : projects.getProjectLink(project));
            result.put("amountCents", subscription.getAmountCents()); result.put("currency", subscription.getCurrency());
            result.put("status", subscription.getStatus()); result.put("cancelAtPeriodEnd", subscription.isCancelAtPeriodEnd());
            result.put("testMode", subscription.isTestMode()); return (Map<String, Object>) result;
        }).toList();
    }

    public String openBillingPortal(User donor, String subscriptionId) {
        CreatorSupportSubscription subscription = subscriptions.findById(subscriptionId).orElseThrow(() -> new IllegalArgumentException("Subscription not found."));
        if (!donor.getId().equals(subscription.getDonorUserId())) throw new SecurityException("This subscription belongs to another account.");
        if (subscription.isTestMode() != gateway.isTestMode() || !gateway.getPlatformAccountId().equals(subscription.getProviderAccountId())) throw new IllegalStateException("Billing account mode or scope does not match this subscription.");
        var result = gateway.createBillingPortalSession(subscription.getCustomerId(), core.normalizeFrontendUrl() + "/dashboard/finance");
        if (!result.success() || result.url() == null) throw new IllegalStateException("Billing management is temporarily unavailable. Please try again.");
        return result.url();
    }

    private static long number(Object value) {
        if (!(value instanceof Number)) return Long.MIN_VALUE;
        try { return new java.math.BigDecimal(value.toString()).longValueExact(); } catch (ArithmeticException invalid) { return Long.MIN_VALUE; }
    }
}
