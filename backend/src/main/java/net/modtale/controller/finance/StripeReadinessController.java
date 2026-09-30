package net.modtale.controller.finance;

import net.modtale.service.finance.StripeReadinessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/finance/stripe-readiness")
@PreAuthorize("!hasAuthority('ROLE_API') && @apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
public class StripeReadinessController {
    private final StripeReadinessService readiness;
    public StripeReadinessController(StripeReadinessService readiness) { this.readiness = readiness; }
    @GetMapping public StripeReadinessService.Report configuration() { return readiness.configuration(); }
    @PostMapping("/verify") public StripeReadinessService.Report verify() { return readiness.verifyProviderConfiguration(); }
}
