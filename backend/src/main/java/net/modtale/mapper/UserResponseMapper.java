package net.modtale.mapper;

import net.modtale.model.dto.user.GitRepositoryDTO;
import net.modtale.model.dto.user.NotificationDTO;
import net.modtale.model.user.GitRepository;
import net.modtale.model.user.Notification;

public class UserResponseMapper {
    public static NotificationDTO toNotificationDTO(Notification notification) {
        if (notification == null) return null;
        return new NotificationDTO(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getLink(),
                notification.getIconUrl(),
                notification.isRead(),
                notification.getType(),
                notification.getMetadata(),
                notification.getCreatedAt()
        );
    }

    public static GitRepositoryDTO toGitRepositoryDTO(GitRepository repository) {
        if (repository == null) return null;
        return new GitRepositoryDTO(
                repository.getName(),
                repository.getUrl(),
                repository.getDescription(),
                repository.isPrivate()
        );
    }
}
