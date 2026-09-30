package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.Validation;
import net.modtale.model.dto.request.finance.CreateSupportCheckoutRequest;
import net.modtale.model.dto.request.finance.StageAdSettlementRequest;
import org.junit.jupiter.api.Test;

class FinanceRequestAmountTest {
    @Test void checkoutJsonPreservesAndRejectsFractionalCents() throws Exception {
        var mapper = new ObjectMapper();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var valid = mapper.readValue("{\"amountCents\":500,\"recurring\":false,\"guestCheckout\":true}", CreateSupportCheckoutRequest.class);
            assertTrue(factory.getValidator().validate(valid).isEmpty());
            var fractional = mapper.readValue("{\"amountCents\":500.99}", CreateSupportCheckoutRequest.class);
            assertEquals(new BigDecimal("500.99"), fractional.amountCents());
            assertFalse(factory.getValidator().validate(fractional).isEmpty());
            assertFalse(factory.getValidator().validate(mapper.readValue("{}", CreateSupportCheckoutRequest.class)).isEmpty());
        }
    }
    @Test void stagedReportsCannotTruncateFractionalDepositCents() {
        var report = new StageAdSettlementRequest("provider", "account", "report", "deposit", "usd", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), new BigDecimal("1000.01"), "a".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> AdSettlementStagingService.validate(report));
    }
}
