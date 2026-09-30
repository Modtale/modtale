package net.modtale.controller.finance;

import net.modtale.model.dto.request.finance.StageAdSettlementRequest;
import net.modtale.service.finance.AdSettlementStagingService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/admin/ad-settlements")
@PreAuthorize("!hasAuthority('ROLE_API') && @apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
public class AdSettlementAdminController {
    private final AdSettlementStagingService staging;
    private final AccountService accounts;
    public AdSettlementAdminController(AdSettlementStagingService staging, AccountService accounts) { this.staging = staging; this.accounts = accounts; }
    @GetMapping public Object list() { return staging.list(accounts.getCurrentUser()); }
    @GetMapping("/{id}") public Object get(@PathVariable String id) { return staging.get(accounts.getCurrentUser(), id); }
    @PostMapping public Object create(@RequestBody StageAdSettlementRequest request) { return staging.create(accounts.getCurrentUser(), request); }
    @PostMapping("/{id}/amendments") public Object amend(@PathVariable String id, @RequestBody AdSettlementStagingService.Amendment request) { return staging.amend(accounts.getCurrentUser(), id, request); }
    @PostMapping("/{id}/reviews") public Object review(@PathVariable String id, @RequestBody AdSettlementStagingService.Review request) { return staging.review(accounts.getCurrentUser(), id, request); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException error) { return ResponseEntity.badRequest().body(error.getMessage()); }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> forbidden(SecurityException error) { return ResponseEntity.status(403).body(error.getMessage()); }
}
