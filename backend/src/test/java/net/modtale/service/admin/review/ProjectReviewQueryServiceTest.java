package net.modtale.service.admin.review;

import java.util.List;
import net.modtale.model.dto.admin.AdminVerificationQueueItemDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectListingQueryService;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectReviewQueryServiceTest {

    @Test
    void verificationQueueReturnsRiskOrderedDedicatedDtos() {
        ProjectReviewQueueService queueService = mock(ProjectReviewQueueService.class);
        ProjectReviewQueryService queryService = new ProjectReviewQueryService(
                mock(UserRepository.class),
                mock(ProjectService.class),
                queueService,
                mock(ProjectListingQueryService.class)
        );

        Project olderClean = queueProject("clean", "2026-01-01T00:00:00", ScanStatus.CLEAN, "ALLOW", 5, 0);
        Project newerRisky = queueProject("risky", "2026-02-01T00:00:00", ScanStatus.SUSPICIOUS, "REVIEW", 80, 2);
        when(queueService.getVerificationQueue()).thenReturn(List.of(olderClean, newerRisky));

        List<AdminVerificationQueueItemDTO> queue = queryService.getVerificationQueue();

        assertEquals(List.of("risky", "clean"), queue.stream().map(AdminVerificationQueueItemDTO::id).toList());
        assertEquals(80, queue.getFirst().pendingVersion().scan().riskScore());
        assertEquals(2, queue.getFirst().pendingVersion().scan().newIssueCount());
    }

    @Test void scanningSiblingCannotHideFailedOrReadyVersions() {
        var queues=mock(ProjectReviewQueueService.class);
        var service=new ProjectReviewQueryService(mock(UserRepository.class),mock(ProjectService.class),queues,mock(ProjectListingQueryService.class));
        var project=queueProject("p","date",ScanStatus.SCANNING,"REVIEW",0,0);
        var scanning=project.getVersions().getFirst();
        var failed=queueProject("failed","date",ScanStatus.FAILED,"REVIEW",0,0).getVersions().getFirst();
        var ready=queueProject("ready","date",ScanStatus.SUSPICIOUS,"REVIEW",50,1).getVersions().getFirst();
        var approved=queueProject("approved","date",ScanStatus.CLEAN,"ALLOW",0,0).getVersions().getFirst();
        approved.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        project.setVersions(List.of(scanning,failed,ready,approved));when(queues.getVerificationQueue()).thenReturn(List.of(project));
        var result=service.getVerificationQueue();assertEquals(2,result.size());
        assertEquals(List.of("ready-version","failed-version"),result.stream().map(v->v.pendingVersion().id()).toList());
        assertEquals(List.of("p","p"),result.stream().map(AdminVerificationQueueItemDTO::id).toList());
    }

    @Test void pendingProjectWithApprovedVersionsRetainsContentReviewRow() {
        var queues=mock(ProjectReviewQueueService.class);
        var service=new ProjectReviewQueryService(mock(UserRepository.class),mock(ProjectService.class),queues,mock(ProjectListingQueryService.class));
        var project=queueProject("p","date",ScanStatus.CLEAN,"ALLOW",0,0);project.setStatus(ProjectStatus.PENDING);
        project.getVersions().getFirst().setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        when(queues.getVerificationQueue()).thenReturn(List.of(project));
        var result=service.getVerificationQueue();assertEquals(1,result.size());
        org.junit.jupiter.api.Assertions.assertNull(result.getFirst().pendingVersion());
    }

    private static Project queueProject(
            String id,
            String updatedAt,
            ScanStatus scanStatus,
            String verdict,
            int riskScore,
            int newIssueCount
    ) {
        ScanResult scan = new ScanResult();
        scan.setStatus(scanStatus);
        scan.setVerdict(verdict);
        scan.setRiskScore(riskScore);
        scan.setNewIssueCount(newIssueCount);

        ProjectVersion version = new ProjectVersion();
        version.setId(id + "-version");
        version.setVersionNumber("1.0.0");
        version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        version.setScanResult(scan);

        Project project = new Project();
        project.setId(id);
        project.setTitle(id);
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setUpdatedAt(updatedAt);
        project.setVersions(List.of(version));
        return project;
    }
}
