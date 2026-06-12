package net.modtale.service.communication;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.modtale.model.user.Notification;
import net.modtale.model.user.NotificationType;
import net.modtale.model.user.User;
import net.modtale.repository.user.NotificationRepository;
import net.modtale.repository.user.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeliveryService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationDeliveryService(
            NotificationRepository notificationRepository,
            UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Async
    public void sendNotifcation(List<String> targetIds, String title, String message, URI link, String iconUrl, NotificationType type, Map<String, String> metadata) {
        if (targetIds.isEmpty()) return;

        List<Notification> toSave = new ArrayList<>();

        for (String targetId : targetIds) {
            User target = userRepository.findById(targetId).orElse(null);
            if (target == null) continue;

            if (target.getAccountType() == User.AccountType.ORGANIZATION) {
                String orgContextTitle = "[" + target.getUsername() + "] " + title;
                target.getOrganizationMembers().stream()
                        .filter(m -> "ADMIN".equals(m.getRole()))
                        .forEach(admin -> toSave.add(
                                new Notification(admin.getUserId(), orgContextTitle, message, link, iconUrl, type, metadata)
                        ));
            } else {
                toSave.add(new Notification(targetId, title, message, link, iconUrl, type, metadata));
            }
        }

        if (!toSave.isEmpty()) {
            notificationRepository.saveAll(toSave);
        }
    }

    @Async
    public void sendNotifcation(List<String> userIds, String title, String message, URI link, String iconUrl) {
        sendNotifcation(userIds, title, message, link, iconUrl, NotificationType.INFO, null);
    }
}
