package net.modtale.service.project.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.modtale.model.dto.project.ProjectVersionChangelogDTO;
import net.modtale.model.project.Comment;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.util.MongoIdUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class ProjectViewService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final AccessControlService accessControlService;
    private final ProjectRouteService projectRouteService;

    public ProjectViewService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            AccessControlService accessControlService,
            ProjectRouteService projectRouteService
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.accessControlService = accessControlService;
        this.projectRouteService = projectRouteService;
    }

    public Project getRawProjectById(String id) {
        if (id == null || id.isBlank()) return null;
        return projectRepository.findById(id).orElse(null);
    }

    public Project getRawProjectByRouteKey(String routeKey) {
        return resolveProjectByRouteKey(routeKey);
    }

    public Project getProjectById(String id, User viewer) {
        if (viewer == null) {
            return getPublicProjectById(id);
        }

        if (id == null || id.isBlank()) return null;
        Project project = projectRepository.findViewerDetailById(id).orElse(null);
        if (project == null || project.getDeletedAt() != null) return null;

        boolean privileged = accessControlService.hasEditPermission(project, viewer) || accessControlService.isAdmin(viewer);
        if (!privileged && !accessControlService.canReadProject(project, viewer)) {
            return null;
        }

        return prepareProjectForViewer(project, viewer, privileged);
    }

    public Project getProjectByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return getPublicProjectByRouteKey(routeKey);
        }

        Project project = resolveViewerProjectByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null) return null;

        boolean privileged = accessControlService.hasEditPermission(project, viewer) || accessControlService.isAdmin(viewer);
        if (!privileged && !accessControlService.canReadProject(project, viewer)) {
            return null;
        }

        return prepareProjectForViewer(project, viewer, privileged);
    }

    public Project getProjectDetailsByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return getPublicProjectDetailsByRouteKey(routeKey);
        }

        Project project = resolveViewerProjectDetailsByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null) return null;

        boolean privileged = accessControlService.hasEditPermission(project, viewer) || accessControlService.isAdmin(viewer);
        if (!privileged && !accessControlService.canReadProject(project, viewer)) {
            return null;
        }

        return prepareProjectForViewer(project, viewer, privileged);
    }

    public Project getProjectPageShellByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return getPublicProjectPageShellByRouteKey(routeKey);
        }

        Project project = resolveViewerProjectPageShellByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null) return null;

        boolean privileged = accessControlService.hasEditPermission(project, viewer) || accessControlService.isAdmin(viewer);
        if (!privileged && !accessControlService.canReadProject(project, viewer)) {
            return null;
        }

        populateAuthorName(project);
        return project;
    }

    public Project getProjectVersionsByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return getPublicProjectVersionsByRouteKey(routeKey);
        }

        Project project = resolveViewerProjectVersionsByRouteKey(routeKey);
        ProjectAccess access = resolveAccess(project, viewer);
        if (!access.canRead()) return null;

        filterVisibleVersions(project, access.privileged());
        populateDependencyMetadata(project, access.privileged());
        return project;
    }

    public Project getProjectCommentsByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return getPublicProjectCommentsByRouteKey(routeKey);
        }

        Project project = resolveViewerProjectCommentsByRouteKey(routeKey);
        ProjectAccess access = resolveAccess(project, viewer);
        if (!access.canRead()) return null;

        if (!project.isAllowComments() && !access.privileged()) {
            project.setComments(new ArrayList<>());
        }
        return project;
    }

    public Project getProjectGalleryByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return getPublicProjectGalleryByRouteKey(routeKey);
        }

        Project project = resolveViewerProjectGalleryByRouteKey(routeKey);
        ProjectAccess access = resolveAccess(project, viewer);
        return access.canRead() ? project : null;
    }

    public Project getProjectTeamByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return getPublicProjectTeamByRouteKey(routeKey);
        }

        Project project = resolveViewerProjectTeamByRouteKey(routeKey);
        ProjectAccess access = resolveAccess(project, viewer);
        if (!access.canRead()) return null;

        if (!access.privileged()) {
            project.setTeamInvites(new ArrayList<>());
        }
        populateRelatedUsers(project);
        return project;
    }

    @Cacheable(value = "projectDetails", key = "'public:' + #id")
    public Project getPublicProjectById(String id) {
        if (id == null || id.isBlank()) return null;
        Project project = projectRepository.findPublicDetailById(id).orElse(null);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        return prepareProjectForViewer(project, null, false);
    }

    @Cacheable(value = "projectDetails", key = "'public:' + #routeKey")
    public Project getPublicProjectByRouteKey(String routeKey) {
        Project project = resolvePublicProjectByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        return prepareProjectForViewer(project, null, false);
    }

    @Cacheable(value = "projectDetails", key = "'public-page:' + #id")
    public Project getPublicProjectDetailsById(String id) {
        if (id == null || id.isBlank()) return null;
        Project project = projectRepository.findPublicDetailsPayloadById(id).orElse(null);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        return prepareProjectForViewer(project, null, false);
    }

    @Cacheable(value = "projectDetails", key = "'public-page:' + #routeKey")
    public Project getPublicProjectDetailsByRouteKey(String routeKey) {
        Project project = resolvePublicProjectDetailsByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        return prepareProjectForViewer(project, null, false);
    }

    public Project getPublicProjectPageShellByRouteKey(String routeKey) {
        Project project = resolvePublicProjectPageShellByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        populateAuthorName(project);
        return project;
    }

    public Project getPublicProjectVersionsByRouteKey(String routeKey) {
        Project project = resolvePublicProjectVersionsByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        filterVisibleVersions(project, false);
        populateDependencyMetadata(project, false);
        return project;
    }

    public Project getPublicProjectCommentsByRouteKey(String routeKey) {
        Project project = resolvePublicProjectCommentsByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        if (!project.isAllowComments()) {
            project.setComments(new ArrayList<>());
        }
        return project;
    }

    public Project getPublicProjectGalleryByRouteKey(String routeKey) {
        Project project = resolvePublicProjectGalleryByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        return project;
    }

    public Project getPublicProjectTeamByRouteKey(String routeKey) {
        Project project = resolvePublicProjectTeamByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        populateRelatedUsers(project);
        return project;
    }

    @Cacheable(
            value = "projectVersionChangelogs",
            key = "'public:' + #routeKey",
            condition = "#viewer == null",
            unless = "#result == null"
    )
    public List<ProjectVersionChangelogDTO> getVersionChangelogsByRouteKey(String routeKey, User viewer) {
        Project project = resolveChangelogProjectByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null) return null;

        boolean privileged = viewer != null && (accessControlService.hasEditPermission(project, viewer) || accessControlService.isAdmin(viewer));
        if (!privileged && !accessControlService.canReadProject(project, viewer)) {
            return null;
        }

        if (project.getVersions() == null) {
            return List.of();
        }

        return project.getVersions().stream()
                .filter(version -> privileged || version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED)
                .map(version -> new ProjectVersionChangelogDTO(
                        version.getId(),
                        version.getVersionNumber(),
                        version.getChangelog()
                ))
                .collect(Collectors.toList());
    }

    public Project getAdminProjectDetails(String id) {
        Project project = getRawProjectById(id);
        if (project == null) return null;
        if (project.getAuthorId() != null) {
            userRepository.findById(project.getAuthorId()).ifPresent(u -> project.setAuthor(u.getUsername()));
        }
        return project;
    }

    public Project getAdminProjectDetailsByRouteKey(String routeKey) {
        Project project = resolveProjectByRouteKey(routeKey);
        if (project == null) return null;
        if (project.getAuthorId() != null) {
            userRepository.findById(project.getAuthorId()).ifPresent(u -> project.setAuthor(u.getUsername()));
        }
        return project;
    }

    private Project resolveProjectByRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String projectId = projectRouteService.extractProjectId(normalized);
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project != null) return project;
            return projectRepository.findBySlug(normalized).orElse(null);
        }

        Project project = projectRepository.findBySlug(normalized).orElse(null);
        if (project != null) return project;

        return projectRepository.findById(normalized).orElse(null);
    }

    private Project resolvePublicProjectByRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String projectId = projectRouteService.extractProjectId(normalized);
            Project project = projectRepository.findPublicDetailById(projectId).orElse(null);
            if (project != null) return project;
            return projectRepository.findPublicDetailBySlug(normalized).orElse(null);
        }

        Project project = projectRepository.findPublicDetailBySlug(normalized).orElse(null);
        if (project != null) return project;

        return projectRepository.findPublicDetailById(normalized).orElse(null);
    }

    private Project resolvePublicProjectDetailsByRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String projectId = projectRouteService.extractProjectId(normalized);
            Project project = projectRepository.findPublicDetailsPayloadById(projectId).orElse(null);
            if (project != null) return project;
            return projectRepository.findPublicDetailsPayloadBySlug(normalized).orElse(null);
        }

        Project route = projectRepository.findPublicRouteBySlug(normalized).orElse(null);
        if (route != null && route.getId() != null) {
            Project project = projectRepository.findPublicDetailsPayloadById(route.getId()).orElse(null);
            if (project != null) return project;
        }

        return projectRepository.findPublicDetailsPayloadById(normalized).orElse(null);
    }

    private Project resolveViewerProjectByRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String projectId = projectRouteService.extractProjectId(normalized);
            Project project = projectRepository.findViewerDetailById(projectId).orElse(null);
            if (project != null) return project;
            return projectRepository.findViewerDetailBySlug(normalized).orElse(null);
        }

        Project project = projectRepository.findViewerDetailBySlug(normalized).orElse(null);
        if (project != null) return project;

        return projectRepository.findViewerDetailById(normalized).orElse(null);
    }

    private Project resolveViewerProjectDetailsByRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String projectId = projectRouteService.extractProjectId(normalized);
            Project project = projectRepository.findViewerDetailsPayloadById(projectId).orElse(null);
            if (project != null) return project;
            return projectRepository.findViewerDetailsPayloadBySlug(normalized).orElse(null);
        }

        Project route = projectRepository.findPublicRouteBySlug(normalized).orElse(null);
        if (route != null && route.getId() != null) {
            Project project = projectRepository.findViewerDetailsPayloadById(route.getId()).orElse(null);
            if (project != null) return project;
        }

        Project project = projectRepository.findViewerDetailsPayloadBySlug(normalized).orElse(null);
        if (project != null) return project;

        return projectRepository.findViewerDetailsPayloadById(normalized).orElse(null);
    }

    private Project resolveChangelogProjectByRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String projectId = projectRouteService.extractProjectId(normalized);
            Project project = projectRepository.findChangelogsById(projectId).orElse(null);
            if (project != null) return project;
            return projectRepository.findChangelogsBySlug(normalized).orElse(null);
        }

        Project route = projectRepository.findPublicRouteBySlug(normalized).orElse(null);
        if (route != null && route.getId() != null) {
            Project project = projectRepository.findChangelogsById(route.getId()).orElse(null);
            if (project != null) return project;
        }

        Project project = projectRepository.findChangelogsBySlug(normalized).orElse(null);
        if (project != null) return project;

        return projectRepository.findChangelogsById(normalized).orElse(null);
    }

    private Project resolvePublicProjectPageShellByRouteKey(String routeKey) {
        return resolveProjectedProjectByRouteKey(
                routeKey,
                projectRepository::findPublicPageShellById,
                projectRepository::findPublicPageShellBySlug
        );
    }

    private Project resolveViewerProjectPageShellByRouteKey(String routeKey) {
        return resolveProjectedProjectByRouteKey(
                routeKey,
                projectRepository::findViewerPageShellById,
                projectRepository::findViewerPageShellBySlug
        );
    }

    private Project resolvePublicProjectVersionsByRouteKey(String routeKey) {
        return resolveProjectedProjectByRouteKey(
                routeKey,
                projectRepository::findPublicVersionsById,
                projectRepository::findPublicVersionsBySlug
        );
    }

    private Project resolveViewerProjectVersionsByRouteKey(String routeKey) {
        return resolveProjectedProjectByRouteKey(
                routeKey,
                projectRepository::findViewerVersionsById,
                projectRepository::findViewerVersionsBySlug
        );
    }

    private Project resolvePublicProjectCommentsByRouteKey(String routeKey) {
        return resolveProjectedProjectByRouteKey(
                routeKey,
                projectRepository::findPublicCommentsById,
                projectRepository::findPublicCommentsBySlug
        );
    }

    private Project resolveViewerProjectCommentsByRouteKey(String routeKey) {
        return resolveProjectedProjectByRouteKey(
                routeKey,
                projectRepository::findViewerCommentsById,
                projectRepository::findViewerCommentsBySlug
        );
    }

    private Project resolvePublicProjectGalleryByRouteKey(String routeKey) {
        return resolveProjectedProjectByRouteKey(
                routeKey,
                projectRepository::findPublicGalleryById,
                projectRepository::findPublicGalleryBySlug
        );
    }

    private Project resolveViewerProjectGalleryByRouteKey(String routeKey) {
        return resolveProjectedProjectByRouteKey(
                routeKey,
                projectRepository::findViewerGalleryById,
                projectRepository::findViewerGalleryBySlug
        );
    }

    private Project resolvePublicProjectTeamByRouteKey(String routeKey) {
        return resolveProjectedProjectByRouteKey(
                routeKey,
                projectRepository::findPublicTeamById,
                projectRepository::findPublicTeamBySlug
        );
    }

    private Project resolveViewerProjectTeamByRouteKey(String routeKey) {
        return resolveProjectedProjectByRouteKey(
                routeKey,
                projectRepository::findViewerTeamById,
                projectRepository::findViewerTeamBySlug
        );
    }

    private Project resolveProjectedProjectByRouteKey(
            String routeKey,
            Function<String, Optional<Project>> byId,
            Function<String, Optional<Project>> bySlug
    ) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String projectId = projectRouteService.extractProjectId(normalized);
            Project project = byId.apply(projectId).orElse(null);
            if (project != null) return project;
            return bySlug.apply(normalized).orElse(null);
        }

        Project project = bySlug.apply(normalized).orElse(null);
        if (project != null) return project;

        return byId.apply(normalized).orElse(null);
    }

    private ProjectAccess resolveAccess(Project project, User viewer) {
        if (project == null || project.getDeletedAt() != null) {
            return new ProjectAccess(false, false);
        }

        boolean privileged = viewer != null && (accessControlService.hasEditPermission(project, viewer) || accessControlService.isAdmin(viewer));
        boolean canRead = privileged || accessControlService.canReadProject(project, viewer);
        return new ProjectAccess(privileged, canRead);
    }

    private void filterVisibleVersions(Project project, boolean privileged) {
        if (project == null || project.getVersions() == null) return;

        if (!privileged) {
            List<ProjectVersion> visibleVersions = project.getVersions().stream()
                    .filter(v -> v.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED)
                    .collect(Collectors.toList());
            project.setVersions(visibleVersions);
        }

        project.getVersions().forEach(v -> v.setScanResult(null));
    }

    private Project prepareProjectForViewer(Project project, User viewer, boolean privileged) {
        populateAuthorName(project);

        if (project.getVersions() != null) {
            filterVisibleVersions(project, privileged);
            populateDependencyMetadata(project, privileged);
        }

        if (!project.isAllowComments() && !privileged) project.setComments(new ArrayList<>());
        populateRelatedUsers(project);

        return project;
    }

    private void populateDependencyMetadata(Project project, boolean privileged) {
        if (project == null || project.getVersions() == null) return;

        Set<String> dependencyIds = project.getVersions().stream()
                .filter(version -> version.getDependencies() != null)
                .flatMap(version -> version.getDependencies().stream())
                .map(ProjectDependency::getModId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (dependencyIds.isEmpty()) {
            return;
        }

        Criteria criteria = Criteria.where("_id").in(MongoIdUtils.expandIds(dependencyIds))
                .and("deletedAt").is(null);
        if (!privileged) {
            criteria.and("status").in("PUBLISHED", "UNLISTED", "ARCHIVED");
        }

        Query query = Query.query(criteria);
        query.fields()
                .include("_id")
                .include("slug")
                .include("title")
                .include("imageUrl")
                .include("classification")
                .include("status");

        Map<String, Project> dependencyProjects = mongoTemplate.find(query, Project.class).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (existing, replacement) -> existing));

        project.getVersions().stream()
                .filter(version -> version.getDependencies() != null)
                .flatMap(version -> version.getDependencies().stream())
                .forEach(dependency -> {
                    Project dependencyProject = dependencyProjects.get(dependency.getModId());
                    if (dependencyProject == null) {
                        dependency.setTitle(dependency.getModTitle());
                        return;
                    }

                    if (dependency.getModTitle() == null || dependency.getModTitle().isBlank()) {
                        dependency.setModTitle(dependencyProject.getTitle());
                    }
                    dependency.setTitle(dependencyProject.getTitle() != null ? dependencyProject.getTitle() : dependency.getModTitle());
                    dependency.setIcon(dependencyProject.getImageUrl() != null ? dependencyProject.getImageUrl() : "");
                    dependency.setClassification(dependencyProject.getClassification());
                    dependency.setSlug(dependencyProject.getSlug() != null && !dependencyProject.getSlug().isBlank()
                            ? dependencyProject.getSlug()
                            : dependencyProject.getId());
                });
    }

    private void populateAuthorName(Project project) {
        if (project.getAuthorId() == null || (project.getAuthor() != null && !project.getAuthor().isBlank())) {
            return;
        }

        Query query = new Query(Criteria.where("_id").in(MongoIdUtils.expandIds(List.of(project.getAuthorId()))));
        query.fields().include("_id").include("username");
        User author = mongoTemplate.findOne(query, User.class);
        if (author != null) {
            project.setAuthor(author.getUsername());
        }
    }

    private void populateRelatedUsers(Project project) {
        Set<String> userIdsToFetch = new HashSet<>();

        if (project.getTeamMembers() != null) project.getTeamMembers().forEach(m -> userIdsToFetch.add(m.getUserId()));
        if (project.getTeamInvites() != null) project.getTeamInvites().forEach(m -> userIdsToFetch.add(m.getUserId()));

        if (project.getComments() != null) {
            for (Comment c : project.getComments()) {
                if (c.getUserId() != null) userIdsToFetch.add(c.getUserId());
                if (c.getDeveloperReply() != null && c.getDeveloperReply().getUserId() != null) {
                    userIdsToFetch.add(c.getDeveloperReply().getUserId());
                }
            }
        }

        if (userIdsToFetch.isEmpty()) {
            return;
        }

        List<User> users = mongoTemplate.find(
                relatedUserQuery(userIdsToFetch),
                User.class
        );

        Map<String, User> userMapById = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        if (project.getTeamMembers() != null) {
            project.getTeamMembers().forEach(m -> {
                User u = userMapById.get(m.getUserId());
                if (u != null) {
                    m.setUsername(u.getUsername());
                    m.setAvatarUrl(u.getAvatarUrl());
                }
            });
        }
        if (project.getTeamInvites() != null) {
            project.getTeamInvites().forEach(m -> {
                User u = userMapById.get(m.getUserId());
                if (u != null) {
                    m.setUsername(u.getUsername());
                    m.setAvatarUrl(u.getAvatarUrl());
                }
            });
        }
    }

    private Query relatedUserQuery(Set<String> userIdsToFetch) {
        Query query = new Query(Criteria.where("_id").in(MongoIdUtils.expandIds(userIdsToFetch)));
        query.fields().include("_id").include("username").include("avatarUrl");
        return query;
    }

    private record ProjectAccess(boolean privileged, boolean canRead) {
    }
}
