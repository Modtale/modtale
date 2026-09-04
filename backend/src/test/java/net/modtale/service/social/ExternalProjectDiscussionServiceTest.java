package net.modtale.service.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.ExternalProjectDiscussion;
import net.modtale.repository.project.ExternalProjectDiscussionRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.validation.SanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalProjectDiscussionServiceTest {
    private ExternalProjectDiscussionService service;
    private final Map<String, ExternalProjectDiscussion> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        ExternalProjectDiscussionRepository repository = mock(ExternalProjectDiscussionRepository.class);
        UserRepository users = mock(UserRepository.class);
        SanitizationService sanitizer = mock(SanitizationService.class);
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(repository.save(any())).thenAnswer(invocation -> {
            ExternalProjectDiscussion value = invocation.getArgument(0);
            stored.put(value.getId(), value);
            return value;
        });
        when(users.existsById(any())).thenReturn(true);
        when(sanitizer.sanitizePlainText(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ExternalProjectDiscussionService(repository, users, sanitizer);
    }

    @Test
    void supportsCurseForgeCommentsVotesAndPinnedOrdering() {
        service.addComment("curseforge", "1450386", "user-1", "First");
        service.addComment("CURSEFORGE", "1450386", "user-2", "Second");
        String firstId = service.getComments("curseforge", "1450386", null).comments().get(1).id();

        service.voteComment("curseforge", "1450386", firstId, "user-2", true);
        service.setPinned("curseforge", "1450386", firstId, true);

        var comments = service.getComments("curseforge", "1450386", "user-2").comments();
        assertEquals("First", comments.getFirst().content());
        assertTrue(comments.getFirst().pinned());
        assertEquals("up", comments.getFirst().userVote());
        assertFalse(comments.get(1).pinned());
    }

    @Test
    void onlyCommentOwnerCanEditExternalComment() {
        service.addComment("curseforge", "1450386", "user-1", "First");
        String commentId = service.getComments("curseforge", "1450386", null).comments().getFirst().id();

        assertThrows(ForbiddenOperationException.class,
                () -> service.editComment("curseforge", "1450386", commentId, "user-2", "Changed"));
    }

    @Test
    void rejectsUnsupportedProvidersAndInvalidIds() {
        assertThrows(InvalidProjectRequestException.class,
                () -> service.getComments("modrinth", "1", null));
        assertThrows(InvalidProjectRequestException.class,
                () -> service.getComments("curseforge", "not-a-number", null));
    }
}
