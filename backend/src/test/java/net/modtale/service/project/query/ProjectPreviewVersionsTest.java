package net.modtale.service.project.query;

import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProjectPreviewVersionsTest {
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ProjectResponseCacheService service = new ProjectResponseCacheService(null, null, null, null, mongoTemplate);

    @Test
    void batchesProjectedReadsAndReturnsOnlyLatestApprovedVersions() {
        Project project = new Project();
        project.setId("project-1");
        project.setVersions(List.of(
                version("old", "2026-01-01", ProjectVersion.ReviewStatus.APPROVED),
                version("pending", "2026-10-01", ProjectVersion.ReviewStatus.PENDING),
                version("latest", "2026-09-01", ProjectVersion.ReviewStatus.APPROVED),
                version("scheduled", "2026-11-01", ProjectVersion.ReviewStatus.SCHEDULED)
        ));
        project.getVersions().get(2).setIncompatibleProjectIds(List.of("other"));
        Project noApprovedVersions = new Project();
        noApprovedVersions.setId("pending-only");
        noApprovedVersions.setVersions(List.of(version("private", "2026-10-01", ProjectVersion.ReviewStatus.PENDING)));
        when(mongoTemplate.find(any(Query.class), eq(Project.class))).thenReturn(List.of(project, noApprovedVersions));

        var result = service.getPublicPreviewVersionsByIds(List.of(" project-1 ", "project-1", "pending-only"));
        assertEquals(1, result.size());
        assertEquals("latest", result.get("project-1").versionNumber());
        assertEquals(List.of("other"), result.get("project-1").incompatibleProjectIds());

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, times(1)).find(query.capture(), eq(Project.class));
        String criteria = query.getValue().getQueryObject().toJson();
        assertTrue(criteria.contains("PUBLISHED"));
        assertTrue(criteria.contains("UNLISTED"));
        assertTrue(criteria.contains("ARCHIVED"));
        assertTrue(criteria.contains("deletedAt"));
        assertEquals(5, query.getValue().getFieldsObject().size());
        assertEquals(1, query.getValue().getFieldsObject().get("versions.versionNumber"));
        assertFalse(query.getValue().getFieldsObject().containsKey("versions.dependencies"));
        assertFalse(query.getValue().getFieldsObject().containsKey("versions.changelog"));
    }

    @Test
    void avoidsDatabaseWorkForEmptyIds() {
        assertTrue(service.getPublicPreviewVersionsByIds(List.of(" ", "")).isEmpty());
        assertTrue(service.getPublicPreviewVersionsByIds(null).isEmpty());
        verifyNoInteractions(mongoTemplate);
    }

    private ProjectVersion version(String number, String date, ProjectVersion.ReviewStatus status) {
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber(number);
        version.setReleaseDate(date);
        version.setReviewStatus(status);
        return version;
    }
}
