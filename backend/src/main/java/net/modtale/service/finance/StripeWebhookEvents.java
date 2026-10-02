package net.modtale.service.finance;

import java.util.Set;

public final class StripeWebhookEvents {
    private StripeWebhookEvents() {}
    public static final Set<String> REQUIRED = Set.of(
            "checkout.session.completed", "checkout.session.async_payment_succeeded",
            "invoice.paid", "invoice.payment_failed", "customer.subscription.updated", "customer.subscription.deleted",
            "charge.refunded", "refund.created", "refund.updated", "charge.dispute.created", "charge.dispute.updated",
            "charge.dispute.closed", "charge.dispute.funds_withdrawn", "charge.dispute.funds_reinstated");
}
