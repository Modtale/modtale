package net.modtale.repository.user;

import net.modtale.model.user.ApiKey;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {
    List<ApiKey> findByUserId(String userId);
    Optional<ApiKey> findByPrefix(String prefix);

    void deleteByUserId(String userId);
}