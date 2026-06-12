package net.modtale.service.social;

import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
import net.modtale.util.MongoIdUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class UserFollowService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;

    UserFollowService(
            UserRepository userRepository,
            NotificationService notificationService,
            MongoTemplate mongoTemplate
    ) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.mongoTemplate = mongoTemplate;
    }

    void followUser(String currentUserId, String targetId) {
        User target = getActiveUser(targetId,
                "We couldn't find the user you were trying to follow.");
        if (target.getId().equals(currentUserId)) {
            throw new ForbiddenOperationException("You cannot follow your own account.");
        }

        User currentUser = getActiveUser(currentUserId,
                "We couldn't find your account.");

        if (currentUser.getFollowingIds() == null) {
            currentUser.setFollowingIds(new ArrayList<>());
        }
        if (target.getFollowerIds() == null) {
            target.setFollowerIds(new ArrayList<>());
        }

        if (!currentUser.getFollowingIds().contains(target.getId())) {
            currentUser.getFollowingIds().add(target.getId());
            target.getFollowerIds().add(currentUser.getId());

            userRepository.save(currentUser);
            userRepository.save(target);

            if (target.getNotificationPreferences().getNewFollowers() != User.NotificationLevel.OFF) {
                notificationService.sendNotifcation(
                        List.of(target.getId()),
                        "New Follower",
                        currentUser.getUsername() + " started following you.",
                        URI.create("/creator/" + (currentUser.getUsername() != null && !currentUser.getUsername().isBlank()
                                ? currentUser.getUsername()
                                : currentUser.getId())),
                        currentUser.getAvatarUrl()
                );
            }
        }
    }

    void unfollowUser(String currentUserId, String targetId) {
        User target = getActiveUser(targetId,
                "We couldn't find the user you were trying to unfollow.");
        User currentUser = getActiveUser(currentUserId,
                "We couldn't find your account.");

        if (currentUser.getFollowingIds() != null) {
            currentUser.getFollowingIds().remove(target.getId());
            userRepository.save(currentUser);
        }
        if (target.getFollowerIds() != null) {
            target.getFollowerIds().remove(currentUser.getId());
            userRepository.save(target);
        }
    }

    List<User> getFollowing(String userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || user.get().isDeleted()) {
            return new ArrayList<>();
        }

        List<String> ids = user.get().getFollowingIds();
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return findUsersByIds(ids);
    }

    List<User> getFollowers(String userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || user.get().isDeleted()) {
            return new ArrayList<>();
        }

        List<String> ids = user.get().getFollowerIds();
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return findUsersByIds(ids);
    }

    private User getActiveUser(String userId, String failureMessage) {
        return userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(failureMessage));
    }

    private List<User> findUsersByIds(List<String> ids) {
        Query query = new Query(Criteria.where("_id").in(MongoIdUtils.expandIds(ids)).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "roles", "tier", "id");
        return mongoTemplate.find(query, User.class);
    }
}
