package net.modtale.repository.finance;

import java.util.List;
import net.modtale.model.finance.CreatorSupportSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CreatorSupportSubscriptionRepository extends MongoRepository<CreatorSupportSubscription, String> {
    List<CreatorSupportSubscription> findByDonorUserId(String donorUserId);
}
