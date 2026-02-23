package net.modtale.repository.admin;

import net.modtale.model.admin.BannedEmail;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface BannedEmailRepository extends MongoRepository<BannedEmail, String> {
    Optional<BannedEmail> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}