package net.modtale.repository.user;

import net.modtale.model.user.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserIdAndIsReadFalse(String userId);

    void deleteByUserIdAndLink(String userId, String link);

    void deleteByUserId(String userId);
}