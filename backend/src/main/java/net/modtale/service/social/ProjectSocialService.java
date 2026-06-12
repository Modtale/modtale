package net.modtale.service.social;

import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Comment;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.security.SanitizationService;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

final class ProjectSocialService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final NotificationService notificationService;
    private final SanitizationService sanitizer;
    private final ScoringService scoringService;

    ProjectSocialService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            ProjectService projectService,
            NotificationService notificationService,
            SanitizationService sanitizer,
            ScoringService scoringService
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectService = projectService;
        this.notificationService = notificationService;
        this.sanitizer = sanitizer;
        this.scoringService = scoringService;
    }

    void toggleFavorite(String projectId, String userId) {
        Project project = getProject(projectId);
        User user = getUser(userId);

        List<String> likes = user.getLikedModIds();
        if (likes == null) {
            likes = new ArrayList<>();
            user.setLikedModIds(likes);
        }

        String canonicalProjectId = project.getId();
        if (likes.contains(canonicalProjectId)) {
            likes.remove(canonicalProjectId);
            project.setFavoriteCount(Math.max(0, project.getFavoriteCount() - 1));
        } else {
            likes.add(canonicalProjectId);
            project.setFavoriteCount(project.getFavoriteCount() + 1);
        }
        scoringService.markProjectRankingDirty(project);

        userRepository.save(user);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    void addComment(String projectId, String userId, String content) {
        Project project = getProject(projectId);
        if (!project.isAllowComments()) {
            throw new ForbiddenOperationException("Comments are disabled for this project.");
        }

        User user = getUser(userId);
        if (project.getComments() == null) {
            project.setComments(new ArrayList<>());
        }
        project.getComments().add(0, new Comment(user.getId(), sanitizer.sanitizePlainText(content)));
        projectRepository.save(project);
        projectService.evictProjectCache(project);

        if (project.getAuthorId() != null && !project.getAuthorId().equals(userId)) {
            User author = userRepository.findById(project.getAuthorId()).orElse(null);
            if (author != null && author.getNotificationPreferences().getNewComments() != User.NotificationLevel.OFF) {
                notificationService.sendNotifcation(
                        List.of(author.getId()),
                        "New Comment",
                        user.getUsername() + " commented on " + project.getTitle(),
                        URI.create(projectService.getProjectLink(project)),
                        project.getImageUrl()
                );
            }
        }
    }

    void editComment(String projectId, String commentId, String userId, String newContent) {
        Project project = getProject(projectId);

        Comment comment = project.getComments().stream()
                .filter(candidate -> candidate.getId().equals(commentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found."));
        if (comment.getUserId() == null || !comment.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("You can only edit your own comments.");
        }

        comment.setContent(sanitizer.sanitizePlainText(newContent));
        comment.setUpdatedAt(LocalDateTime.now().toString());
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    void voteComment(String projectId, String commentId, String userId, boolean upvote) {
        Project project = getProject(projectId);
        if (project.getComments() == null) {
            throw new ResourceNotFoundException("Project not found.");
        }

        Comment comment = project.getComments().stream()
                .filter(candidate -> candidate.getId().equals(commentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found."));

        if (upvote) {
            if (comment.getUpvotes().contains(userId)) {
                comment.getUpvotes().remove(userId);
            } else {
                comment.getUpvotes().add(userId);
                comment.getDownvotes().remove(userId);
            }
        } else if (comment.getDownvotes().contains(userId)) {
            comment.getDownvotes().remove(userId);
        } else {
            comment.getDownvotes().add(userId);
            comment.getUpvotes().remove(userId);
        }

        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    private Project getProject(String projectId) {
        Project project = projectService.getRawProjectById(projectId);
        if (project == null) {
            throw new ResourceNotFoundException("Project not found.");
        }
        return project;
    }

    private User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}
