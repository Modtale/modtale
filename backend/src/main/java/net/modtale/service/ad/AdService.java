package net.modtale.service.ad;

import net.modtale.model.ad.AffiliateAd;
import net.modtale.repository.ad.AffiliateAdRepository;
import net.modtale.service.resources.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Random;

@Service
public class AdService {

    private static final Logger logger = LoggerFactory.getLogger(AdService.class);

    @Autowired
    private AffiliateAdRepository adRepository;

    @Autowired
    private StorageService storageService;

    private final Random random = new Random();

    public AffiliateAd getRandomAd() {
        if (random.nextDouble() > 0.5) {
            return null;
        }

        List<AffiliateAd> ads = adRepository.findAllActive();
        if (ads.isEmpty()) {
            return null;
        }
        AffiliateAd ad = ads.get(random.nextInt(ads.size()));
        ad.setViews(ad.getViews() + 1);
        adRepository.save(ad);

        // Ensure we return the full public URL
        if (ad.getImageUrl() != null && !ad.getImageUrl().startsWith("http")) {
            ad.setImageUrl(storageService.getPublicUrl(ad.getImageUrl()));
        }

        return ad;
    }

    public void trackClick(String id) {
        adRepository.findById(id).ifPresent(ad -> {
            ad.setClicks(ad.getClicks() + 1);
            adRepository.save(ad);
        });
    }

    public AffiliateAd createAd(AffiliateAd ad, MultipartFile image) throws IOException {
        if (image != null && !image.isEmpty()) {
            String path = storageService.upload(image, "ads");
            ad.setImageUrl(path);
        }
        return adRepository.save(ad);
    }

    public List<AffiliateAd> getAllAds() {
        List<AffiliateAd> ads = adRepository.findAll();
        // Resolve URLs for admin display
        ads.forEach(ad -> {
            if (ad.getImageUrl() != null && !ad.getImageUrl().startsWith("http")) {
                ad.setImageUrl(storageService.getPublicUrl(ad.getImageUrl()));
            }
        });
        return ads;
    }

    public AffiliateAd updateAd(String id, AffiliateAd updated, MultipartFile image) throws IOException {
        return adRepository.findById(id).map(ad -> {
            ad.setTitle(updated.getTitle());
            ad.setLinkUrl(updated.getLinkUrl());
            ad.setActive(updated.isActive());

            if (image != null && !image.isEmpty()) {
                // Delete old image if it exists and isn't external
                if (ad.getImageUrl() != null && !ad.getImageUrl().startsWith("http")) {
                    storageService.deleteFile(ad.getImageUrl());
                }
                try {
                    String path = storageService.upload(image, "ads");
                    ad.setImageUrl(path);
                } catch (IOException e) {
                    logger.error("Failed to upload ad image", e);
                }
            } else if (updated.getImageUrl() != null) {
                if (!updated.getImageUrl().equals(storageService.getPublicUrl(ad.getImageUrl()))) {
                    ad.setImageUrl(updated.getImageUrl());
                }
            }

            return adRepository.save(ad);
        }).orElse(null);
    }

    public void deleteAd(String id) {
        adRepository.findById(id).ifPresent(ad -> {
            if (ad.getImageUrl() != null && !ad.getImageUrl().startsWith("http")) {
                storageService.deleteFile(ad.getImageUrl());
            }
            adRepository.deleteById(id);
        });
    }
}