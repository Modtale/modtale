package net.modtale.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.Modjam.JamPermission;
import net.modtale.model.user.User;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.jam.ModjamCustomizationService;
import net.modtale.service.jam.ModjamOrganizerService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ModjamManagementTest {
    private ModjamService service;
    private ModjamRepository jams;
    private UserRepository users;
    private AccountService account;
    private MongoTemplate mongo;
    private Modjam jam;

    @BeforeEach
    void setUp() {
        service = new ModjamService();
        jams = mock(ModjamRepository.class);
        users = mock(UserRepository.class);
        account = mock(AccountService.class);
        mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class), any(org.springframework.data.mongodb.core.query.Update.class), eq(Modjam.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
        ReflectionTestUtils.setField(service, "modjamRepository", jams);
        ReflectionTestUtils.setField(service, "feedService", mock(net.modtale.service.jam.ModjamDiscordFeedService.class));
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "accountService", account);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongo);
        ReflectionTestUtils.setField(service, "membershipPersistence", new net.modtale.service.jam.ModjamMembershipPersistence(mongo));
        ReflectionTestUtils.setField(service, "customizationService", mock(ModjamCustomizationService.class));
        ReflectionTestUtils.setField(service, "organizerService", new ModjamOrganizerService(jams, users, mock(MongoTemplate.class)));
        jam = new Modjam();
        jam.setId("jam-1");
        jam.setSlug("new-jam");
        jam.setTitle("New Jam");
        jam.setHostId("host");
        jam.setOrganizerRoles(new ArrayList<>(List.of(new Modjam.OrganizerRole("editor", "Editor", "#123456", Set.of(JamPermission.EDIT_DETAILS)))));
        jam.setOrganizerMembers(new ArrayList<>(List.of(new Modjam.OrganizerMember("member", "editor"))));
        when(jams.findById("jam-1")).thenReturn(Optional.of(jam));
        when(jams.findBySlug("new-jam")).thenReturn(Optional.of(jam));
        when(jams.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void publicListAlwaysExcludesDraftsAndPendingInviteRecipients() {
        Modjam publicJam = new Modjam();
        publicJam.setStatus("ACTIVE");
        publicJam.setPendingOrganizerInvites(List.of(new Modjam.OrganizerInvite("private-user", "Builder", "editor")));
        when(jams.findAll()).thenReturn(List.of(jam, publicJam));
        List<Modjam> result = service.getAllJams();
        assertEquals(1, result.size());
        assertTrue(result.getFirst().getPendingOrganizerInvites().isEmpty());
        assertEquals(1, publicJam.getPendingOrganizerInvites().size());
        verifyNoInteractions(account);
    }

    @Test
    void anonymousAndHostOwnedApiKeysCannotReadDrafts() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.getJamBySlug("new-jam")).getStatusCode());
        User host = user("host", "Host");
        when(account.getCurrentUser()).thenReturn(host);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("host", null, List.of(new SimpleGrantedAuthority("ROLE_API"))));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.getJamBySlug("new-jam")).getStatusCode());
    }

    @Test
    void acceptedMembersCanReadDraftsAndInviteesOnlySeeTheirOwnInvitation() {
        when(account.getCurrentUser()).thenReturn(user("member", "Editor"));
        assertEquals("jam-1", service.getJamBySlug("new-jam").getId());
        jam.setPendingOrganizerInvites(List.of(new Modjam.OrganizerInvite("invitee", "Invitee", "editor"), new Modjam.OrganizerInvite("other", "Other", "editor")));
        when(account.getCurrentUser()).thenReturn(user("invitee", "Invitee"));
        assertEquals(List.of("invitee"), service.getJamBySlug("new-jam").getPendingOrganizerInvites().stream().map(Modjam.OrganizerInvite::userId).toList());
        assertEquals(2, jam.getPendingOrganizerInvites().size());
    }

    @Test
    void editorCannotChangeRulesOrSettingsButCanChangeDetails() {
        Modjam update = new Modjam();
        BeanUtils.copyProperties(jam, update);
        update.setRules("Changed rules");
        assertThrows(ResponseStatusException.class, () -> service.updateJam("jam-1", update, "member"));
        verify(jams, never()).save(any());
        update.setRules(jam.getRules());
        update.setAllowPublicVoting(!jam.isAllowPublicVoting());
        assertThrows(ResponseStatusException.class, () -> service.updateJam("jam-1", update, "member"));
        update.setAllowPublicVoting(jam.isAllowPublicVoting());
        update.setDescription("Updated description");
        assertEquals("Updated description", service.updateJam("jam-1", update, "member").getDescription());
        var mutation = org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongo).updateFirst(any(org.springframework.data.mongodb.core.query.Query.class), mutation.capture(), eq(Modjam.class));
        var fields = (org.bson.Document) mutation.getValue().getUpdateObject().get("$set");
        assertFalse(fields.containsKey("participantIds"));
        assertFalse(fields.containsKey("organizerMembers"));
        assertFalse(fields.containsKey("pendingJudgeInviteUsers"));
        verify(jams, never()).save(any());
    }

    @Test
    void schedulerStatusWritesCannotOverwriteFinalizationOrReopenCompletedJams() {
        jam.setStatus("VOTING");
        jam.setStartDate(java.time.Instant.now().minusSeconds(7200));
        jam.setEndDate(java.time.Instant.now().minusSeconds(3600));
        jam.setVotingEndDate(java.time.Instant.now().minusSeconds(1));
        when(jams.findAll()).thenReturn(List.of(jam));
        service.updateJamStates();
        var query = org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Query.class);
        verify(mongo).updateFirst(query.capture(), any(org.springframework.data.mongodb.core.query.Update.class), eq(Modjam.class));
        assertEquals("VOTING", query.getValue().getQueryObject().get("status"));
        assertEquals(false, ((org.bson.Document) query.getValue().getQueryObject().get("finalizationHash")).get("$exists"));
        verify(jams, never()).save(any());
    }

    @Test
    void judgeInvitationIsBoundToIdentityThroughUsernameChanges() {
        jam.setPendingJudgeInvites(new ArrayList<>(List.of("OriginalName")));
        jam.setPendingJudgeInviteUsers(new LinkedHashMap<>(java.util.Map.of("invited-id", "OriginalName")));
        assertThrows(IllegalArgumentException.class, () -> service.acceptJudgeInvite("jam-1", "reused-name-owner", "OriginalName"));
        Modjam accepted = new Modjam();
        BeanUtils.copyProperties(jam, accepted);
        accepted.setJudgeIds(List.of("invited-id"));
        accepted.setPendingJudgeInvites(List.of());
        accepted.setPendingJudgeInviteUsers(java.util.Map.of());
        when(mongo.findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.aggregation.AggregationUpdate.class),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(Modjam.class))).thenReturn(accepted);
        Modjam result = service.acceptJudgeInvite("jam-1", "invited-id", "RenamedUser");
        assertEquals(List.of("invited-id"), result.getJudgeIds());
        assertTrue(result.getPendingJudgeInvites().isEmpty());
        assertTrue(result.getPendingJudgeInviteUsers().isEmpty());
    }

    @Test
    void draftCreationCannotSmuggleMembersJudgesOrOrganizerGrants() {
        jam.setParticipantIds(new ArrayList<>(List.of("fake-participant")));
        jam.setJudgeIds(new ArrayList<>(List.of("fake-judge")));
        jam.setPendingJudgeInviteUsers(new LinkedHashMap<>(java.util.Map.of("fake-id", "Fake")));
        when(jams.findBySlug("new-jam")).thenReturn(Optional.empty());
        Modjam created = service.createJam(jam, "host", "Host");
        assertEquals("DRAFT", created.getStatus());
        assertTrue(created.getParticipantIds().isEmpty());
        assertTrue(created.getJudgeIds().isEmpty());
        assertTrue(created.getPendingJudgeInviteUsers().isEmpty());
        assertTrue(created.getOrganizerMembers().isEmpty());
        assertTrue(created.getOrganizerRoles().isEmpty());
    }

    private static User user(String id, String username) { User user = new User(); user.setId(id); user.setUsername(username); return user; }
}
