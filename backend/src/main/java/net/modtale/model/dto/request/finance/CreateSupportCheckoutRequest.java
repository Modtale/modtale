package net.modtale.model.dto.request.finance;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** Preserve the submitted numeric value until validation; never truncate fractional cents. */
public record CreateSupportCheckoutRequest(@NotNull @DecimalMin("100") @DecimalMax("100000") @Digits(integer = 6, fraction = 0) BigDecimal amountCents,
        boolean recurring, boolean guestCheckout,
        @NotNull @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 0) BigDecimal expectedPlatformCutBps) {}
