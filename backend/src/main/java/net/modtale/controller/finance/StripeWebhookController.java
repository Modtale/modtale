package net.modtale.controller.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import net.modtale.service.finance.StripeWebhookEvents;
import net.modtale.model.finance.PaymentWebhookReceipt;
import net.modtale.repository.finance.PaymentWebhookReceiptRepository;
import net.modtale.service.finance.DonationCheckoutService;
import net.modtale.service.finance.StripeGatewayService;
import net.modtale.service.finance.RecurringSupportService;
import net.modtale.service.finance.PaymentAdjustmentService;
import net.modtale.service.finance.StripeWebhookSignature;
import net.modtale.service.finance.FinanceSourceKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/webhooks")
public class StripeWebhookController {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DonationCheckoutService donations;
    private final PaymentWebhookReceiptRepository receipts;
    private final StripeGatewayService gateway;
    private final RecurringSupportService recurring;
    private final PaymentAdjustmentService adjustments;
    private final String webhookSecret;

    public StripeWebhookController(DonationCheckoutService donations, PaymentWebhookReceiptRepository receipts,
            StripeGatewayService gateway, RecurringSupportService recurring, PaymentAdjustmentService adjustments, @Value("${app.finance.stripe.webhook-secret:}") String webhookSecret) {
        this.donations = donations;
        this.receipts = receipts;
        this.gateway = gateway;
        this.recurring = recurring;
        this.adjustments = adjustments;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<?> receive(@RequestBody byte[] body,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) return ResponseEntity.status(503).build();
        if (body.length > 262144) return ResponseEntity.status(413).build();
        if (!StripeWebhookSignature.verify(body, signature, webhookSecret, Instant.now().getEpochSecond())) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> event;
        try {
            event = JSON.readValue(body, new TypeReference<>() {});
        } catch (Exception invalidJson) {
            return ResponseEntity.badRequest().build();
        }
        if (!(event.get("id") instanceof String eventId) || !eventId.startsWith("evt_")
                || !(event.get("type") instanceof String type)) return ResponseEntity.badRequest().build();
        // Existing obligations keep reconciling even when new checkout creation is paused.
        if (!(event.get("livemode") instanceof Boolean live) || !gateway.isReconciliationEnabled() || live == gateway.isTestMode()) return ResponseEntity.status(503).build();
        if (!StripeGatewayService.API_VERSION.equals(event.get("api_version"))) return ResponseEntity.status(503).build();
        String accountId = gateway.getPlatformAccountId();
        if (event.containsKey("account") && !accountId.equals(event.get("account"))) return ResponseEntity.status(503).build();
        String receiptId = FinanceSourceKey.stripe(!live, accountId, "event:" + eventId);
        if (receipts.existsById(receiptId)) return ResponseEntity.ok(Map.of("received", true));
        if (StripeWebhookEvents.REQUIRED.contains(type)) {
            if (!(event.get("data") instanceof Map<?, ?> data) || !(data.get("object") instanceof Map<?, ?> object)) {
                return ResponseEntity.badRequest().build();
            }
            @SuppressWarnings("unchecked") Map<String, Object> session = (Map<String, Object>) object;
            try {
                if (type.startsWith("charge.dispute.") && session.get("id") instanceof String disputeId && session.get("charge") instanceof String chargeId) adjustments.synchronizeDispute(disputeId, chargeId);
                else if ("charge.refunded".equals(type) && session.get("id") instanceof String chargeId) adjustments.synchronizeCharge(chargeId, eventId + ":" + chargeId);
                else if (type.startsWith("refund.") && session.get("charge") instanceof String chargeId) adjustments.synchronizeCharge(chargeId, eventId + ":" + chargeId);
                else if (type.startsWith("checkout.session.")) donations.handlePaidCheckout(session);
                else if ("invoice.paid".equals(type)) recurring.handlePaidInvoice(session);
                else if (type.startsWith("customer.subscription.") && session.get("id") instanceof String id) recurring.refreshSubscription(id);
                else if ("invoice.payment_failed".equals(type) && session.get("parent") instanceof Map<?, ?> parent
                        && parent.get("subscription_details") instanceof Map<?, ?> details && details.get("subscription") instanceof String id) recurring.refreshSubscription(id);
            } catch (IllegalArgumentException notRecordedYet) {
                return ResponseEntity.status(503).build();
            }
        }
        // Mark only after fulfillment: crashes/retries cannot lose a payment between receipt and credit.
        try {
            receipts.insert(new PaymentWebhookReceipt(receiptId, type, Instant.now()));
        } catch (DuplicateKeyException alreadyProcessed) {
            // The deterministic payment ledger ID also deduplicates separate events for one session.
        }
        return ResponseEntity.ok(Map.of("received", true));
    }
}
