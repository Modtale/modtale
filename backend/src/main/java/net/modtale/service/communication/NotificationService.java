package net.modtale.service.communication;

import net.modtale.model.project.Project;
import net.modtale.model.user.Notification;
import net.modtale.model.user.NotificationType;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.NotificationRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ProjectService projectService;

    @Qualifier("taskExecutor")
    @Autowired private Executor taskExecutor;

    public List<Notification> getUserNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void markAsRead(String notificationId, String userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    public void markAsUnread(String notificationId, String userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setRead(false);
                notificationRepository.save(n);
            }
        });
    }

    public void markAllAsRead(String userId) {
        mongoTemplate.updateMulti(
                new Query(Criteria.where("userId").is(userId).and("isRead").is(false)),
                new Update().set("isRead", true),
                Notification.class
        );
    }

    public void deleteNotification(String notificationId, String userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                voidAction(n);
                notificationRepository.delete(n);
            }
        });
    }

    public void clearAll(String userId) {
        List<Notification> all = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for(Notification n : all) {
            voidAction(n);
        }
        notificationRepository.deleteByUserId(userId);
    }

    public void deleteInviteNotification(String userId, String link) {
        notificationRepository.deleteByUserIdAndLink(userId, link);
    }

    private void voidAction(Notification n) {
        try {
            if (n.getType() == NotificationType.TRANSFER_REQUEST) {
                String modId = n.getMetadata().get("modId");
                if (modId != null) {
                    mongoTemplate.updateFirst(
                            new Query(Criteria.where("_id").is(modId).and("pendingTransferTo").exists(true)),
                            new Update().unset("pendingTransferTo"),
                            Project.class
                    );
                }
            } else if (n.getType() == NotificationType.ORG_INVITE) {
                String orgId = n.getMetadata().get("orgId");
                if (orgId != null) {
                    mongoTemplate.updateFirst(
                            new Query(Criteria.where("_id").is(orgId)),
                            new Update().pull("pendingOrgInvites", new Query(Criteria.where("userId").is(n.getUserId()))),
                            User.class
                    );
                }
            } else if (n.getType() == NotificationType.CONTRIBUTOR_INVITE) {
                String modId = n.getMetadata().get("modId");
                if (modId != null) {
                    userRepository.findById(n.getUserId()).ifPresent(u -> {
                        mongoTemplate.updateFirst(
                                new Query(Criteria.where("_id").is(modId)),
                                new Update().pull("pendingInvites", u.getUsername()),
                                Project.class
                        );
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Failed to void action for notification " + n.getId(), e);
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupExpiredRequests() {
        LocalDateTime expirationThreshold = LocalDateTime.now().minusDays(7);
        Query query = new Query(Criteria.where("type").in(
                        NotificationType.TRANSFER_REQUEST,
                        NotificationType.ORG_INVITE,
                        NotificationType.CONTRIBUTOR_INVITE)
                .and("createdAt").lt(expirationThreshold));
        List<Notification> expired = mongoTemplate.find(query, Notification.class);
        if (!expired.isEmpty()) {
            logger.info("Cleaning up {} expired actionable notifications.", expired.size());
            for (Notification n : expired) {
                voidAction(n);
                notificationRepository.delete(n);
            }
        }
    }

    @Async
    public void sendActionableNotification(List<String> targetIds, String title, String message, URI link, String iconUrl, NotificationType type, Map<String, String> metadata) {
        if (targetIds.isEmpty()) return;

        List<Notification> toSave = new ArrayList<>();

        for (String targetId : targetIds) {
            User target = userRepository.findById(targetId).orElse(null);
            if (target == null) continue;

            if (target.getAccountType() == User.AccountType.ORGANIZATION) {
                String orgContextTitle = "[" + target.getUsername() + "] " + title;
                target.getOrganizationMembers().stream()
                        .filter(m -> "ADMIN".equals(m.getRole()))
                        .forEach(admin -> {
                            toSave.add(new Notification(admin.getUserId(), orgContextTitle, message, link, iconUrl, type, metadata));
                        });
            } else {
                toSave.add(new Notification(targetId, title, message, link, iconUrl, type, metadata));
            }
        }

        if (!toSave.isEmpty()) {
            notificationRepository.saveAll(toSave);
        }
    }

    @Async
    public void sendNotification(List<String> userIds, String title, String message, URI link, String iconUrl) {
        sendActionableNotification(userIds, title, message, link, iconUrl, NotificationType.INFO, null);
    }

    public void notifyUpdates(Project project, String versionNumber) {
        taskExecutor.execute(() -> {
            try {
                List<User> fans = userRepository.findByLikedModIdsContaining(project.getId());
                List<String> usersToNotify = fans.stream()
                        .filter(u -> u.getNotificationPreferences().getProjectUpdates() == User.NotificationLevel.ON)
                        .map(User::getId).toList();

                if (!usersToNotify.isEmpty()) {
                    sendNotification(usersToNotify, "Update: " + project.getTitle(), "Version " + versionNumber + " is now available.", URI.create(projectService.getProjectLink(project)), project.getImageUrl());
                }
            } catch (Exception e) { logger.error("Failed to send notifications", e); }
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
                        .map(User::getId).toList();

                if (!usersToNotify.isEmpty()) {
                    sendNotification(usersToNotify, "New Project from " + project.getAuthor(), project.getTitle() + " has been released.", URI.create(projectService.getProjectLink(project)), project.getImageUrl());
                }
            } catch (Exception e) { logger.error("Failed to send new project notifications", e); }
        });
    }

    public void notifyDependents(Project updatedProject, String version) {
        taskExecutor.execute(() -> {
            List<Project> dependents = projectRepository.findByDependency(updatedProject.getId());
            for (Project dependent : dependents) {
                User author = userRepository.findById(dependent.getAuthorId()).orElse(null);
                if (author != null && author.getNotificationPreferences().getDependencyUpdates() != User.NotificationLevel.OFF) {
                    String msg = updatedProject.getTitle() + " (used in " + dependent.getTitle() + ") has been updated to version " + version + ".";
                    sendNotification(List.of(author.getId()), "Dependency Update", msg, URI.create(projectService.getProjectLink(updatedProject)), updatedProject.getImageUrl());
                }
            }
        });
    }
}