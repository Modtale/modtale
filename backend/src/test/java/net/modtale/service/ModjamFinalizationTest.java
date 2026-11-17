package net.modtale.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.ModjamSubmission;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.jam.ModjamSubmissionRepository;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ModjamFinalizationTest {
    private ModjamService service;
    private ModjamRepository jamRepository;
    private ModjamSubmissionRepository submissionRepository;
    private Modjam jam;
    private ModjamSubmission submission;
    private org.springframework.data.mongodb.core.MongoTemplate mongo;
    private net.modtale.service.jam.ModjamVotePersistence votePersistence;

    @BeforeEach
    void setUp() {
        service = new ModjamService();
        jamRepository = mock(ModjamRepository.class);
        submissionRepository = mock(ModjamSubmissionRepository.class);
        ReflectionTestUtils.setField(service, "modjamRepository", jamRepository);
        ReflectionTestUtils.setField(service, "feedService", mock(net.modtale.service.jam.ModjamDiscordFeedService.class));
        ReflectionTestUtils.setField(service, "submissionRepository", submissionRepository);
        votePersistence = mock(net.modtale.service.jam.ModjamVotePersistence.class);
        ReflectionTestUtils.setField(service, "votePersistence", votePersistence);
        mongo = mock(org.springframework.data.mongodb.core.MongoTemplate.class);
        when(mongo.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class), any(org.springframework.data.mongodb.core.query.Update.class), eq(Modjam.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
        ReflectionTestUtils.setField(service, "mongoTemplate", mongo);
        ReflectionTestUtils.setField(service, "userRepository", mock(UserRepository.class));
        ReflectionTestUtils.setField(service, "organizerService", new net.modtale.service.jam.ModjamOrganizerService(
                jamRepository, mock(UserRepository.class), mock(org.springframework.data.mongodb.core.MongoTemplate.class)));
        jam = new Modjam();
        jam.setId("jam-1");
        jam.setHostId("host-1");
        jam.setStatus("AWAITING_WINNERS");
        jam.setStartDate(Instant.now().minusSeconds(7200));
        jam.setEndDate(Instant.now().minusSeconds(3600));
        jam.setVotingEndDate(Instant.now().minusSeconds(1));
        submission = new ModjamSubmission();
        submission.setId("submission-1");
        submission.setJamId("jam-1");
        when(jamRepository.findById("jam-1")).thenReturn(Optional.of(jam));
        when(mongo.findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(Modjam.class))).thenReturn(jam);
        when(mongo.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), eq(Modjam.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
        when(submissionRepository.findByJamId("jam-1")).thenAnswer(
                invocation -> new ArrayList<>(List.of(submission)));
        when(jamRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongo.findAndModify(any(org.springframework.data.mongodb.core.query.Query.class), any(org.springframework.data.mongodb.core.query.Update.class),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(Modjam.class))).thenAnswer(invocation -> jam);
    }

    @Test
    void cannotFinalizeBeforeVotingDeadlineDespiteStaleStatus() {
        jam.setVotingEndDate(Instant.now().plusSeconds(3600));
        assertThrows(IllegalArgumentException.class,
                () -> service.finalizeJam("jam-1", "host-1", winners()));
        verifyNoInteractions(submissionRepository);
        verify(jamRepository, never()).save(any());
    }

    @Test
    void staleVotingStatusCanFinalizeOnceDeadlinePasses() {
        jam.setStatus("VOTING");
        service.finalizeJam("jam-1", "host-1", winners());
        assertEquals("COMPLETED", jam.getStatus());
        assertTrue(submission.isWinner());
        assertEquals("Best Overall", submission.getAwardTitle());
    }

    @Test
    void delegatedAnnouncerFinalizesWithNarrowStatusAndAwardWrites() {
        jam.setOrganizerRoles(List.of(new Modjam.OrganizerRole("announce", "Announcer", "#123456",
                Set.of(Modjam.JamPermission.ANNOUNCE_WINNERS))));
        jam.setOrganizerMembers(List.of(new Modjam.OrganizerMember("announcer", "announce")));
        service.finalizeJam("jam-1", "announcer", winners());
        assertEquals("COMPLETED", jam.getStatus());
        verify(votePersistence).saveAward(submission);
        verify(votePersistence).saveScores(submission);
        var update = org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongo).updateFirst(any(org.springframework.data.mongodb.core.query.Query.class), update.capture(), eq(Modjam.class));
        assertEquals(Set.of("status", "updatedAt", "winnersAnnouncedAt"),
                update.getValue().getUpdateObject().get("$set", org.bson.Document.class).keySet());
        verify(jamRepository, never()).save(any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void rejectsWinnerFromAnotherJamBeforeCalculatingOrSaving() {
        assertThrows(IllegalArgumentException.class,
                () -> service.finalizeJam("jam-1", "host-1", List.of(
                        Map.of("submissionId", "foreign-submission", "awardTitle", "Winner"))));
        verify(submissionRepository, times(1)).findByJamId("jam-1");
        verify(submissionRepository, never()).save(any());
        verify(jamRepository, never()).save(any());
        assertFalse(submission.isWinner());
    }

    @Test
    void malformedWinnerInputCannotPartiallyFinalize() {
        List<List<Map<String, String>>> invalidRequests = new ArrayList<>();
        invalidRequests.add(null);
        invalidRequests.add(java.util.Arrays.asList((Map<String, String>) null));
        invalidRequests.add(List.of(Map.of("awardTitle", "Winner")));
        invalidRequests.add(List.of(Map.of("submissionId", "submission-1")));
        invalidRequests.add(List.of(Map.of("submissionId", "submission-1", "awardTitle", " ")));
        invalidRequests.add(List.of(winners().getFirst(), winners().getFirst()));
        for (var request : invalidRequests) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.finalizeJam("jam-1", "host-1", request));
        }
        assertEquals("AWAITING_WINNERS", jam.getStatus());
        assertFalse(submission.isWinner());
        verify(submissionRepository, never()).save(any());
        verify(jamRepository, never()).save(any());
    }

    @Test
    void identicalFinalizationReplayDoesNotWriteAgain() {
        service.finalizeJam("jam-1", "host-1", winners());
        clearInvocations(jamRepository, submissionRepository);
        service.finalizeJam("jam-1", "host-1", winners());
        verify(submissionRepository, never()).save(any());
        verify(jamRepository, never()).save(any());
    }

    @Test
    void competingFinalizationClaimCannotWriteAwards() {
        when(mongo.findAndModify(any(org.springframework.data.mongodb.core.query.Query.class), any(org.springframework.data.mongodb.core.query.Update.class),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(Modjam.class))).thenReturn(null);
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.finalizeJam("jam-1", "host-1", winners()));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(votePersistence);
        verify(mongo, never()).updateFirst(any(org.springframework.data.mongodb.core.query.Query.class), any(org.springframework.data.mongodb.core.query.Update.class), eq(Modjam.class));
    }

    @Test
    void completedJamCannotReplaceWinnersOnReplay() {
        service.finalizeJam("jam-1", "host-1", winners());
        clearInvocations(jamRepository, submissionRepository);
        assertThrows(IllegalArgumentException.class,
                () -> service.finalizeJam("jam-1", "host-1", List.of()));
        assertTrue(submission.isWinner());
        verify(submissionRepository, never()).save(any());
        verify(jamRepository, never()).save(any());
    }

    private static List<Map<String, String>> winners() {
        return List.of(Map.of("submissionId", "submission-1", "awardTitle", "Best Overall"));
    }
}
