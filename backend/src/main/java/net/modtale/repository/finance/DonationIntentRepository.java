package net.modtale.repository.finance;

import net.modtale.model.finance.DonationIntent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DonationIntentRepository extends MongoRepository<DonationIntent, String> {
    Optional<DonationIntent> findByStripeSessionId(String stripeSessionId);
    List<DonationIntent> findByStatusAndExpiresAtBefore(DonationIntent.DonationStatus status, LocalDateTime cutoff);
}
