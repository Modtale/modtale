package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import net.modtale.model.finance.FinanceLedgerEntry;
import org.junit.jupiter.api.Test;

class PaymentSettlementServiceTest {
    private FinanceLedgerEntry pending() {
        var pending = new FinanceLedgerEntry(); pending.setId("observation"); pending.setCreatorId("creator"); pending.setGrossCents(500);
        pending.setPlatformCents(50); pending.getMetadata().put("providerAccountId", "acct_platform"); pending.getMetadata().put("paymentIntentId", "pi_test"); pending.getMetadata().put("testMode", "true");
        return pending;
    }
    private Map<String, Object> payment(long fee, String status) {
        return Map.of("id", "pi_test", "status", "succeeded", "livemode", false, "currency", "usd", "amount_received", 500,
                "latest_charge", Map.of("id", "ch_test", "paid", true, "captured", true, "disputed", false, "amount_refunded", 0,
                        "balance_transaction", Map.of("id", "txn_test", "status", status, "currency", "usd", "amount", 500, "fee", fee, "net", 500 - fee)));
    }
    @Test void actualProcessorFeeComesOutOfCreatorRemainder() {
        var credit = PaymentSettlementService.settledCredit(pending(), payment(45, "available"));
        assertNotNull(credit); assertEquals(405, credit.getCreatorCents()); assertEquals(50, credit.getPlatformCents());
        assertEquals(45, credit.getProcessorFeeCents()); assertEquals(450, credit.getCreatorGrossCents());
        assertEquals(credit.getGrossCents(), credit.getCreatorCents() + credit.getPlatformCents() + credit.getProcessorFeeCents());
        assertEquals("stripe:test:acct_platform:settlement:txn_test", credit.getId()); assertNull(credit.getExpiresAt());
    }
    @Test void pendingProviderFundsAndFeeDominatedPaymentsAreNotCredited() {
        assertNull(PaymentSettlementService.settledCredit(pending(), payment(45, "pending")));
        assertNull(PaymentSettlementService.settledCredit(pending(), payment(451, "available")));
    }
    @Test void paymentCannotCrossCurrencyOrTestLiveBoundaries() {
        var payment = new HashMap<>(payment(45, "available")); payment.put("livemode", true);
        assertNull(PaymentSettlementService.settledCredit(pending(), payment));
        payment.put("livemode", false); payment.put("currency", "eur");
        assertNull(PaymentSettlementService.settledCredit(pending(), payment));
    }
}
