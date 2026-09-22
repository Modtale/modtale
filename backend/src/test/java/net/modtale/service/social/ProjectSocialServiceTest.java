package net.modtale.service.social;

import java.util.*;
import net.modtale.model.project.*;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.validation.SanitizationService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSocialServiceTest {
    private final FavoritePersistence repository = mock(FavoritePersistence.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final SanitizationService sanitizer = mock(SanitizationService.class);

    private final ProjectSocialService service = new ProjectSocialService(mock(ProjectRepository.class), users, projects, notifications,
            sanitizer, mock(ScoringService.class), repository);
    @Test void favoriteFailureDoesNotEvictAndSuccessUsesCanonicalProjectId() {
        var project = new Project(); project.setId("canonical");
        when(projects.getRawProjectByRouteKey("slug")).thenReturn(project);
        when(repository.toggle("canonical", "user")).thenThrow(new IllegalStateException("transaction failed"));
        assertThrows(IllegalStateException.class, () -> service.toggleFavorite("slug", "user"));
        verify(projects, never()).evictProjectCache(any(Project.class)); verify(users, never()).save(any(User.class));
        doReturn(1).when(repository).toggle("canonical", "user");
        service.toggleFavorite("slug", "user");
        assertEquals(1, project.getFavoriteCount()); verify(projects).evictProjectCache(project);
    }

}
