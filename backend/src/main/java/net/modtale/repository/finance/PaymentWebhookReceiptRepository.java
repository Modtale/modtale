package net.modtale.repository.finance;

import net.modtale.model.finance.PaymentWebhookReceipt;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentWebhookReceiptRepository extends MongoRepository<PaymentWebhookReceipt, String> {}
