package net.modtale.controller.finance;

import net.modtale.model.dto.request.finance.UpdatePlatformFinanceSettingsRequest;
import net.modtale.model.user.User;
import net.modtale.service.finance.EarningsAccountService;
import net.modtale.service.finance.AdCampaignService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance")
public class RevenueAdminController {

    @Autowired private EarningsAccountService financeAccountService;
    @Autowired private AdCampaignService financeAdsService;
    @Autowired private AccountService accountService;
    @Autowired private AccessControlService accessControlService;

    @GetMapping("/admin/overview")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> getAdminOverview(@RequestParam(defaultValue = "30d") String range) {
        User user = accountService.getCurrentUser();
        if (user == null || !accessControlService.isAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(financeAccountService.getAdminOverview(range));
    }

    @PutMapping("/admin/settings")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> updateAdminSettings(@RequestBody UpdatePlatformFinanceSettingsRequest request) {
        User user = accountService.getCurrentUser();
        if (user == null || !accessControlService.isAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(financeAccountService.updatePlatformSettings(request));
    }

    @GetMapping("/admin/ads/campaigns")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> getAdCampaigns() {
        User user = accountService.getCurrentUser();
        if (!isSuperAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(financeAdsService.getAdCampaigns());
    }

    @PostMapping("/admin/ads/campaigns")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<?> createAdCampaign(@RequestBody Map<String, Object> payload) {
        User user = accountService.getCurrentUser();
        if (!isSuperAdmin(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            return ResponseEntity.ok(financeAdsService.createAdCampaign(payload));
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
            return ResponseEntity.ok(financeAdsService.updateAdCampaign(campaignId, payload));
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
            return ResponseEntity.ok(financeAdsService.setCampaignActiveState(campaignId, true));
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
            return ResponseEntity.ok(financeAdsService.setCampaignActiveState(campaignId, false));
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
        return ResponseEntity.ok(financeAdsService.getTestAdSlotForProject(projectId, placement));
    }

    private boolean isSuperAdmin(User user) {
        return accessControlService.isSuperAdmin(user);
    }
}
