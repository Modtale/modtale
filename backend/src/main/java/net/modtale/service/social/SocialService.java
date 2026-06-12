package net.modtale.service.social;

import java.util.List;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.validation.SanitizationService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class SocialService {

    private final ProjectSocialService projectSocialService;
    private final UserFollowService userFollowService;

    public SocialService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            ProjectService projectService,
            NotificationService notificationService,
            SanitizationService sanitizer,
            MongoTemplate mongoTemplate,
            ScoringService scoringService
    ) {
        this.projectSocialService = new ProjectSocialService(
                projectRepository,
                userRepository,
                projectService,
                notificationService,
                sanitizer,
                scoringService
        );
        this.userFollowService = new UserFollowService(userRepository, notificationService, mongoTemplate);
    }

    public void toggleFavorite(String projectId, String userId) {
        projectSocialService.toggleFavorite(projectId, userId);
    }

    public void addComment(String projectId, String userId, String content) {
        projectSocialService.addComment(projectId, userId, content);
    }

    public void editComment(String projectId, String commentId, String userId, String newContent) {
        projectSocialService.editComment(projectId, commentId, userId, newContent);
    }

    public void voteComment(String projectId, String commentId, String userId, boolean upvote) {
        projectSocialService.voteComment(projectId, commentId, userId, upvote);
    }

    public void followUser(String currentUserId, String targetId) {
        userFollowService.followUser(currentUserId, targetId);
    }

    public void unfollowUser(String currentUserId, String targetId) {
        userFollowService.unfollowUser(currentUserId, targetId);
    }

    public List<User> getFollowing(String userId) {
        return userFollowService.getFollowing(userId);
    }

    public List<User> getFollowers(String userId) {
        return userFollowService.getFollowers(userId);
    }
}
