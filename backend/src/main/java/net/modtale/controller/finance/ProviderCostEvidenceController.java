package net.modtale.controller.finance;

import jakarta.validation.Valid;
import net.modtale.service.finance.ProviderCostEvidenceService;
import net.modtale.service.user.account.AccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/finance/provider-costs")
@PreAuthorize("!hasAuthority('ROLE_API') && @apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
public class ProviderCostEvidenceController {
    private final ProviderCostEvidenceService costs;
    private final AccountService accounts;
    public ProviderCostEvidenceController(ProviderCostEvidenceService costs, AccountService accounts) { this.costs = costs; this.accounts = accounts; }
    @GetMapping public Object list() { return costs.list(accounts.getCurrentUser()); }
    @PostMapping("/import-stripe") public Object importStripe(@Valid @RequestBody ProviderCostEvidenceService.ImportRequest request) {
        return costs.retrieveAndImport(accounts.getCurrentUser(), request);
    }
}
