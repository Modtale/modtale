package net.modtale.repository.ad;

import net.modtale.model.ad.AffiliateAd;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface AffiliateAdRepository extends MongoRepository<AffiliateAd, String> {
    @Query("{ 'active': true }")
    List<AffiliateAd> findAllActive();
}