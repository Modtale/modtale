package net.modtale.controller.ad;

import net.modtale.model.ad.AffiliateAd;
import net.modtale.service.ad.AdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ads")
public class AdController {

    @Autowired
    private AdService adService;

    @GetMapping("/serve")
    public ResponseEntity<AffiliateAd> getAd() {
        AffiliateAd ad = adService.getRandomAd();
        if (ad == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(ad);
    }

    @PostMapping("/{id}/click")
    public ResponseEntity<Void> trackClick(@PathVariable String id) {
        adService.trackClick(id);
        return ResponseEntity.ok().build();
    }
}