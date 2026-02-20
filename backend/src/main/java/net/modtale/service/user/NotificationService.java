package net.modtale.service.user;

import net.modtale.model.user.Notification;
import net.modtale.model.user.User;
import net.modtale.repository.resources.ModRepository;
import net.modtale.repository.user.NotificationRepository;
import net.modtale.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ModRepository modRepository;
    @Autowired private MongoTemplate mongoTemplate;

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
            if ("TRANSFER_REQUEST".equals(n.getType())) {
                String modId = n.getMetadata().get("modId");
                if (modId != null) {
                    mongoTemplate.updateFirst(
                            new Query(Criteria.where("_id").is(modId).and("pendingTransferTo").exists(true)),
                            new Update().unset("pendingTransferTo"),
                            net.modtale.model.resources.Mod.class
                    );
                }
            } else if ("ORG_INVITE".equals(n.getType())) {
                String orgId = n.getMetadata().get("orgId");
                if (orgId != null) {
                    mongoTemplate.updateFirst(
                            new Query(Criteria.where("_id").is(orgId)),
                            new Update().pull("pendingOrgInvites", new Query(Criteria.where("userId").is(n.getUserId()))),
                            User.class
                    );
                }
            } else if ("CONTRIBUTOR_INVITE".equals(n.getType())) {
                String modId = n.getMetadata().get("modId");
                if (modId != null) {
                    userRepository.findById(n.getUserId()).ifPresent(u -> {
                        mongoTemplate.updateFirst(
                                new Query(Criteria.where("_id").is(modId)),
                                new Update().pull("pendingInvites", u.getUsername()),
                                net.modtale.model.resources.Mod.class
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
        Query query = new Query(Criteria.where("type").in("TRANSFER_REQUEST", "ORG_INVITE", "CONTRIBUTOR_INVITE")
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
    public void sendActionableNotification(List<String> targetIds, String title, String message, URI link, String iconUrl, String type, Map<String, String> metadata) {
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
        sendActionableNotification(userIds, title, message, link, iconUrl, "INFO", null);
    }
}