package net.modtale.controller.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import net.modtale.service.finance.PaymentAdjustmentService;
import net.modtale.service.user.account.AccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/finance/dispute-reconciliation")
@PreAuthorize("!hasAuthority('ROLE_API') && @apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
public class DisputeReconciliationController {
    private final PaymentAdjustmentService adjustments;
    private final AccountService accounts;
    public DisputeReconciliationController(PaymentAdjustmentService adjustments, AccountService accounts) { this.adjustments = adjustments; this.accounts = accounts; }
    public record Decision(@NotBlank String caseId, @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String expectedEvidenceDigest,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal creatorFeeCents, @NotBlank @Size(max = 1000) String reason) {}
    @GetMapping public Object list() { return adjustments.getDisputeCases(); }
    @PostMapping("/resolve") public Object resolve(@Valid @RequestBody Decision body) {
        return adjustments.resolveCase(body.caseId(), body.expectedEvidenceDigest(), body.creatorFeeCents().longValueExact(), accounts.getCurrentUser(), body.reason());
    }
}
