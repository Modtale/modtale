package net.modtale.service.communication;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ProjectNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectNotificationService.class);

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final ProjectService projectService;
    private final Executor taskExecutor;

    public ProjectNotificationService(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            NotificationDeliveryService notificationDeliveryService,
            ProjectService projectService,
            @Qualifier("taskExecutor") Executor taskExecutor
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.projectService = projectService;
        this.taskExecutor = taskExecutor;
    }

    public void notifyUpdates(Project project, String versionNumber) {
        taskExecutor.execute(() -> {
            try {
                List<User> fans = userRepository.findByLikedModIdsContaining(project.getId());
                List<String> usersToNotify = fans.stream()
                        .filter(u -> u.getNotificationPreferences().getProjectUpdates() == User.NotificationLevel.ON)
                        .map(User::getId)
                        .toList();

                if (!usersToNotify.isEmpty()) {
                    notificationDeliveryService.sendNotifcation(
                            usersToNotify,
                            "Update: " + project.getTitle(),
                            "Version " + versionNumber + " is now available.",
                            URI.create(projectService.getProjectLink(project)),
                            project.getImageUrl()
                    );
                }
            } catch (Exception e) {
                logger.error("Failed to send notifications", e);
            }
        });
    }

    public void notifyNewProject(Project project) {
        taskExecutor.execute(() -> {
            try {
                User author = userRepository.findById(project.getAuthorId()).orElse(null);
                if (author == null) return;
                List<User> followers = userRepository.findByFollowingIdsContaining(author.getId());
                List<String> usersToNotify = followers.stream()
                        .filter(u -> u.getNotificationPreferences().getCreatorUploads() == User.NotificationLevel.ON)
                        .map(User::getId)
                        .toList();

                if (!usersToNotify.isEmpty()) {
                    notificationDeliveryService.sendNotifcation(
                            usersToNotify,
                            "New Project from " + project.getAuthor(),
                            project.getTitle() + " has been released.",
                            URI.create(projectService.getProjectLink(project)),
                            project.getImageUrl()
                    );
                }
            } catch (Exception e) {
                logger.error("Failed to send new project notifications", e);
            }
        });
    }

    public void notifyDependents(Project updatedProject, String version) {
        taskExecutor.execute(() -> {
            List<Project> dependents = projectRepository.findByDependency(updatedProject.getId());
            for (Project dependent : dependents) {
                User author = userRepository.findById(dependent.getAuthorId()).orElse(null);
                if (author != null && author.getNotificationPreferences().getDependencyUpdates() != User.NotificationLevel.OFF) {
                    String msg = updatedProject.getTitle() + " (used in " + dependent.getTitle() + ") has been updated to version " + version + ".";
                    notificationDeliveryService.sendNotifcation(
                            List.of(author.getId()),
                            "Dependency Update",
                            msg,
                            URI.create(projectService.getProjectLink(updatedProject)),
                            updatedProject.getImageUrl()
                    );
                }
            }
        });
    }
}
