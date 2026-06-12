package net.modtale.service.communication;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import net.modtale.model.project.Project;
import net.modtale.model.user.Notification;
import net.modtale.model.user.NotificationType;
import net.modtale.model.user.User;
import net.modtale.repository.user.NotificationRepository;
import net.modtale.repository.user.UserRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final MongoTemplate mongoTemplate;
    private final NotificationDeliveryService notificationDeliveryService;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            NotificationDeliveryService notificationDeliveryService
    ) {
        this.notificationRepository = notificationRepository;
        this.mongoTemplate = mongoTemplate;
        this.notificationDeliveryService = notificationDeliveryService;
    }

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
        for (Notification n : all) {
            voidAction(n);
        }
        notificationRepository.deleteByUserId(userId);
    }

    public void deleteInviteNotification(String userId, String link) {
        notificationRepository.deleteByUserIdAndLink(userId, link);
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

    public void sendNotifcation(List<String> targetIds, String title, String message, URI link, String iconUrl, NotificationType type, Map<String, String> metadata) {
        notificationDeliveryService.sendNotifcation(targetIds, title, message, link, iconUrl, type, metadata);
    }

    public void sendNotifcation(List<String> userIds, String title, String message, URI link, String iconUrl) {
        notificationDeliveryService.sendNotifcation(userIds, title, message, link, iconUrl);
    }

    private void voidAction(Notification n) {
        try {
            if (n.getType() == NotificationType.TRANSFER_REQUEST) {
                String projectId = n.getMetadata().get("projectId");
                if (projectId != null) {
                    mongoTemplate.updateFirst(
                            new Query(Criteria.where("_id").is(projectId).and("pendingTransferTo").exists(true)),
                            new Update().unset("pendingTransferTo"),
                            Project.class
                    );
                }
            } else if (n.getType() == NotificationType.ORG_INVITE) {
                String orgId = n.getMetadata().get("orgId");
                if (orgId != null) {
                    mongoTemplate.updateFirst(
                            new Query(Criteria.where("_id").is(orgId)),
                            new Update().pull("pendingOrgInvites", new Document("userId", n.getUserId())),
                            User.class
                    );
                }
            } else if (n.getType() == NotificationType.CONTRIBUTOR_INVITE) {
                String projectId = n.getMetadata().get("projectId");
                if (projectId != null) {
                    mongoTemplate.updateFirst(
                            new Query(Criteria.where("_id").is(projectId)),
                            new Update().pull("teamInvites", new Document("userId", n.getUserId())),
                            Project.class
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Failed to void action for notification " + n.getId(), e);
        }
    }
}
