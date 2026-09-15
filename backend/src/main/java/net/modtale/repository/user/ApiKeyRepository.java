package net.modtale.repository.user;

import java.util.List;
import java.util.Optional;
import net.modtale.model.user.ApiKey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {
    List<ApiKey> findByUserId(String userId);
    Optional<ApiKey> findByPrefix(String prefix);

    void deleteByUserId(String userId);

    @Query("{ 'userId': ?0, 'contextPermissions.?1': { $exists: true } }")
    List<ApiKey> findByUserIdAndContext(String userId, String contextId);
    @Query("{ '_id': ?0, 'userId': ?1, 'keyHash': ?2 }")
    @org.springframework.data.mongodb.repository.Update("{ '$max': { 'lastUsed': ?3 } }")
    long recordUse(String id, String userId, String keyHash, java.time.LocalDateTime usedAt);

    @Query("{ '_id': ?0, 'userId': ?1, 'keyHash': ?2, 'contextPermissions': ?3 }")
    @org.springframework.data.mongodb.repository.Update("{ '$set': { 'contextPermissions': ?4 } }")
    long restrictContexts(String id, String userId, String keyHash,
            java.util.Map<String, java.util.Set<ApiKey.ApiPermission>> expected,
            java.util.Map<String, java.util.Set<ApiKey.ApiPermission>> replacement);

}
