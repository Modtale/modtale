package net.modtale.repository.user;

import net.modtale.model.user.ApiKey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {
    List<ApiKey> findByUserId(String userId);
    Optional<ApiKey> findByPrefix(String prefix);

    void deleteByUserId(String userId);

    @Query("{ 'userId': ?0, 'contextPermissions.?1': { $exists: true } }")
    List<ApiKey> findByUserIdAndContext(String userId, String contextId);
}