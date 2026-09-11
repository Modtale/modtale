package net.modtale.controller.finance;

import net.modtale.model.dto.request.finance.UpdateProjectMonetizationRequest;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.finance.EarningsAccountService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.account.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance")
public class CreatorRevenueController {

    @Autowired private EarningsAccountService financeAccountService;
    @Autowired private AccountService accountService;
    @Autowired private ProjectService projectService;
    @Autowired private AccessControlService accessControlService;

    @GetMapping("/creator/overview")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> getCreatorOverview(
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) String ownerId
    ) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            return ResponseEntity.ok(financeAccountService.getCreatorOverview(user, ownerId, range));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/creator/contexts")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> getFinanceContexts() {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(financeAccountService.getFinanceContexts(user));
    }

    @PostMapping("/creator/stripe/onboarding-link")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_EDIT_BASIC', authentication)")
    public ResponseEntity<?> createStripeOnboardingLink(@RequestBody(required = false) Map<String, String> payload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String returnPath = payload != null ? payload.get("returnPath") : null;
        String ownerId = payload != null ? payload.get("ownerId") : null;
        try {
            return ResponseEntity.ok(financeAccountService.createStripeOnboardingLink(user, ownerId, returnPath));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/creator/stripe/refresh-status")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> refreshStripeStatus(@RequestBody(required = false) Map<String, String> payload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String ownerId = payload != null ? payload.get("ownerId") : null;
        try {
            return ResponseEntity.ok(financeAccountService.refreshStripeStatus(user, ownerId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/creator/payouts/request")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> requestPayout(@RequestBody(required = false) Map<String, Object> payload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Long amountCents = null;
        String ownerId = null;
        if (payload != null && payload.get("amountCents") != null) {
            try {
                amountCents = Long.parseLong(String.valueOf(payload.get("amountCents")));
            } catch (Exception ignored) {}
        }
        if (payload != null && payload.get("ownerId") != null) {
            ownerId = String.valueOf(payload.get("ownerId"));
        }

        try {
            return ResponseEntity.ok(financeAccountService.requestPayout(user, ownerId, amountCents));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/creator/orgs/{orgId}/payout-policy")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_EDIT_METADATA', authentication)")
    public ResponseEntity<?> getOrgPayoutPolicy(@PathVariable String orgId) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            return ResponseEntity.ok(financeAccountService.getOrgPayoutPolicy(user, orgId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/creator/orgs/{orgId}/payout-policy")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_EDIT_METADATA', authentication)")
    public ResponseEntity<?> updateOrgPayoutPolicy(@PathVariable String orgId, @RequestBody Map<String, Object> payload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String payoutMode = payload == null || payload.get("payoutMode") == null ? null : String.valueOf(payload.get("payoutMode"));
        List<Map<String, Object>> shares = new ArrayList<>();
        if (payload != null && payload.get("shares") instanceof List<?> rawShares) {
            for (Object item : rawShares) {
                if (item instanceof Map<?, ?> rawMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) rawMap;
                    shares.add(typed);
                }
            }
        }

        try {
            return ResponseEntity.ok(financeAccountService.updateOrgPayoutPolicy(user, orgId, payoutMode, shares));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/projects/{projectId}/settings")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#projectId, 'PROJECT_EDIT_METADATA', authentication)")
    public ResponseEntity<?> updateProjectMonetization(
            @PathVariable String projectId,
            @RequestBody UpdateProjectMonetizationRequest request
    ) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Project project = projectService.getRawProjectById(projectId);
        if (project == null) return ResponseEntity.notFound().build();
        if (!accessControlService.hasProjectPermission(project, user, "PROJECT_EDIT_METADATA")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(financeAccountService.updateProjectMonetization(user, project, request));
    }
}
