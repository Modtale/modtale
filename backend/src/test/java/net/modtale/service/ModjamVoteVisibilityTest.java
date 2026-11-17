package net.modtale.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.ModjamSubmission;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.jam.ModjamSubmissionRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.jam.ModjamOrganizerService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ModjamVoteVisibilityTest {
    private ModjamService service;
    private ModjamRepository jams;
    private ModjamSubmissionRepository submissions;
    private ProjectRepository projects;
    private AccountService account;
    private AccessControlService access;
    private Modjam jam;
    private ModjamSubmission submission;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ModjamService();
        jams = mock(ModjamRepository.class);
        submissions = mock(ModjamSubmissionRepository.class);
        projects = mock(ProjectRepository.class);
        account = mock(AccountService.class);
        access = mock(AccessControlService.class);
        ReflectionTestUtils.setField(service, "modjamRepository", jams);
        ReflectionTestUtils.setField(service, "feedService", mock(net.modtale.service.jam.ModjamDiscordFeedService.class));
        ReflectionTestUtils.setField(service, "submissionRepository", submissions);
        ReflectionTestUtils.setField(service, "projectRepository", projects);
        ReflectionTestUtils.setField(service, "accountService", account);
        ReflectionTestUtils.setField(service, "accessControlService", access);
        var votePersistence = mock(net.modtale.service.jam.ModjamVotePersistence.class);
        ReflectionTestUtils.setField(service, "votePersistence", votePersistence);
        when(votePersistence.replaceBallot(any(), any(), any())).thenAnswer(invocation -> {
            ModjamSubmission.Vote vote = invocation.getArgument(2);
            submission.getVotes().removeIf(existing -> vote.getVoterId().equals(existing.getVoterId())
                    && vote.getCategoryId().equals(existing.getCategoryId()));
            submission.getVotes().add(vote);
            submission.setVoteRevision(submission.getVoteRevision() + 1);
            return submission;
        });
        ReflectionTestUtils.setField(service, "organizerService", new ModjamOrganizerService(
                jams, mock(UserRepository.class), mock(MongoTemplate.class)));
        jam = new Modjam();
        jam.setId("jam-1");
        jam.setHostId("host-1");
        jam.setStatus("VOTING");
        jam.setStartDate(Instant.now().minusSeconds(7200));
        jam.setEndDate(Instant.now().minusSeconds(3600));
        jam.setVotingEndDate(Instant.now().plusSeconds(3600));
        jam.setAllowPublicVoting(true);
        jam.setCategories(List.of(new Modjam.Category("quality", "Quality", "", 10)));
        project = new Project();
        project.setId("project-1");
        project.setAuthorId("author-1");
        project.setStatus(ProjectStatus.PUBLISHED);
        submission = new ModjamSubmission();
        submission.setId("submission-1");
        submission.setJamId("jam-1");
        submission.setProjectId("project-1");
        submission.setSubmitterId("author-1");
        submission.setVotes(new ArrayList<>(List.of(
                new ModjamSubmission.Vote("vote-1", "viewer-1", "quality", 5, false),
                new ModjamSubmission.Vote("vote-2", "other-1", "quality", 9, false))));
        submission.setCategoryScores(Map.of("quality", 7.0));
        submission.setJudgeCategoryScores(Map.of("quality", 9.0));
        submission.setTotalScore(7.0);
        submission.setTotalJudgeScore(9.0);
        submission.setTotalPublicScore(7.0);
        submission.setRank(1);
        when(jams.findById("jam-1")).thenReturn(Optional.of(jam));
        when(submissions.findByJamId("jam-1")).thenAnswer(invocation -> new ArrayList<>(List.of(submission)));
        when(submissions.findById("submission-1")).thenReturn(Optional.of(submission));
        when(projects.findById("project-1")).thenReturn(Optional.of(project));
        when(projects.findAllById(any())).thenReturn(List.of(project));
    }

    @Test
    void anonymousReadRedactsScoresAndBallotsWithoutChangingStoredDocument() {
        var response = service.getSubmissions("jam-1").getFirst();
        assertNotSame(submission, response);
        assertNull(response.getTotalScore());
        assertNull(response.getTotalJudgeScore());
        assertNull(response.getTotalPublicScore());
        assertNull(response.getCategoryScores());
        assertNull(response.getJudgeCategoryScores());
        assertNull(response.getRank());
        assertTrue(response.getVotes().isEmpty());
        assertEquals(7.0, submission.getTotalScore());
        assertEquals(2, submission.getVotes().size());
    }

    @Test
    void browserViewerRetainsOnlyTheirOwnBallot() {
        when(account.getCurrentUser()).thenReturn(user("viewer-1"));
        var response = service.getSubmissions("jam-1").getFirst();
        assertEquals(1, response.getVotes().size());
        assertEquals("viewer-1", response.getVotes().getFirst().getVoterId());
        assertNull(response.getTotalScore());
    }

    @Test
    void readOnlyOrganizerCanViewResultsButCannotJudge() {
        jam.setAllowPublicVoting(false);
        jam.setOrganizerRoles(List.of(new Modjam.OrganizerRole("view", "Viewer", "#123456",
                Set.of(Modjam.JamPermission.VIEW_RESULTS))));
        jam.setOrganizerMembers(List.of(new Modjam.OrganizerMember("viewer-1", "view")));
        when(account.getCurrentUser()).thenReturn(user("viewer-1"));
        assertEquals(7.0, service.getSubmissions("jam-1").getFirst().getTotalScore());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.vote("jam-1", "submission-1", "quality", 8, "viewer-1")).getStatusCode());
    }

    @Test
    void apiKeyCannotInheritOwnersPrivateBallotsOrHostPrivileges() {
        when(account.getCurrentUser()).thenReturn(user("host-1"));
        when(access.isApiKey(any())).thenReturn(true);
        var response = service.getSubmissions("jam-1").getFirst();
        assertNull(response.getTotalScore());
        assertTrue(response.getVotes().isEmpty());
        jam.setHideSubmissions(true);
        jam.setEndDate(Instant.now().plusSeconds(3600));
        assertTrue(service.getSubmissions("jam-1").isEmpty());
    }

    @Test
    void staleAwaitingStatusDoesNotRevealScoresBeforeDeadline() {
        jam.setStatus("AWAITING_WINNERS");
        assertNull(service.getSubmissions("jam-1").getFirst().getTotalScore());
    }

    @Test
    void resultsRevealAtDeadlineWithoutExposingOtherBallots() {
        jam.setVotingEndDate(Instant.now().minusSeconds(1));
        var response = service.getSubmissions("jam-1").getFirst();
        assertEquals(7.0, response.getTotalScore());
        assertTrue(response.getVotes().isEmpty());
    }

    @Test
    void staleVotingStatusCannotExposeSecretEntriesDuringSubmissionPhase() {
        jam.setHideSubmissions(true);
        jam.setEndDate(Instant.now().plusSeconds(3600));
        assertTrue(service.getSubmissions("jam-1").isEmpty());
    }

    @Test
    void rejectsCrossJamSubmissionBeforeAnyMutation() {
        submission.setJamId("different-jam");
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.vote("jam-1", "submission-1", "quality", 8, "viewer-1")).getStatusCode());
        verify(projects, never()).findById(any());
        verify(submissions, never()).save(any());
    }

    @Test
    void teammateCannotVoteOnOwnEntry() {
        project.setTeamMembers(List.of(new Project.ProjectMember("viewer-1", "developer")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.vote("jam-1", "submission-1", "quality", 8, "viewer-1")).getStatusCode());
        verify(submissions, never()).save(any());
    }

    @Test
    void rejectsInvalidCategoryAndScoreWithoutLoadingSubmission() {
        for (int score : List.of(0, -1, 11, Integer.MAX_VALUE)) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.vote("jam-1", "submission-1", "quality", score, "viewer-1")).getStatusCode());
        }
        assertThrows(ResponseStatusException.class,
                () -> service.vote("jam-1", "submission-1", "foreign-category", 5, "viewer-1"));
        verifyNoInteractions(submissions);
    }

    @Test
    void staleActiveStatusRejectsVotesAfterDeadline() {
        jam.setStatus("ACTIVE");
        jam.setVotingEndDate(Instant.now().minusSeconds(1));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.vote("jam-1", "submission-1", "quality", 8, "viewer-1")).getStatusCode());
        verifyNoInteractions(submissions);
    }

    @Test
    void voteReplayReplacesOnlyOwnCategoryAndReturnsRedactedCopy() {
        jam.setStatus("UPCOMING");
        var response = service.vote("jam-1", "submission-1", "quality", 8, "viewer-1");
        assertEquals(2, submission.getVotes().size());
        assertEquals(1, response.getVotes().size());
        assertEquals(8, response.getVotes().getFirst().getScore());
        assertNull(response.getTotalScore());
        service.vote("jam-1", "submission-1", "quality", 6, "viewer-1");
        assertEquals(2, submission.getVotes().size());
        assertEquals(9, submission.getVotes().stream().filter(vote -> "other-1".equals(vote.getVoterId())).findFirst().orElseThrow().getScore());
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
