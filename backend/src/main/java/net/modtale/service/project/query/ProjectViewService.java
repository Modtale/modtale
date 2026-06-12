package net.modtale.service.project.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.modtale.model.dto.project.ProjectVersionChangelogDTO;
import net.modtale.model.project.Comment;
import net.modtale.model.project.Project;
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

    public Project getProjectPageByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return getPublicProjectPageByRouteKey(routeKey);
        }

        Project project = resolveViewerProjectPageByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null) return null;

        boolean privileged = accessControlService.hasEditPermission(project, viewer) || accessControlService.isAdmin(viewer);
        if (!privileged && !accessControlService.canReadProject(project, viewer)) {
            return null;
        }

        return prepareProjectForViewer(project, viewer, privileged);
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
    public Project getPublicProjectPageById(String id) {
        if (id == null || id.isBlank()) return null;
        Project project = projectRepository.findPublicPageDetailById(id).orElse(null);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        return prepareProjectForViewer(project, null, false);
    }

    @Cacheable(value = "projectDetails", key = "'public-page:' + #routeKey")
    public Project getPublicProjectPageByRouteKey(String routeKey) {
        Project project = resolvePublicProjectPageByRouteKey(routeKey);
        if (project == null || project.getDeletedAt() != null || !accessControlService.isPubliclyReadable(project)) return null;
        return prepareProjectForViewer(project, null, false);
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

    private Project resolvePublicProjectPageByRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String projectId = projectRouteService.extractProjectId(normalized);
            Project project = projectRepository.findPublicPageDetailById(projectId).orElse(null);
            if (project != null) return project;
            return projectRepository.findPublicPageDetailBySlug(normalized).orElse(null);
        }

        Project route = projectRepository.findPublicRouteBySlug(normalized).orElse(null);
        if (route != null && route.getId() != null) {
            Project project = projectRepository.findPublicPageDetailById(route.getId()).orElse(null);
            if (project != null) return project;
        }

        return projectRepository.findPublicPageDetailById(normalized).orElse(null);
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

    private Project resolveViewerProjectPageByRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) return null;

        String normalized = routeKey.trim();
        if (projectRouteService.hasExplicitProjectHandle(normalized)) {
            String projectId = projectRouteService.extractProjectId(normalized);
            Project project = projectRepository.findViewerPageDetailById(projectId).orElse(null);
            if (project != null) return project;
            return projectRepository.findViewerPageDetailBySlug(normalized).orElse(null);
        }

        Project route = projectRepository.findPublicRouteBySlug(normalized).orElse(null);
        if (route != null && route.getId() != null) {
            Project project = projectRepository.findViewerPageDetailById(route.getId()).orElse(null);
            if (project != null) return project;
        }

        Project project = projectRepository.findViewerPageDetailBySlug(normalized).orElse(null);
        if (project != null) return project;

        return projectRepository.findViewerPageDetailById(normalized).orElse(null);
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

    private Project prepareProjectForViewer(Project project, User viewer, boolean privileged) {
        populateAuthorName(project);

        if (project.getVersions() != null) {
            if (!privileged) {
                List<ProjectVersion> visibleVersions = project.getVersions().stream()
                        .filter(v -> v.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED)
                        .collect(Collectors.toList());
                project.setVersions(visibleVersions);
            }

            project.getVersions().forEach(v -> v.setScanResult(null));
        }

        if (!project.isAllowComments() && !privileged) project.setComments(new ArrayList<>());
        populateRelatedUsers(project);

        return project;
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
}
