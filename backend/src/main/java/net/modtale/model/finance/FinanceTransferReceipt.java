package net.modtale.model.finance;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Unique provider/account/mode reference claims a transfer for exactly one reserved recipient. */
@Document(collection = "finance_transfer_receipts")
public record FinanceTransferReceipt(@Id String id, String payoutRequestId, int recipientIndex,
        String transferId, String providerAccountId, boolean testMode, String destination,
        long amountCents, String currency, String confirmedBy, String confirmationReason, Instant confirmedAt) {}
