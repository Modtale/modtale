package net.modtale.repository.finance;

import net.modtale.model.finance.AdCampaign;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AdCampaignRepository extends MongoRepository<AdCampaign, String> {
    List<AdCampaign> findByActiveTrue();
    List<AdCampaign> findByActiveTrueAndTestCampaignFalse();
    List<AdCampaign> findByActiveTrueAndTestCampaignTrue();
}
