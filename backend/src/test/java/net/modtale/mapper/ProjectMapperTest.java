package net.modtale.mapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.modtale.model.dto.admin.AdminProjectVersionSummaryDTO;
import net.modtale.model.dto.project.ProjectCommentDTO;
import net.modtale.model.dto.project.ProjectDTO;
import net.modtale.model.dto.project.ProjectDependencyDTO;
import net.modtale.model.dto.project.ProjectMetaDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.dto.project.ProjectVersionSummaryDTO;
import net.modtale.model.project.Comment;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.user.ApiKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMapperTest {

    @Test
    void toSummaryDTOIncludesManagementFieldsOnlyWhenRequested() {
        Project project = baseProject();
        project.setCanEdit(true);
        project.setIsOwner(true);
        project.setVersions(List.of(version("v1")));

        ProjectSummaryDTO publicSummary = ProjectMapper.toSummaryDTO(project, false);
        ProjectSummaryDTO managementSummary = ProjectMapper.toSummaryDTO(project, true);

        assertNull(publicSummary.status());
        assertNull(publicSummary.canEdit());
        assertNull(publicSummary.isOwner());
        assertNull(publicSummary.versions());
        assertEquals("ItsNeil17", publicSummary.author());

        assertEquals(ProjectStatus.PENDING, managementSummary.status());
        assertEquals(Boolean.TRUE, managementSummary.canEdit());
        assertEquals(Boolean.TRUE, managementSummary.isOwner());
        assertEquals(1, managementSummary.versions().size());
        assertEquals("ItsNeil17", managementSummary.author());
    }

    @Test
    void toMetaDTOUsesSafeFallbacksForNullableFields() {
        Project project = new Project();
        project.setId("project-1");
        project.setTitle("LevelingCore");
        project.setAuthor("ItsNeil17");
        project.setClassification(ProjectClassification.PLUGIN);
        project.setDownloadCount(42);

        ProjectMetaDTO dto = ProjectMapper.toMetaDTO(project);

        assertEquals("LevelingCore", dto.title());
        assertEquals("", dto.description());
        assertEquals("", dto.icon());
        assertEquals("ItsNeil17", dto.author());
        assertEquals("", dto.repositoryUrl());
        assertEquals("project-1", dto.slug());
    }

    @Test
    void toDTOMapsCommentsVersionsAndVotesForFullResponses() {
        Project project = baseProject();
        project.setAbout("Deep project details");
        project.setChildProjectIds(List.of("child-1"));
        project.setGalleryImages(List.of("https://example.com/one.png"));
        project.setGalleryImageCaptions(Map.of("https://example.com/one.png", "Opening shot"));
        project.setGalleryCarouselEnabled(true);
        project.setProjectRoles(List.of(new Project.ProjectRole("role-1", "Editor", "#fff", Set.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA))));
        project.setTeamMembers(List.of(new Project.ProjectMember("user-1", "role-1")));
        project.setTeamInvites(List.of(new Project.ProjectMember("user-2", "role-1")));
        project.setComments(List.of(comment()));
        project.setVersions(List.of(version("v1")));

        ProjectDTO dto = ProjectMapper.toDTO(project, false, "user-1");

        assertEquals("Deep project details", dto.getAbout());
        assertEquals(List.of("child-1"), dto.getChildProjectIds());
        assertEquals(Map.of("https://example.com/one.png", "Opening shot"), dto.getGalleryImageCaptions());
        assertTrue(dto.isGalleryCarouselEnabled());
        assertEquals(1, dto.getComments().size());
        assertEquals(1, dto.getVersions().size());
        assertEquals(1, dto.getProjectRoles().size());
        assertEquals(1, dto.getTeamMembers().size());
        assertEquals(1, dto.getTeamInvites().size());
        assertTrue(dto.isCustomLicenseOpenSource());

        ProjectCommentDTO comment = dto.getComments().getFirst();
        assertEquals("Lock in, this one ships.", comment.content());
        assertEquals("up", comment.userVote());
        assertNotNull(comment.developerReply());
        assertEquals("ItsNeil17 says thanks.", comment.developerReply().content());
        assertEquals("down", comment.developerReply().userVote());
    }

    @Test
    void toDTOUsesEmptyListsWhenCommentsAndVersionsAreMissing() {
        Project project = baseProject();
        project.setComments(null);
        project.setVersions(null);

        ProjectDTO dto = ProjectMapper.toDTO(project, false, null);

        assertTrue(dto.getComments().isEmpty());
        assertTrue(dto.getVersions().isEmpty());
    }

    @Test
    void versionAndDependencyMappingsHonorOptionalReviewData() {
        ProjectVersion version = version("v2");
        ProjectVersionSummaryDTO withoutReview = ProjectMapper.toVersionSummaryDTO(version, false);
        ProjectVersionSummaryDTO withReview = ProjectMapper.toVersionSummaryDTO(version, true);
        AdminProjectVersionSummaryDTO adminVersion = ProjectMapper.toAdminVersionSummaryDTO(version);
        ProjectDependency dependency = new ProjectDependency(
                "modtale:core",
                "Core",
                "1.0.0",
                ProjectDependency.DependencyType.EMBEDDED
        );
        dependency.setIcon("/icons/core.png");
        dependency.setTitle("Core Display");
        dependency.setClassification(ProjectClassification.PLUGIN);
        dependency.setSlug("core");
        ProjectDependencyDTO dependencyDto = ProjectMapper.toDependencyDTO(dependency);

        assertNull(withoutReview.reviewStatus());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED, withReview.reviewStatus());
        assertEquals("Security review cleared", withReview.rejectionReason());
        assertNotNull(adminVersion.scanResult());
        assertEquals("modtale:core", dependencyDto.projectId());
        assertEquals(ProjectDependency.DependencyType.EMBEDDED, dependencyDto.dependencyType());
        assertEquals(ProjectDependency.Source.MODTALE, dependencyDto.source());
        assertEquals("/icons/core.png", dependencyDto.icon());
        assertEquals("Core Display", dependencyDto.title());
        assertEquals(ProjectClassification.PLUGIN, dependencyDto.classification());
        assertEquals("core", dependencyDto.slug());
        assertFalse(dependencyDto.isOptional());
        assertTrue(dependencyDto.isEmbedded());
        assertEquals("Lock in complete", ProjectMapper.toVersionDTO(version).getChangelog());
        assertEquals("Core", ProjectMapper.toVersionDTO(version).getDependencies().getFirst().projectTitle());
        assertEquals(List.of("modtale:legacy"), ProjectMapper.toVersionDTO(version).getIncompatibleProjectIds());
    }

    private static Project baseProject() {
        Project project = new Project();
        project.setId("project-1");
        project.setSlug("levelingcore");
        project.setTitle("LevelingCore");
        project.setDescription("Automation helpers");
        project.setAuthorId("author-1");
        project.setAuthor("ItsNeil17");
        project.setImageUrl("https://example.com/icon.png");
        project.setBannerUrl("https://example.com/banner.png");
        project.setClassification(ProjectClassification.PLUGIN);
        project.setCategories(List.of("Automation"));
        project.setTags(List.of("utility"));
        project.setDownloadCount(42);
        project.setFavoriteCount(7);
        project.setTrendScore(5);
        project.setRelevanceScore(10.5);
        project.setPopularScore(88.0);
        project.setRepositoryUrl("https://github.com/modtale/levelingcore");
        project.setUpdatedAt("2026-01-01");
        project.setCreatedAt("2025-12-01");
        project.setLicense("MIT");
        project.setCustomLicenseOpenSource(true);
        project.setLastTrendingNotification("2026-01-02");
        project.setLinks(Map.of("docs", "https://example.com/docs"));
        project.setTypes(List.of("SERVER"));
        project.setAllowModpacks(true);
        project.setAllowComments(true);
        project.setHmWikiEnabled(true);
        project.setHmWikiSlug("levelingcore");
        project.setStatus(ProjectStatus.PENDING);
        project.setExpiresAt("2026-02-01");
        return project;
    }

    private static Comment comment() {
        Comment comment = new Comment();
        comment.setId("comment-1");
        comment.setUserId("user-1");
        comment.setContent("Lock in, this one ships.");
        comment.setDate("2026-01-03T10:00:00");
        comment.setUpdatedAt("2026-01-04T10:00:00");
        comment.setUpvotes(Set.of("user-1", "user-2"));
        comment.setDownvotes(Set.of("user-3"));

        Comment.Reply reply = new Comment.Reply();
        reply.setUserId("author-1");
        reply.setContent("ItsNeil17 says thanks.");
        reply.setDate("2026-01-05T10:00:00");
        reply.setUpvotes(Set.of("user-2"));
        reply.setDownvotes(Set.of("user-1"));
        comment.setDeveloperReply(reply);

        return comment;
    }

    private static ProjectVersion version(String id) {
        ProjectVersion version = new ProjectVersion();
        version.setId(id);
        version.setVersionNumber("1.2.3");
        version.setGameVersions(List.of("1.0.0"));
        version.setFileUrl("https://example.com/file.jar");
        version.setDownloadCount(12);
        version.setReleaseDate("2026-01-01T10:00:00");
        version.setChangelog("Lock in complete");
        version.setDependencies(List.of(new ProjectDependency("modtale:core", "Core", "1.0.0")));
        version.setIncompatibleProjectIds(List.of("modtale:legacy"));
        version.setChannel(ProjectVersion.Channel.RELEASE);
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        version.setRejectionReason("Security review cleared");
        version.setScanResult(new ScanResult());
        return version;
    }
}
