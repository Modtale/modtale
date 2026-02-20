package net.modtale.repository.resources;

import net.modtale.model.resources.Modjam;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModjamRepository extends MongoRepository<Modjam, String> {
    Optional<Modjam> findBySlug(String slug);
    List<Modjam> findByStatusIn(List<String> statuses);
}