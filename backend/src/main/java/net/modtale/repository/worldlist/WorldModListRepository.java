package net.modtale.repository.worldlist;

import java.time.Instant;
import net.modtale.model.worldlist.WorldModList;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WorldModListRepository extends MongoRepository<WorldModList, String> {
    void deleteByExpiresAtBefore(Instant cutoff);
}
