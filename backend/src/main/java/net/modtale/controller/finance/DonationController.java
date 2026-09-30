package net.modtale.controller.finance;

import net.modtale.model.user.User;
import jakarta.validation.Valid;
import net.modtale.model.dto.request.finance.CreateSupportCheckoutRequest;
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

    @PostMapping("/projects/{projectId}/donations/checkout-url")
    public ResponseEntity<?> createDonationCheckout(
            @PathVariable String projectId,
            @Valid @RequestBody CreateSupportCheckoutRequest request
    ) {
        try {
            User donor = accountService.getCurrentUser();
            return ResponseEntity.ok(financeDonationService.createDonationCheckout(projectId, request.amountCents(), request.recurring(), donor, request.guestCheckout()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/donations/confirm")
    public ResponseEntity<?> confirmDonation(@RequestBody java.util.Map<String, String> payload) {
        try {
            return ResponseEntity.ok(financeDonationService.confirmDonationIntent(payload.get("intentId")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
