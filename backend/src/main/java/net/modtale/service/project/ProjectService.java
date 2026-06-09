package net.modtale.service.project;

import net.modtale.model.project.Comment;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private CacheManager cacheManager;

    @Lazy
    @Autowired private AccountService accountService;

    @Lazy
    @Autowired private AccessControlService AccessControlService;

    public void evictProjectCache(Project project) {
        if (project == null) return;
        Cache cache = cacheManager.getCache("projectDetails");
        if (cache != null) {
            if (project.getId() != null) cache.evict(project.getId());
            String routeHandle = buildProjectHandle(project);
            if (routeHandle != null) cache.evict(routeHandle);
            if (project.getSlug() != null) cache.evict(project.getSlug());
        }
    }

    public String extractId(String slugOrId) {
        if (slugOrId == null) return null;
        String clean = slugOrId.replaceAll("\\.(png|jpg|jpeg)$", "");
        int separatorIndex = clean.lastIndexOf('~');
        if (separatorIndex >= 0 && separatorIndex < clean.length() - 1) {
            return clean.substring(separatorIndex + 1);
        }
        if (clean.length() >= 36) {
            String possibleId = clean.substring(clean.length() - 36);
            if (possibleId.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                return possibleId;
            }
        }
        return clean;
    }

    public Project getRawProjectById(String identifier) {
        String extracted = extractId(identifier);
        if (extracted == null) return null;
        return projectRepository.findById(extracted).orElse(null);
    }

    @Cacheable(value = "projectDetails", key = "#identifier")
    public Project getProjectById(String identifier) {
        Project project = getRawProjectById(identifier);
        if (project == null || project.getDeletedAt() != null) return null;

        if (project.getAuthorId() != null) {
            userRepository.findById(project.getAuthorId()).ifPresent(u -> project.setAuthor(u.getUsername()));
        }

        User currentUser = accountService.getCurrentUser();
        boolean isPrivileged = AccessControlService.hasEditPermission(project, currentUser) || AccessControlService.isAdmin(currentUser);

        if (!isPrivileged && project.getVersions() != null) {
            List<ProjectVersion> visibleVersions = project.getVersions().stream()
                    .filter(v -> v.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED)
                    .collect(Collectors.toList());
            project.setVersions(visibleVersions);
            project.getVersions().forEach(v -> v.setScanResult(null));
        }

        if (!project.isAllowComments() && !isPrivileged) project.setComments(new ArrayList<>());
        populateRelatedUsers(project);

        return project;
    }

    public Project getAdminProjectDetails(String identifier) {
        Project project = getRawProjectById(identifier);
        if (project == null) return null;
        if (project.getAuthorId() != null) {
            userRepository.findById(project.getAuthorId()).ifPresent(u -> project.setAuthor(u.getUsername()));
        }
        return project;
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

        if (!userIdsToFetch.isEmpty()) {
            List<User> users = new ArrayList<>();
            if (!userIdsToFetch.isEmpty()) users.addAll(mongoTemplate.find(new Query(Criteria.where("_id").in(userIdsToFetch)), User.class));

            Map<String, User> userMapById = users.stream().collect(Collectors.toMap(User::getId, u -> u));

            if (project.getTeamMembers() != null) {
                project.getTeamMembers().forEach(m -> {
                    User u = userMapById.get(m.getUserId());
                    if (u != null) { m.setUsername(u.getUsername()); m.setAvatarUrl(u.getAvatarUrl()); }
                });
            }
            if (project.getTeamInvites() != null) {
                project.getTeamInvites().forEach(m -> {
                    User u = userMapById.get(m.getUserId());
                    if (u != null) { m.setUsername(u.getUsername()); m.setAvatarUrl(u.getAvatarUrl()); }
                });
            }
        }
    }

    public String getProjectLink(Project project) {
        String handle = buildProjectHandle(project);
        if ("MODPACK".equals(project.getClassification().name())) return "/modpack/" + handle;
        if ("SAVE".equals(project.getClassification().name())) return "/world/" + handle;
        return "/mod/" + handle;
    }

    private String buildProjectHandle(Project project) {
        if (project == null || project.getId() == null || project.getId().isBlank()) return null;
        String base = project.getSlug();
        if (base == null || base.isBlank()) {
            base = createSlug(project.getTitle());
        }
        if (base != null && project.getId() != null) {
            if (base.equals(project.getId())) return project.getId();
            if (base.endsWith("~" + project.getId())) base = base.substring(0, base.length() - project.getId().length() - 1);
            else if (base.endsWith("-" + project.getId())) base = base.substring(0, base.length() - project.getId().length() - 1);
        }
        if (base == null || base.isBlank()) return project.getId();
        return base + "~" + project.getId();
    }

    private String createSlug(String title) {
        if (title == null) return null;
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 30) slug = slug.substring(0, 30);
        return slug;
    }
}
