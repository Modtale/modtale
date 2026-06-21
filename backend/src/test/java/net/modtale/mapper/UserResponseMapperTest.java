package net.modtale.mapper;

import java.time.LocalDateTime;
import java.util.Map;
import net.modtale.model.dto.user.GitRepositoryDTO;
import net.modtale.model.dto.user.NotificationDTO;
import net.modtale.model.user.GitRepository;
import net.modtale.model.user.Notification;
import net.modtale.model.user.NotificationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserResponseMapperTest {

    @Test
    void toNotificationDTOMapsUserFacingNotificationFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 11, 0);
        Notification notification = new Notification();
        notification.setId("note-1");
        notification.setTitle("Build ready");
        notification.setMessage("Your artifact is available.");
        notification.setLink("https://example.com/project");
        notification.setIconUrl("https://example.com/icon.png");
        notification.setRead(true);
        notification.setType(NotificationType.TRANSFER_REQUEST);
        notification.setMetadata(Map.of("projectId", "project-1"));
        notification.setCreatedAt(createdAt);

        NotificationDTO dto = UserResponseMapper.toNotificationDTO(notification);

        assertEquals("note-1", dto.id());
        assertEquals("Build ready", dto.title());
        assertTrue(dto.read());
        assertEquals(NotificationType.TRANSFER_REQUEST, dto.type());
        assertEquals("project-1", dto.metadata().get("projectId"));
        assertEquals(createdAt, dto.createdAt());
        assertNull(UserResponseMapper.toNotificationDTO(null));
    }

    @Test
    void toGitRepositoryDTOMapsRepositoryVisibility() {
        GitRepository repository = new GitRepository();
        repository.setName("modtale/sky-tools");
        repository.setUrl("https://github.com/modtale/sky-tools");
        repository.setDescription("Automation helpers");
        repository.setVisibility("internal");

        GitRepositoryDTO dto = UserResponseMapper.toGitRepositoryDTO(repository);

        assertEquals("modtale/sky-tools", dto.name());
        assertEquals("https://github.com/modtale/sky-tools", dto.url());
        assertEquals("Automation helpers", dto.description());
        assertTrue(dto.isPrivate());
        assertNull(UserResponseMapper.toGitRepositoryDTO(null));

        repository.setPrivate(false);
        assertFalse(UserResponseMapper.toGitRepositoryDTO(repository).isPrivate());
    }
}
