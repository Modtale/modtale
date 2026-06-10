package net.modtale.service.project;

import net.modtale.model.project.Comment;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.AccessControlService;
import net.modtale.util.MongoIdUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectViewService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final AccessControlService accessControlService;

    public ProjectViewService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            AccessControlService accessControlService
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.accessControlService = accessControlService;
    }

    public Project getRawProjectById(String id) {
        if (id == null || id.isBlank()) return null;
        return projectRepository.findById(id).orElse(null);
    }

    public Project getProjectById(String id, User viewer) {
        if (viewer == null) {
            return getPublicProjectById(id);
        }

        Project project = getRawProjectById(id);
        if (project == null || project.getDeletedAt() != null) return null;

        boolean privileged = accessControlService.hasEditPermission(project, viewer) || accessControlService.isAdmin(viewer);
        boolean canReadHiddenProject = accessControlService.hasProjectPermission(project, viewer, "PROJECT_READ");
        if (!privileged && !isPubliclyVisible(project) && !canReadHiddenProject) {
            return null;
        }

        return prepareProjectForViewer(project, privileged);
    }

    public Project getPublicProjectById(String id) {
        Project project = getRawProjectById(id);
        if (project == null || project.getDeletedAt() != null || !isPubliclyVisible(project)) return null;
        return prepareProjectForViewer(project, false);
    }

    public Project getAdminProjectDetails(String id) {
        Project project = getRawProjectById(id);
        if (project == null) return null;
        if (project.getAuthorId() != null) {
            userRepository.findById(project.getAuthorId()).ifPresent(u -> project.setAuthor(u.getUsername()));
        }
        return project;
    }

    private Project prepareProjectForViewer(Project project, boolean privileged) {
        if (project.getAuthorId() != null) {
            userRepository.findById(project.getAuthorId()).ifPresent(u -> project.setAuthor(u.getUsername()));
        }

        if (!privileged && project.getVersions() != null) {
            List<ProjectVersion> visibleVersions = project.getVersions().stream()
                    .filter(v -> v.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED)
                    .collect(Collectors.toList());
            project.setVersions(visibleVersions);
            project.getVersions().forEach(v -> v.setScanResult(null));
        }

        if (!project.isAllowComments() && !privileged) project.setComments(new ArrayList<>());
        populateRelatedUsers(project);

        return project;
    }

    private boolean isPubliclyVisible(Project project) {
        return project != null && (
                project.getStatus() == ProjectStatus.PUBLISHED
                        || project.getStatus() == ProjectStatus.UNLISTED
                        || project.getStatus() == ProjectStatus.ARCHIVED
        );
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
                new Query(Criteria.where("_id").in(MongoIdUtils.expandIds(userIdsToFetch))),
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
}
