package net.modtale.repository.user;

import net.modtale.model.user.BannedEmail;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface BannedEmailRepository extends MongoRepository<BannedEmail, String> {
    Optional<BannedEmail> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}