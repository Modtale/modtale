package net.modtale.model.finance;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Minimal event receipt; payment/contact data and raw webhook bodies are never stored here. */
@Document(collection = "payment_webhook_receipts")
public record PaymentWebhookReceipt(@Id String id, String type, Instant processedAt) {}
