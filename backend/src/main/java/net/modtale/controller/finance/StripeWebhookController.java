package net.modtale.controller.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import net.modtale.model.finance.PaymentWebhookReceipt;
import net.modtale.repository.finance.PaymentWebhookReceiptRepository;
import net.modtale.service.finance.DonationCheckoutService;
import net.modtale.service.finance.StripeGatewayService;
import net.modtale.service.finance.StripeWebhookSignature;
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
    private final String webhookSecret;

    public StripeWebhookController(DonationCheckoutService donations, PaymentWebhookReceiptRepository receipts,
            StripeGatewayService gateway, @Value("${app.finance.stripe.webhook-secret:}") String webhookSecret) {
        this.donations = donations;
        this.receipts = receipts;
        this.gateway = gateway;
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
        // Do not acknowledge live events as fulfilled until the full live-money lifecycle is enabled.
        if (!Boolean.FALSE.equals(event.get("livemode")) || !gateway.isTestMode()) return ResponseEntity.status(503).build();
        if (receipts.existsById(eventId)) return ResponseEntity.ok(Map.of("received", true));
        if ("checkout.session.completed".equals(type) || "checkout.session.async_payment_succeeded".equals(type)) {
            if (!(event.get("data") instanceof Map<?, ?> data) || !(data.get("object") instanceof Map<?, ?> object)) {
                return ResponseEntity.badRequest().build();
            }
            @SuppressWarnings("unchecked") Map<String, Object> session = (Map<String, Object>) object;
            try {
                donations.handlePaidCheckout(session);
            } catch (IllegalArgumentException notRecordedYet) {
                return ResponseEntity.status(503).build();
            }
        }
        // Mark only after fulfillment: crashes/retries cannot lose a payment between receipt and credit.
        try {
            receipts.insert(new PaymentWebhookReceipt(eventId, type, Instant.now()));
        } catch (DuplicateKeyException alreadyProcessed) {
            // The deterministic payment ledger ID also deduplicates separate events for one session.
        }
        return ResponseEntity.ok(Map.of("received", true));
    }
}
