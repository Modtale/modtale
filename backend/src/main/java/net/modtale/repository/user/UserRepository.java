package net.modtale.repository.user;

import net.modtale.model.user.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUsername(String username);

    @Query(value = "{ 'username': { $regex: ?0, $options: 'i' } }")
    Optional<User> findByUsernameIgnoreCase(String username);

    @Query(value = "{ 'username': { $regex: ?0, $options: 'i' } }", exists = true)
    boolean existsByUsernameIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    @Query("{ 'connectedAccounts.providerId': ?0 }")
    Optional<User> findByConnectedAccountsProviderId(String providerId);

    List<User> findByLikedModIdsContaining(String modId);
    List<User> findByFollowingIdsContaining(String userId);

    @Query("{ 'organizationMembers.userId': ?0 }")
    List<User> findOrganizationsByMemberId(String userId);
}