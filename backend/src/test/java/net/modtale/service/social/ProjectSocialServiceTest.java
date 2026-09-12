package net.modtale.service.social;

import java.util.*;
import net.modtale.model.project.*;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.admin.review.*;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.validation.SanitizationService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSocialServiceTest {
    private final ProjectRepository repository = mock(ProjectRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final SanitizationService sanitizer = mock(SanitizationService.class);
    private final ProjectReviewPersistence writes = mock(ProjectReviewPersistence.class);
    private final ProjectSocialService service = new ProjectSocialService(repository, users, projects, notifications,
            sanitizer, mock(ScoringService.class), writes);
    private ProjectReviewPersistence.Snapshot setup() {
        var project = new Project(); project.setId("canonical"); project.setAuthorId("author"); project.setAllowComments(true);
        project.setComments(new ArrayList<>(List.of(new Comment("user", "old"))));
        var user = new User(); user.setId("user"); user.setUsername("User");
        when(users.findById("user")).thenReturn(Optional.of(user));
        when(projects.getRawProjectByRouteKey("slug")).thenReturn(project);
        var snapshot = new ProjectReviewPersistence.Snapshot(new Document(), project);
        when(writes.capture(eq("canonical"), anyString())).thenReturn(snapshot);
        when(sanitizer.sanitizePlainText(anyString())).thenAnswer(inv -> inv.getArgument(0));
        return snapshot;
    }
    @Test void allCommentWritesFailBeforeCacheOrNotificationOnConflict() {
        for (int action = 0; action < 4; action++) {
            var snapshot = setup(); String id = snapshot.project().getComments().getFirst().getId();
            int selected = action;
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
                switch (selected) {
                    case 0 -> service.addComment("slug", "user", "new");
                    case 1 -> service.editComment("slug", id, "user", "new");
                    case 2 -> service.voteComment("slug", id, "user", true);
                    default -> service.setCommentPinned("slug", id, true);
                }
            });
        }
        verify(projects, never()).evictProjectCache(any(Project.class));
        verifyNoInteractions(repository, notifications);
        verify(users, never()).save(any(User.class));
    }
    @Test void successfulCommentActionsUseCanonicalSnapshotAndPreserveBehavior() {
        var snapshot = setup(); when(writes.applyComments(any())).thenReturn(true);
        var comment = snapshot.project().getComments().getFirst();
        service.addComment("slug", "user", "new");
        assertEquals("new", snapshot.project().getComments().getFirst().getContent());
        service.editComment("slug", comment.getId(), "user", "edited"); assertEquals("edited", comment.getContent());
        service.voteComment("slug", comment.getId(), "user", true); assertTrue(comment.getUpvotes().contains("user"));
        service.setCommentPinned("slug", comment.getId(), true); assertTrue(comment.isPinned());
        verify(writes, times(4)).applyComments(snapshot);
        verify(projects, times(4)).evictProjectCache(snapshot.project()); verifyNoInteractions(repository);
    }
}
