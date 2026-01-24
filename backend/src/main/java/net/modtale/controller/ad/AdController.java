package net.modtale.controller.ad;

import net.modtale.model.ad.AffiliateAd;
import net.modtale.service.ad.AdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AdController {

    @Autowired
    private AdService adService;

    @GetMapping("/ads/serve")
    public ResponseEntity<AffiliateAd> getAd() {
        AffiliateAd ad = adService.getRandomAd();
        if (ad == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(ad);
    }

    @PostMapping("/ads/{id}/click")
    public ResponseEntity<Void> trackClick(@PathVariable String id) {
        adService.trackClick(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/ads")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AffiliateAd>> getAllAds() {
        return ResponseEntity.ok(adService.getAllAds());
    }

    @PostMapping(value = "/admin/ads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AffiliateAd> createAd(
            @RequestPart("ad") AffiliateAd ad,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        return ResponseEntity.ok(adService.createAd(ad, image));
    }

    @PutMapping(value = "/admin/ads/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AffiliateAd> updateAd(
            @PathVariable String id,
            @RequestPart("ad") AffiliateAd ad,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        return ResponseEntity.ok(adService.updateAd(id, ad, image));
    }

    @DeleteMapping("/admin/ads/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAd(@PathVariable String id) {
        adService.deleteAd(id);
        return ResponseEntity.ok().build();
    }
}