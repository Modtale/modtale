package net.modtale.controller.finance;

import java.util.Map;
import net.modtale.model.user.User;
import net.modtale.service.finance.RecurringSupportService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/support")
@PreAuthorize("isAuthenticated() && !hasAuthority('ROLE_API')")
public class RecurringSupportController {
    private final RecurringSupportService support;
    private final AccountService accounts;
    public RecurringSupportController(RecurringSupportService support, AccountService accounts) { this.support = support; this.accounts = accounts; }

    @GetMapping("/subscriptions")
    public ResponseEntity<?> subscriptions() {
        User user = accounts.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(support.listForDonor(user));
    }

    @PostMapping("/subscriptions/{subscriptionId}/billing-portal")
    public ResponseEntity<?> billingPortal(@PathVariable String subscriptionId) {
        User user = accounts.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try { return ResponseEntity.ok(Map.of("url", support.openBillingPortal(user, subscriptionId))); }
        catch (SecurityException forbidden) { return ResponseEntity.status(403).build(); }
        catch (IllegalArgumentException missing) { return ResponseEntity.notFound().build(); }
        catch (IllegalStateException unavailable) { return ResponseEntity.status(503).body(unavailable.getMessage()); }
    }
}
