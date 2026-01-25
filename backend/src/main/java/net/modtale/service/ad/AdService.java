package net.modtale.service.ad;

import net.modtale.model.ad.AdCreative;
import net.modtale.model.ad.AffiliateAd;
import net.modtale.repository.ad.AffiliateAdRepository;
import net.modtale.service.resources.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class AdService {

    private static final Logger logger = LoggerFactory.getLogger(AdService.class);

    @Autowired
    private AffiliateAdRepository adRepository;

    @Autowired
    private StorageService storageService;

    private final Random random = new Random();

    public AffiliateAd getRandomAd(String placement) {
        if (random.nextDouble() > 0.5) {
            return null;
        }

        AdCreative.CreativeType targetType = parsePlacement(placement);

        List<AffiliateAd> allActive = adRepository.findAllActive();

        List<AffiliateAd> candidates = allActive.stream()
                .filter(ad -> ad.getCreatives().stream().anyMatch(c -> c.getType() == targetType))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            if (targetType == AdCreative.CreativeType.SIDEBAR) {
                candidates = allActive.stream()
                        .filter(ad -> ad.getCreatives().stream().anyMatch(c -> c.getType() == AdCreative.CreativeType.CARD))
                        .collect(Collectors.toList());
            }

            if (candidates.isEmpty()) return null;
        }

        AffiliateAd selectedAd = candidates.get(random.nextInt(candidates.size()));

        AdCreative bestCreative = selectedAd.getCreatives().stream()
                .filter(c -> c.getType() == targetType)
                .findFirst()
                .orElse(selectedAd.getCreatives().stream()
                        .filter(c -> c.getType() == AdCreative.CreativeType.CARD)
                        .findFirst()
                        .orElse(selectedAd.getCreatives().get(0)));

        AffiliateAd response = new AffiliateAd();
        response.setId(selectedAd.getId());
        response.setTitle(selectedAd.getTitle());
        response.setLinkUrl(selectedAd.getLinkUrl());

        String publicUrl = bestCreative.getImageUrl();
        if (publicUrl != null && !publicUrl.startsWith("http")) {
            publicUrl = storageService.getPublicUrl(publicUrl);
        }
        bestCreative.setImageUrl(publicUrl);

        response.setCreatives(List.of(bestCreative));

        selectedAd.setViews(selectedAd.getViews() + 1);
        adRepository.save(selectedAd);

        return response;
    }

    private AdCreative.CreativeType parsePlacement(String placement) {
        if (placement == null) return AdCreative.CreativeType.CARD;
        switch (placement.toLowerCase()) {
            case "banner": return AdCreative.CreativeType.BANNER;
            case "sidebar": return AdCreative.CreativeType.SIDEBAR;
            default: return AdCreative.CreativeType.CARD;
        }
    }

    public void trackClick(String id) {
        adRepository.findById(id).ifPresent(ad -> {
            ad.setClicks(ad.getClicks() + 1);
            adRepository.save(ad);
        });
    }

    public AffiliateAd createAd(AffiliateAd ad, List<MultipartFile> images) throws IOException {
        processImages(ad, images);
        return adRepository.save(ad);
    }

    public List<AffiliateAd> getAllAds() {
        List<AffiliateAd> ads = adRepository.findAll();
        ads.forEach(ad -> {
            if (ad.getCreatives() != null) {
                ad.getCreatives().forEach(c -> {
                    if (c.getImageUrl() != null && !c.getImageUrl().startsWith("http")) {
                        c.setImageUrl(storageService.getPublicUrl(c.getImageUrl()));
                    }
                });
            }
        });
        return ads;
    }

    public AffiliateAd updateAd(String id, AffiliateAd updated, List<MultipartFile> newImages, List<String> deleteCreativeIds) throws IOException {
        return adRepository.findById(id).map(ad -> {
            ad.setTitle(updated.getTitle());
            ad.setLinkUrl(updated.getLinkUrl());
            ad.setActive(updated.isActive());

            if (deleteCreativeIds != null && !deleteCreativeIds.isEmpty() && ad.getCreatives() != null) {
                List<AdCreative> toKeep = new ArrayList<>();
                for (AdCreative c : ad.getCreatives()) {
                    if (deleteCreativeIds.contains(c.getId())) {
                        if (c.getImageUrl() != null && !c.getImageUrl().startsWith("http")) {
                            storageService.deleteFile(c.getImageUrl());
                        }
                    } else {
                        toKeep.add(c);
                    }
                }
                ad.setCreatives(toKeep);
            }

            try {
                processImages(ad, newImages);
            } catch (IOException e) {
                logger.error("Failed to process new images", e);
            }

            return adRepository.save(ad);
        }).orElse(null);
    }

    public void deleteAd(String id) {
        adRepository.findById(id).ifPresent(ad -> {
            if (ad.getCreatives() != null) {
                for (AdCreative c : ad.getCreatives()) {
                    if (c.getImageUrl() != null && !c.getImageUrl().startsWith("http")) {
                        storageService.deleteFile(c.getImageUrl());
                    }
                }
            }
            adRepository.deleteById(id);
        });
    }

    private void processImages(AffiliateAd ad, List<MultipartFile> images) throws IOException {
        if (images == null || images.isEmpty()) return;

        if (ad.getCreatives() == null) {
            ad.setCreatives(new ArrayList<>());
        }

        for (MultipartFile file : images) {
            if (file.isEmpty()) continue;

            BufferedImage bImg = ImageIO.read(file.getInputStream());
            int width = bImg != null ? bImg.getWidth() : 0;
            int height = bImg != null ? bImg.getHeight() : 0;

            String path = storageService.upload(file, "ads");

            AdCreative creative = new AdCreative(path, width, height);
            ad.getCreatives().add(creative);
        }
    }
}