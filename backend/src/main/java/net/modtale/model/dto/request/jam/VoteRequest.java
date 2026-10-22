package net.modtale.model.dto.request.jam;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record VoteRequest(
        @NotBlank String submissionId,
        @NotBlank String categoryId,
        @NotNull @DecimalMin("1") @Digits(integer = 9, fraction = 0) BigDecimal score
) {
}
