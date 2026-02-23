package net.modtale.repository.jam;

import net.modtale.model.jam.Modjam;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModjamRepository extends MongoRepository<Modjam, String> {
    Optional<Modjam> findBySlug(String slug);
    List<Modjam> findByStatusIn(List<String> statuses);
    List<Modjam> findByHostId(String hostId);
    List<Modjam> findByStatusAndUpdatedAtBefore(String status, LocalDateTime date);
}