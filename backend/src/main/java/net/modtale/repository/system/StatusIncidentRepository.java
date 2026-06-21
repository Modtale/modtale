package net.modtale.repository.system;

import java.util.List;
import net.modtale.model.system.StatusIncident;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StatusIncidentRepository extends MongoRepository<StatusIncident, String> {
    List<StatusIncident> findTop50ByOrderByCreatedAtDesc();
}
