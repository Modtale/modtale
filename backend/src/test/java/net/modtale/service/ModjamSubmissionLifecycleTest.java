package net.modtale.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.modtale.exception.ProjectOperationForbiddenException;
import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.ModjamSubmission;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.jam.ModjamSubmissionRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.lifecycle.LifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ModjamSubmissionLifecycleTest {
    private ModjamService service;
    private ModjamRepository jamRepository;
    private ModjamSubmissionRepository submissionRepository;
    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private LifecycleService lifecycleService;
    private Modjam jam;
    private Project project;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ModjamService();
        jamRepository = mock(ModjamRepository.class);
        submissionRepository = mock(ModjamSubmissionRepository.class);
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        lifecycleService = mock(LifecycleService.class);
        ReflectionTestUtils.setField(service, "modjamRepository", jamRepository);
        ReflectionTestUtils.setField(service, "submissionRepository", submissionRepository);
        ReflectionTestUtils.setField(service, "projectRepository", projectRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "lifecycleService", lifecycleService);
        jam = new Modjam();
        jam.setId("jam-1");
        jam.setStatus("ACTIVE");
        jam.setStartDate(Instant.now().minusSeconds(3600));
        jam.setEndDate(Instant.now().plusSeconds(3600));
        jam.setVotingEndDate(Instant.now().plusSeconds(7200));
        jam.setParticipantIds(new ArrayList<>(List.of("user-1")));
        project = new Project();
        project.setId("project-1");
        project.setAuthorId("user-1");
        project.setStatus(ProjectStatus.DRAFT);
        project.setClassification(ProjectClassification.PLUGIN);
        user = new User();
        user.setId("user-1");
        when(jamRepository.findById("jam-1")).thenReturn(Optional.of(jam));
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(submissionRepository.findByJamIdAndSubmitterId("jam-1", "user-1")).thenReturn(List.of());
        when(projectRepository.findAllById(any())).thenReturn(List.of(project));
        when(submissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void restrictionFailureLeavesDraftUntouched() {
        jam.getRestrictions().setRequireSourceRepo(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.submitProject("jam-1", "project-1", "user-1"));
        assertEquals(ProjectStatus.DRAFT, project.getStatus());
        verifyNoInteractions(lifecycleService);
        verify(projectRepository, never()).save(any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void eligibleDraftUsesCanonicalReviewWorkflowBeforeCreatingSubmission() {
        doAnswer(invocation -> {
            assertEquals(ProjectStatus.DRAFT, project.getStatus());
            verify(submissionRepository, never()).save(any());
            project.setStatus(ProjectStatus.PENDING);
            return null;
        }).when(lifecycleService).submitProject("project-1", user);
        ModjamSubmission submission = service.submitProject("jam-1", "project-1", "user-1");
        verify(lifecycleService).submitProject("project-1", user);
        assertEquals("project-1", submission.getProjectId());
        assertTrue(project.getModjamIds().contains("jam-1"));
    }

    @Test
    void canonicalReviewFailureDoesNotCreateSubmission() {
        doThrow(new ProjectOperationForbiddenException("Verify your email address."))
                .when(lifecycleService).submitProject("project-1", user);
        assertThrows(ProjectOperationForbiddenException.class,
                () -> service.submitProject("jam-1", "project-1", "user-1"));
        assertEquals(ProjectStatus.DRAFT, project.getStatus());
        verify(submissionRepository, never()).save(any());
        verify(projectRepository, never()).save(any());
    }

    @Test
    void directSubmissionRequiresParticipation() {
        jam.setParticipantIds(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.submitProject("jam-1", "project-1", "user-1"));
        verifyNoInteractions(projectRepository, lifecycleService, submissionRepository);
    }

    @Test
    void staleActiveStatusCannotSubmitAfterDeadline() {
        jam.setEndDate(Instant.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitProject("jam-1", "project-1", "user-1"));
        verifyNoInteractions(projectRepository, lifecycleService, submissionRepository);
    }

    @Test
    void staleUpcomingStatusCanSubmitOnceStartTimePasses() {
        jam.setStatus("UPCOMING");
        project.setStatus(ProjectStatus.PUBLISHED);
        service.submitProject("jam-1", "project-1", "user-1");
        verify(submissionRepository).save(any());
        verifyNoInteractions(lifecycleService);
    }

    @Test
    void repeatedSubmissionDoesNotEnterDraftWorkflowAgain() {
        ModjamSubmission existing = new ModjamSubmission();
        existing.setProjectId("project-1");
        when(submissionRepository.findByJamIdAndSubmitterId("jam-1", "user-1")).thenReturn(List.of(existing));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitProject("jam-1", "project-1", "user-1"));
        verifyNoInteractions(lifecycleService);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void cannotJoinDraftOrClosedJam() {
        for (String status : List.of("DRAFT", "VOTING", "AWAITING_WINNERS", "COMPLETED")) {
            jam.setStatus(status);
            if (!"DRAFT".equals(status) && !"COMPLETED".equals(status)) {
                jam.setEndDate(Instant.now().minusSeconds(10));
            }
            assertThrows(IllegalArgumentException.class,
                    () -> service.participate("jam-1", "user-1"), status);
        }
        verify(userRepository, never()).save(any());
        verify(jamRepository, never()).save(any());
    }
}
