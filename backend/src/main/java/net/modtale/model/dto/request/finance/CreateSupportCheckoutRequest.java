package net.modtale.model.dto.request.finance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateSupportCheckoutRequest(@Min(100) @Max(100000) long amountCents,
        boolean recurring, boolean guestCheckout) {}
