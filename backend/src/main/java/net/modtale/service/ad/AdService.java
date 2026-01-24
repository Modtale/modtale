package net.modtale.service.ad;

import net.modtale.model.ad.AffiliateAd;
import net.modtale.repository.ad.AffiliateAdRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class AdService {

    @Autowired
    private AffiliateAdRepository adRepository;

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
        return ad;
    }

    public void trackClick(String id) {
        adRepository.findById(id).ifPresent(ad -> {
            ad.setClicks(ad.getClicks() + 1);
            adRepository.save(ad);
        });
    }

    public AffiliateAd createAd(AffiliateAd ad) {
        return adRepository.save(ad);
    }
}