package net.modtale.controller.finance;

import net.modtale.model.user.User;
import net.modtale.service.finance.DonationCheckoutService;
import net.modtale.service.user.account.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance")
public class DonationController {

    @Autowired private DonationCheckoutService financeDonationService;
    @Autowired private AccountService accountService;

    @GetMapping("/projects/{projectId}/donation-config")
    public ResponseEntity<?> getDonationConfig(@PathVariable String projectId) {
        try {
            return ResponseEntity.ok(financeDonationService.getDonationConfig(projectId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/projects/{projectId}/donations/checkout-url")
    public ResponseEntity<?> createDonationCheckout(
            @PathVariable String projectId,
            @RequestParam long amountCents,
            @RequestParam(defaultValue = "false") boolean recurring,
            @RequestParam(defaultValue = "false") boolean guestCheckout
    ) {
        try {
            User donor = accountService.getCurrentUser();
            return ResponseEntity.ok(financeDonationService.createDonationCheckout(projectId, amountCents, recurring, donor, guestCheckout));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/donations/confirm")
    public ResponseEntity<?> confirmDonation(@RequestParam String intentId) {
        try {
            return ResponseEntity.ok(financeDonationService.confirmDonationIntent(intentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
