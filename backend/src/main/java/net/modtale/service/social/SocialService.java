package net.modtale.service.social;

import net.modtale.model.project.Comment;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
import net.modtale.util.MongoIdUtils;
import net.modtale.service.project.ProjectService;
import net.modtale.service.security.SanitizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SocialService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectService projectService;
    @Autowired private NotificationService notificationService;
    @Autowired private SanitizationService sanitizer;
    @Autowired private MongoTemplate mongoTemplate;

    public void toggleFavorite(String projectId, String userId) {
        Project project = projectService.getRawProjectById(projectId);
        if (project == null) return;

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            List<String> likes = user.getLikedModIds();
            if (likes == null) { likes = new ArrayList<>(); user.setLikedModIds(likes); }

            String canonicalProjectId = project.getId();
            if (likes.contains(canonicalProjectId)) {
                likes.remove(canonicalProjectId);
                project.setFavoriteCount(Math.max(0, project.getFavoriteCount() - 1));
            } else {
                likes.add(canonicalProjectId);
                project.setFavoriteCount(project.getFavoriteCount() + 1);
            }

            userRepository.save(user);
            projectRepository.save(project);
            projectService.evictProjectCache(project);
        }
    }

    public void addComment(String projectId, String userId, String content) {
        Project project = projectService.getRawProjectById(projectId);
        if (project == null) return;
        if (!project.isAllowComments()) throw new IllegalStateException("Comments disabled.");

        User user = userRepository.findById(userId).orElseThrow();
        if (project.getComments() == null) project.setComments(new ArrayList<>());
        project.getComments().add(0, new Comment(user.getId(), sanitizer.sanitizePlainText(content)));
        projectRepository.save(project);
        projectService.evictProjectCache(project);

        if (project.getAuthorId() != null && !project.getAuthorId().equals(userId)) {
            User author = userRepository.findById(project.getAuthorId()).orElse(null);
            if (author != null && author.getNotificationPreferences().getNewComments() != User.NotificationLevel.OFF) {
                notificationService.sendNotifcation(List.of(author.getId()), "New Comment", user.getUsername() + " commented on " + project.getTitle(), URI.create(projectService.getProjectLink(project)), project.getImageUrl());
            }
        }
    }

    public void editComment(String projectId, String commentId, String userId, String newContent) {
        Project project = projectService.getRawProjectById(projectId);
        if (project != null) {
            Comment comment = project.getComments().stream().filter(c -> c.getId().equals(commentId)).findFirst().orElseThrow();
            if (comment.getUserId() == null || !comment.getUserId().equals(userId)) throw new SecurityException("Not your comment.");
            comment.setContent(sanitizer.sanitizePlainText(newContent));
            comment.setUpdatedAt(LocalDateTime.now().toString());
            projectRepository.save(project);
            projectService.evictProjectCache(project);
        }
    }

    public void voteComment(String projectId, String commentId, String userId, boolean upvote) {
        Project project = projectService.getRawProjectById(projectId);
        if (project == null || project.getComments() == null) return;
        Comment comment = project.getComments().stream().filter(c -> c.getId().equals(commentId)).findFirst().orElseThrow();

        if (upvote) {
            if (comment.getUpvotes().contains(userId)) comment.getUpvotes().remove(userId);
            else { comment.getUpvotes().add(userId); comment.getDownvotes().remove(userId); }
        } else {
            if (comment.getDownvotes().contains(userId)) comment.getDownvotes().remove(userId);
            else { comment.getDownvotes().add(userId); comment.getUpvotes().remove(userId); }
        }
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void followUser(String currentUserId, String targetId) {
        User target = userRepository.findById(targetId).orElseThrow(() -> new IllegalArgumentException("Target not found"));
        if (target.getId().equals(currentUserId)) throw new IllegalArgumentException("Cannot follow yourself.");
        if (target.isDeleted()) throw new IllegalArgumentException("Target user not found.");

        User currentUser = userRepository.findById(currentUserId).orElseThrow();

        if (currentUser.getFollowingIds() == null) currentUser.setFollowingIds(new ArrayList<>());
        if (target.getFollowerIds() == null) target.setFollowerIds(new ArrayList<>());

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
                        URI.create("/creator/" + currentUser.getId()),
                        currentUser.getAvatarUrl()
                );
            }
        }
    }

    public void unfollowUser(String currentUserId, String targetId) {
        User target = userRepository.findById(targetId).orElseThrow(() -> new IllegalArgumentException("Target not found"));
        User currentUser = userRepository.findById(currentUserId).orElseThrow();

        if (currentUser.getFollowingIds() != null) {
            currentUser.getFollowingIds().remove(target.getId());
            userRepository.save(currentUser);
        }
        if (target.getFollowerIds() != null) {
            target.getFollowerIds().remove(currentUser.getId());
            userRepository.save(target);
        }
    }

    public List<User> getFollowing(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || userOpt.get().isDeleted()) return new ArrayList<>();

        List<String> ids = userOpt.get().getFollowingIds();
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        Query query = new Query(Criteria.where("_id").in(MongoIdUtils.expandIds(ids)).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "roles", "tier", "id");
        return mongoTemplate.find(query, User.class);
    }

    public List<User> getFollowers(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || userOpt.get().isDeleted()) return new ArrayList<>();

        List<String> ids = userOpt.get().getFollowerIds();
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        Query query = new Query(Criteria.where("_id").in(MongoIdUtils.expandIds(ids)).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "roles", "tier", "id");
        return mongoTemplate.find(query, User.class);
    }
}
