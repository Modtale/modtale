package net.modtale.controller.ad;

import net.modtale.model.ad.AffiliateAd;
import net.modtale.model.user.User;
import net.modtale.service.ad.AdService;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AdController {

    @Autowired
    private AdService adService;

    @Autowired
    private UserService userService;

    private static final String SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    private boolean isSuperAdmin(User user) {
        return user != null && SUPER_ADMIN_ID.equals(user.getId());
    }

    private boolean isAdmin(User user) {
        if (user == null) return false;
        if (SUPER_ADMIN_ID.equals(user.getId())) return true;
        return user.getRoles() != null && user.getRoles().contains("ADMIN");
    }

    private User getSafeUser() {
        try {
            return userService.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

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
    public ResponseEntity<List<AffiliateAd>> getAllAds() {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        return ResponseEntity.ok(adService.getAllAds());
    }

    @PostMapping(value = "/admin/ads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AffiliateAd> createAd(
            @RequestPart("ad") AffiliateAd ad,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        return ResponseEntity.ok(adService.createAd(ad, image));
    }

    @PutMapping(value = "/admin/ads/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AffiliateAd> updateAd(
            @PathVariable String id,
            @RequestPart("ad") AffiliateAd ad,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        return ResponseEntity.ok(adService.updateAd(id, ad, image));
    }

    @DeleteMapping("/admin/ads/{id}")
    public ResponseEntity<Void> deleteAd(@PathVariable String id) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        adService.deleteAd(id);
        return ResponseEntity.ok().build();
    }
}