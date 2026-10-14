package net.modtale.service.admin.review;

import java.util.List;
import java.util.Optional;
import net.modtale.model.user.ApiKey;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectReviewQueryServiceTest {

    @Test
    void reviewDetailsCountsPublishedProjectsWithoutLoadingTheirDocuments() {
        UserRepository users = mock(UserRepository.class);
        ProjectService projects = mock(ProjectService.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProjectReviewQueryService queryService = new ProjectReviewQueryService(users, projects, mongo);
        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("author-1");
        User author = mock(User.class);
        when(author.getId()).thenReturn("author-1");
        when(author.getTier()).thenReturn(ApiKey.Tier.USER);
        when(projects.getRawProjectById("project-1")).thenReturn(project);
        when(users.findById("author-1")).thenReturn(Optional.of(author));
        when(mongo.count(any(Query.class), eq(Project.class))).thenReturn(42L);

        assertEquals(42L, queryService.getProjectReviewDetails("project-1").authorStats().totalProjects());
        ArgumentCaptor<Query> count = ArgumentCaptor.forClass(Query.class);
        verify(mongo).count(count.capture(), eq(Project.class));
        assertEquals("author-1", count.getValue().getQueryObject().getString("authorId"));
        assertEquals("PUBLISHED", count.getValue().getQueryObject().get("status").toString());
        assertTrue(count.getValue().getQueryObject().containsKey("deletedAt"));
        assertNull(count.getValue().getQueryObject().get("deletedAt"));
    }

    @Test
    void reviewDetailsKeepEveryVersionAndBindTheFullAuthoritativeProject() {
        UserRepository users = mock(UserRepository.class);
        ProjectService projects = mock(ProjectService.class);
        ProjectReviewQueryService queryService = new ProjectReviewQueryService(users, projects, mock(MongoTemplate.class));
        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("author-1");
        ProjectVersion first = new ProjectVersion();
        first.setId("first");
        first.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        ProjectVersion second = new ProjectVersion();
        second.setId("second");
        second.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        project.setVersions(List.of(first, second));
        when(projects.getRawProjectById("project-1")).thenReturn(project);
        when(users.findById("author-1")).thenReturn(Optional.empty());

        var details = queryService.getProjectReviewDetails("project-1");

        assertEquals(List.of("first", "second"), details.mod().versions().stream().map(version -> version.id()).toList());
        assertEquals(ProjectReviewSnapshot.token(project), details.mod().reviewToken());
        assertEquals(0L, details.authorStats().totalProjects());
        verify(projects).getRawProjectById("project-1");
    }
}
