package net.modtale.repository.finance;

import java.util.List;
import java.time.Instant;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import net.modtale.model.finance.CreatorSupportSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CreatorSupportSubscriptionRepository extends MongoRepository<CreatorSupportSubscription, String> {
    List<CreatorSupportSubscription> findByDonorUserId(String donorUserId);

    @Query("{'_id': ?0, 'updatedAt': ?1, 'providerAccountId': ?2, 'testMode': ?3, 'customerId': ?4}")
    @Update("{'$set': {'status': ?5, 'cancelAtPeriodEnd': ?6, 'updatedAt': ?7}}")
    long updateProviderState(String id, Instant expectedUpdatedAt, String accountId, boolean testMode, String customerId,
            String status, boolean cancelAtPeriodEnd, Instant updatedAt);
}
