package net.modtale.repository.admin;

import java.util.Optional;
import net.modtale.model.admin.BannedEmail;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BannedEmailRepository extends MongoRepository<BannedEmail, String> {
    Optional<BannedEmail> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
