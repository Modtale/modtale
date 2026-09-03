package net.modtale.controller.finance;

import net.modtale.model.dto.request.finance.UpdatePlatformFinanceSettingsRequest;
import net.modtale.service.finance.EarningsAccountService;
import net.modtale.service.finance.AdCampaignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance")
public class RevenueAdminController {

    @Autowired private EarningsAccountService financeAccountService;
    @Autowired private AdCampaignService financeAdsService;

    @GetMapping("/admin/overview")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
    public ResponseEntity<?> getAdminOverview(@RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(financeAccountService.getAdminOverview(range));
    }

    @PutMapping("/admin/settings")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
    public ResponseEntity<?> updateAdminSettings(@RequestBody UpdatePlatformFinanceSettingsRequest request) {
        return ResponseEntity.ok(financeAccountService.updatePlatformSettings(request));
    }

    @GetMapping("/admin/ads/campaigns")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
    public ResponseEntity<?> getAdCampaigns() {
        return ResponseEntity.ok(financeAdsService.getAdCampaigns());
    }

    @PostMapping("/admin/ads/campaigns")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
    public ResponseEntity<?> createAdCampaign(@RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(financeAdsService.createAdCampaign(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/admin/ads/campaigns/{campaignId}")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
    public ResponseEntity<?> updateAdCampaign(@PathVariable String campaignId, @RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(financeAdsService.updateAdCampaign(campaignId, payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/admin/ads/campaigns/{campaignId}/start")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
    public ResponseEntity<?> startAdCampaign(@PathVariable String campaignId) {
        try {
            return ResponseEntity.ok(financeAdsService.setCampaignActiveState(campaignId, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/admin/ads/campaigns/{campaignId}/pause")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
    public ResponseEntity<?> pauseAdCampaign(@PathVariable String campaignId) {
        try {
            return ResponseEntity.ok(financeAdsService.setCampaignActiveState(campaignId, false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/admin/ads/test-slot/{projectId}")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PLATFORM_FINANCE_MANAGE', authentication)")
    public ResponseEntity<?> getTestAdSlot(
            @PathVariable String projectId,
            @RequestParam(required = false) String placement
    ) {
        return ResponseEntity.ok(financeAdsService.getTestAdSlotForProject(projectId, placement));
    }
}
