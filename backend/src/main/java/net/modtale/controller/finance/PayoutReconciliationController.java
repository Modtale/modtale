package net.modtale.controller.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import net.modtale.service.user.account.AccountService;
import net.modtale.service.finance.CreatorPayoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/finance/payout-reconciliation")
@PreAuthorize("!hasAuthority('ROLE_API') && @apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
public class PayoutReconciliationController {
    private final CreatorPayoutService payouts;
    private final AccountService accounts;
    public PayoutReconciliationController(CreatorPayoutService payouts, AccountService accounts) { this.payouts = payouts; this.accounts = accounts; }
    public record Confirmation(@NotBlank String requestId, @Min(0) int recipientIndex,
            @NotBlank @Pattern(regexp = "tr_[A-Za-z0-9]+") String transferId, @NotBlank @Size(max = 1000) String reason) {}
    @GetMapping public ResponseEntity<?> list() { return ResponseEntity.ok(payouts.getReviewRequests()); }
    @PostMapping("/confirm-existing-transfer") public ResponseEntity<?> confirm(@Valid @RequestBody Confirmation body) {
        return ResponseEntity.ok(CreatorPayoutService.toResponse(payouts.reconcileKnownTransfer(body.requestId(), body.recipientIndex(), body.transferId(), accounts.getCurrentUser(), body.reason())));
    }
}
