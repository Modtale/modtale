package net.modtale.service.user.account;

import java.time.LocalDateTime;
import java.util.List;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.Notification;
import net.modtale.model.user.User;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class AccountLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(AccountLifecycleService.class);

    private final UserRepository userRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final MongoTemplate mongoTemplate;
    private final TrackingService trackingService;

    public AccountLifecycleService(
            UserRepository userRepository,
            ApiKeyRepository apiKeyRepository,
            MongoTemplate mongoTemplate,
            TrackingService trackingService
    ) {
        this.userRepository = userRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.mongoTemplate = mongoTemplate;
        this.trackingService = trackingService;
    }

    public void deleteUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.setDeletedAt(LocalDateTime.now());

        apiKeyRepository.deleteByUserId(userId);
        user.setGithubAccessToken(null);
        user.setGitlabAccessToken(null);
        user.setGitlabRefreshToken(null);

        userRepository.save(user);

        if (user.getAccountType() == User.AccountType.ORGANIZATION) {
            trackingService.logDeletedOrg(user.getId());
        } else {
            trackingService.logDeletedUser(user.getId());
        }

        logger.info("Soft deleted user account: {} ({})", user.getUsername(), userId);
    }

    public void recoverUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (!user.isDeleted()) {
            return;
        }

        user.setDeletedAt(null);
        userRepository.save(user);

        if (user.getAccountType() == User.AccountType.ORGANIZATION) {
            trackingService.logNewOrg(user.getId());
        } else {
            trackingService.logNewUser(user.getId());
        }

        logger.info("Recovered user account: {} ({})", user.getUsername(), userId);
    }

    public void cleanupDeletedUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<User> expiredUsers = userRepository.findByDeletedAtBefore(cutoff);

        for (User user : expiredUsers) {
            try {
                performHardDelete(user);
            } catch (Exception e) {
                logger.error("Failed to hard delete user {}", user.getId(), e);
            }
        }
    }

    private void performHardDelete(User user) {
        mongoTemplate.remove(new Query(Criteria.where("userId").is(user.getId())), Notification.class);

        mongoTemplate.updateMulti(
                new Query(Criteria.where("followerIds").is(user.getId())),
                new Update().pull("followerIds", user.getId()),
                User.class
        );
        mongoTemplate.updateMulti(
                new Query(Criteria.where("followingIds").is(user.getId())),
                new Update().pull("followingIds", user.getId()),
                User.class
        );

        userRepository.deleteById(user.getId());
        logger.info("Permanently deleted user account: {} ({})", user.getUsername(), user.getId());
    }
}
