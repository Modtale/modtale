package net.modtale.controller.finance;

import jakarta.servlet.http.HttpServletRequest;
import net.modtale.model.dto.request.finance.UpdatePlatformFinanceSettingsRequest;
import net.modtale.model.dto.request.finance.UpdateProjectMonetizationRequest;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.finance.FinanceService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

    @Autowired private FinanceService financeService;
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
            return ResponseEntity.ok(financeService.getCreatorOverview(user, ownerId, range));
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
        return ResponseEntity.ok(financeService.getFinanceContexts(user));
    }

    @PostMapping("/creator/stripe/onboarding-link")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_EDIT_BASIC', authentication)")
    public ResponseEntity<?> createStripeOnboardingLink(@RequestBody(required = false) Map<String, String> payload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String returnPath = payload != null ? payload.get("returnPath") : null;
        String ownerId = payload != null ? payload.get("ownerId") : null;
        try {
            return ResponseEntity.ok(financeService.createStripeOnboardingLink(user, ownerId, returnPath));
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
            return ResponseEntity.ok(financeService.refreshStripeStatus(user, ownerId));
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
            return ResponseEntity.ok(financeService.requestPayout(user, ownerId, amountCents));
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
            return ResponseEntity.ok(financeService.getOrgPayoutPolicy(user, orgId));
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
            return ResponseEntity.ok(financeService.updateOrgPayoutPolicy(user, orgId, payoutMode, shares));
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

        return ResponseEntity.ok(financeService.updateProjectMonetization(user, project, request));
    }

    @GetMapping("/projects/{projectId}/donation-config")
    public ResponseEntity<?> getDonationConfig(@PathVariable String projectId) {
        try {
            return ResponseEntity.ok(financeService.getDonationConfig(projectId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/projects/{projectId}/donations/checkout-url")
    public ResponseEntity<?> createDonationCheckout(
            @PathVariable String projectId,
            @RequestParam long amountCents,
            @RequestParam(defaultValue = "false") boolean recurring
    ) {
        try {
            return ResponseEntity.ok(financeService.createDonationCheckout(projectId, amountCents, recurring));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/donations/confirm")
    public ResponseEntity<?> confirmDonation(@RequestParam String intentId) {
        try {
            return ResponseEntity.ok(financeService.confirmDonationIntent(intentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/ads/slot/{projectId}")
    public ResponseEntity<?> getAdSlot(
            @PathVariable String projectId,
            @RequestParam(required = false) String placement
    ) {
        return ResponseEntity.ok(financeService.getAdSlotForProject(projectId, placement));
    }

    @PostMapping("/ads/impression")
    public ResponseEntity<?> trackAdImpression(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String campaignId = payload.get("campaignId");
        String projectId = payload.get("projectId");
        if (campaignId == null || projectId == null) return ResponseEntity.badRequest().build();

        String ip = getClientIp(request);
        financeService.trackAdImpression(campaignId, projectId, ip);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/ads/click/{campaignId}")
    public ResponseEntity<Void> clickAd(
            @PathVariable String campaignId,
            @RequestParam String projectId,
            HttpServletRequest request
    ) {
        String ip = getClientIp(request);
        String url = financeService.registerAdClickAndResolveUrl(campaignId, projectId, ip);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(url));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/admin/overview")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> getAdminOverview(@RequestParam(defaultValue = "30d") String range) {
        User user = accountService.getCurrentUser();
        if (user == null || !accessControlService.isAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(financeService.getAdminOverview(range));
    }

    @PutMapping("/admin/settings")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> updateAdminSettings(@RequestBody UpdatePlatformFinanceSettingsRequest request) {
        User user = accountService.getCurrentUser();
        if (user == null || !accessControlService.isAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(financeService.updatePlatformSettings(request));
    }

    @GetMapping("/admin/ads/campaigns")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> getAdCampaigns() {
        User user = accountService.getCurrentUser();
        if (!isSuperAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(financeService.getAdCampaigns());
    }

    @PostMapping("/admin/ads/campaigns")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> createAdCampaign(@RequestBody Map<String, Object> payload) {
        User user = accountService.getCurrentUser();
        if (!isSuperAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            return ResponseEntity.ok(financeService.createAdCampaign(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/admin/ads/campaigns/{campaignId}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> updateAdCampaign(@PathVariable String campaignId, @RequestBody Map<String, Object> payload) {
        User user = accountService.getCurrentUser();
        if (!isSuperAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            return ResponseEntity.ok(financeService.updateAdCampaign(campaignId, payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/admin/ads/campaigns/{campaignId}/start")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> startAdCampaign(@PathVariable String campaignId) {
        User user = accountService.getCurrentUser();
        if (!isSuperAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            return ResponseEntity.ok(financeService.setCampaignActiveState(campaignId, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/admin/ads/campaigns/{campaignId}/pause")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> pauseAdCampaign(@PathVariable String campaignId) {
        User user = accountService.getCurrentUser();
        if (!isSuperAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            return ResponseEntity.ok(financeService.setCampaignActiveState(campaignId, false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/admin/ads/test-slot/{projectId}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> getTestAdSlot(
            @PathVariable String projectId,
            @RequestParam(required = false) String placement
    ) {
        User user = accountService.getCurrentUser();
        if (!isSuperAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(financeService.getTestAdSlotForProject(projectId, placement));
    }

    @GetMapping("/public/daily-revenue")
    public ResponseEntity<?> getPublicDailyRevenue(@RequestParam(defaultValue = "90") int days) {
        long secondsToMidnight = Duration.between(LocalDateTime.now(), LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay()).getSeconds();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(secondsToMidnight, TimeUnit.SECONDS).cachePublic())
                .body(Map.of(
                        "currency", financeService.getSettings().getCurrency(),
                        "days", Math.max(1, Math.min(365, days)),
                        "data", financeService.getPublicDailyRevenue(days)
                ));
    }

    @GetMapping("/public/settings")
    public ResponseEntity<?> getPublicMonetizationSettings() {
        return ResponseEntity.ok(Map.of(
                "adCreatorSplitPercent", financeService.getSettings().getAdCreatorSplitBps() / 100.0,
                "donationPlatformCutPercent", financeService.getSettings().getDonationPlatformCutBps() / 100.0,
                "fundExpiryDays", financeService.getSettings().getFundExpiryDays()
        ));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return xfHeader == null ? request.getRemoteAddr() : xfHeader.split(",")[0];
    }

    private boolean isSuperAdmin(User user) {
        return accessControlService.isSuperAdmin(user);
    }
}
