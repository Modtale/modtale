package net.modtale.repository.user;

import net.modtale.model.user.User;
import net.modtale.model.user.OAuthProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByVerificationToken(String token);

    Optional<User> findByPasswordResetToken(String token);

    List<User> findByDeletedAtBefore(LocalDateTime dateTime);

    @Query("{ 'connectedAccounts': { '$elemMatch': { 'provider': ?0, 'providerId': ?1 } } }")
    Optional<User> findByConnectedAccountsProviderAndProviderId(OAuthProvider provider, String providerId);

    List<User> findByLikedModIdsContaining(String modId);
    List<User> findByFollowingIdsContaining(String userId);

    @Query("{ 'organizationMembers.userId': ?0 }")
    List<User> findOrganizationsByMemberId(String userId);

    List<User> findByUsernameIn(Collection<String> usernames);

    List<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
}
