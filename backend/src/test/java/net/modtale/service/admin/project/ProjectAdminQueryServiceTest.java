package net.modtale.service.admin.project;

import java.util.List;
import java.util.Map;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.service.admin.audit.AdminAuditLogger;
import net.modtale.service.admin.review.ProjectReviewPersistence;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.query.SearchService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectAdminQueryServiceTest {
    private final ProjectReviewPersistence writes = mock(ProjectReviewPersistence.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final AdminAuditLogger audit = mock(AdminAuditLogger.class);
    private final ProjectAdminQueryService service = new ProjectAdminQueryService(writes, projects,
            mock(SearchService.class), audit);

    private ProjectReviewPersistence.Snapshot snapshot() {
        var project = new Project();
        project.setId("canonical");
        project.setTitle("Old title");
        project.setClassification(ProjectClassification.PLUGIN);
        var snapshot = new ProjectReviewPersistence.Snapshot(new Document(), project);
        when(writes.capture("canonical", "token")).thenReturn(snapshot);
        return snapshot;
    }

    @Test void repairConflictDoesNotInvalidateOrAudit() {
        var snapshot = snapshot();
        var metadata = Map.<String, Object>of("title", "New title");
        when(writes.applyMetadataRepair(snapshot, metadata)).thenReturn(false);
        assertThrows(ResponseStatusException.class,
                () -> service.updateRawProject("admin", "canonical", metadata, "token"));
        verifyNoInteractions(projects, audit);
    }

    @Test void successfulRepairInvalidatesBothRoutesOnlyAfterConditionalWrite() {
        var snapshot = snapshot();
        var metadata = Map.<String, Object>of("title", "New title");
        when(writes.applyMetadataRepair(snapshot, metadata)).thenReturn(true);
        service.updateRawProject("admin", "canonical", metadata, "token");
        var order = inOrder(writes, projects, audit);
        order.verify(writes).capture("canonical", "token");
        order.verify(writes).applyMetadataRepair(snapshot, metadata);
        order.verify(projects).evictProjectDetailsCaches(argThat(routes -> routes.size() == 2
                && routes.contains(snapshot.project())
                && routes.stream().anyMatch(route -> "New title".equals(route.getTitle())
                    && "canonical".equals(route.getId())
                    && route.getClassification() == ProjectClassification.PLUGIN)), eq(List.of()));
        order.verify(audit).logAction("admin", "RAW_UPDATE_PROJECT", "canonical", "PROJECT",
                "Repaired metadata fields: title");
        assertEquals("Old title", snapshot.project().getTitle());
        verify(projects, never()).evictProjectCache(any());
    }

    @Test void repairDeniesAuthorityFieldsBeforeCapturingOrWriting() {
        assertThrows(ResponseStatusException.class,
                () -> service.updateRawProject("admin", "canonical", Map.of("status", "PUBLISHED"), "token"));
        verifyNoInteractions(writes, projects, audit);
    }
}
