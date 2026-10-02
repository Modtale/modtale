package net.modtale.service.jam;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.Modjam.JamPermission;
import net.modtale.model.user.User;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;

class ModjamOrganizerServiceTest {
    private ModjamOrganizerService service;
    private ModjamRepository jams;
    private UserRepository users;
    private MongoTemplate mongo;
    private Modjam jam;

    @BeforeEach
    void setUp() {
        jams = mock(ModjamRepository.class);
        users = mock(UserRepository.class);
        mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class), any(org.springframework.data.mongodb.core.query.Update.class), eq(Modjam.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
        service = new ModjamOrganizerService(jams, users, mongo);
        jam = new Modjam();
        jam.setId("jam-1");
        jam.setHostId("host-1");
        jam.setOrganizerRoles(new ArrayList<>(List.of(new Modjam.OrganizerRole("editor", "Editor", "#123456", Set.of(JamPermission.EDIT_DETAILS)))));
        jam.setOrganizerMembers(new ArrayList<>(List.of(new Modjam.OrganizerMember("editor-1", "editor"))));
        when(jams.findById("jam-1")).thenReturn(Optional.of(jam));
    }

    @Test
    void hostHasEveryPermissionAndMembersOnlyHaveGrantedPermissions() {
        for (JamPermission permission : JamPermission.values()) assertTrue(service.permits(jam, "host-1", permission));
        assertTrue(service.permits(jam, "editor-1", JamPermission.EDIT_DETAILS));
        assertFalse(service.permits(jam, "editor-1", JamPermission.ANNOUNCE_WINNERS));
        assertFalse(service.permits(jam, null, JamPermission.EDIT_DETAILS));
        assertFalse(service.permits(jam, "stranger", JamPermission.EDIT_DETAILS));
        jam.setOrganizerRoles(List.of());
        assertFalse(service.permits(jam, "editor-1", JamPermission.EDIT_DETAILS));
    }

    @Test
    void delegatedEditorsCannotGrantThemselvesExtraPermissions() {
        assertThrows(ResponseStatusException.class, () -> service.saveRole("jam-1", "editor-1",
                new Modjam.OrganizerRole("editor", "Owner", "#123456", Set.of(JamPermission.ANNOUNCE_WINNERS))));
        verifyNoInteractions(mongo);
    }

    @Test
    void invitationBindsToUserIdAndAcceptedMembershipIsNotReplacedByUsernameReuse() {
        User target = new User();
        target.setId("invited-id");
        target.setUsername("Builder");
        when(users.findByUsernameIgnoreCase("Builder")).thenReturn(Optional.of(target));
        service.invite("jam-1", "host-1", " Builder ", "editor");
        assertEquals("invited-id", jam.getPendingOrganizerInvites().getFirst().userId());
        assertThrows(ResponseStatusException.class, () -> service.answerInvite("jam-1", "new-owner-of-username", true));
        service.answerInvite("jam-1", "invited-id", true);
        assertTrue(service.permits(jam, "invited-id", JamPermission.EDIT_DETAILS));
        assertTrue(jam.getPendingOrganizerInvites().isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.answerInvite("jam-1", "invited-id", true));
    }

    @Test
    void rolesCannotBeDeletedWhileAssignedAndHostCannotBeRemoved() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteRole("jam-1", "host-1", "editor"));
        assertThrows(IllegalArgumentException.class, () -> service.remove("jam-1", "host-1", "host-1"));
        service.remove("jam-1", "host-1", "editor-1");
        assertFalse(service.permits(jam, "editor-1", JamPermission.EDIT_DETAILS));
        service.deleteRole("jam-1", "host-1", "editor");
        assertTrue(jam.getOrganizerRoles().isEmpty());
        verify(jams, never()).save(any());
    }
}
