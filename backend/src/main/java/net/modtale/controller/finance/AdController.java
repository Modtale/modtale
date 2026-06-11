package net.modtale.controller.finance;

import jakarta.servlet.http.HttpServletRequest;
import net.modtale.service.finance.AdCampaignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance")
public class AdController {

    @Autowired private AdCampaignService financeAdsService;

    @GetMapping("/ads/slot/{projectId}")
    public ResponseEntity<?> getAdSlot(
            @PathVariable String projectId,
            @RequestParam(required = false) String placement
    ) {
        return ResponseEntity.ok(financeAdsService.getAdSlotForProject(projectId, placement));
    }

    @PostMapping("/ads/impression")
    public ResponseEntity<?> trackAdImpression(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String campaignId = payload.get("campaignId");
        String projectId = payload.get("projectId");
        if (campaignId == null || projectId == null) return ResponseEntity.badRequest().build();

        String ip = getClientIp(request);
        financeAdsService.trackAdImpression(campaignId, projectId, ip);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/ads/click/{campaignId}")
    public ResponseEntity<Void> clickAd(
            @PathVariable String campaignId,
            @RequestParam String projectId,
            HttpServletRequest request
    ) {
        String ip = getClientIp(request);
        String url = financeAdsService.registerAdClickAndResolveUrl(campaignId, projectId, ip);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(url));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return xfHeader == null ? request.getRemoteAddr() : xfHeader.split(",")[0];
    }
}
