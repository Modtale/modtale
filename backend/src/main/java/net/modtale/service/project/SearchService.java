package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class SearchService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountService accountService;
    @Autowired private MongoTemplate mongoTemplate;

    public Page<Project> searchProjects(List<String> tags, String search, int page, int size, String sortBy, String gameVersion, String contentType, Integer minDownloads, Integer minFavorites, String viewCategory, String dateRange, String authorId) {
        if ("Favorites".equals(viewCategory)) {
            User currentUser = accountService.getCurrentUser();
            List<String> likedIds = (currentUser != null && currentUser.getLikedModIds() != null) ? currentUser.getLikedModIds() : new ArrayList<>();
            return projectRepository.findFavorites(likedIds, search != null ? search : "", PageRequest.of(page, size, Sort.by("title")));
        }

        Sort sort = switch (sortBy != null ? sortBy : "relevance") {
            case "downloads" -> Sort.by("downloadCount").descending();
            case "updated" -> Sort.by("updatedAt").descending();
            case "new", "newest" -> Sort.by("createdAt").descending();
            case "favorites" -> Sort.by("favoriteCount").descending();
            default -> Sort.unsorted();
        };

        LocalDate dateCutoff = null;
        if (dateRange != null && !dateRange.equals("all") && !dateRange.isEmpty()) {
            try {
                if (dateRange.equals("7d")) dateCutoff = LocalDate.now().minusDays(7);
                else if (dateRange.equals("30d")) dateCutoff = LocalDate.now().minusDays(30);
                else if (dateRange.equals("90d")) dateCutoff = LocalDate.now().minusDays(90);
                else if (dateRange.equals("1y")) dateCutoff = LocalDate.now().minusYears(1);
                else dateCutoff = LocalDate.parse(dateRange.substring(0, 10));
            } catch (Exception ignored) {}
        }

        User currentUser = accountService.getCurrentUser();
        String resolvedAuthorId = authorId;
        if (resolvedAuthorId != null && !resolvedAuthorId.trim().isEmpty()) {
            Optional<User> resolvedAuthor = userRepository.findByUsernameIgnoreCase(resolvedAuthorId);
            if (resolvedAuthor.isPresent()) {
                resolvedAuthorId = resolvedAuthor.get().getId();
            }
        }
        Page<Project> results = projectRepository.searchProjects(
                search, tags, gameVersion, contentType, minDownloads, minFavorites, PageRequest.of(page, size, sort),
                currentUser != null ? currentUser.getId() : null, sortBy, viewCategory, dateCutoff, resolvedAuthorId
        );

        if (results.hasContent()) {
            results.getContent().forEach(p -> {
                if (p.getVersions() != null) p.getVersions().forEach(v -> v.setScanResult(null));
                if (p.getAuthor() == null && p.getAuthorId() != null) {
                    userRepository.findById(p.getAuthorId()).ifPresent(u -> p.setAuthor(u.getUsername()));
                }
            });
        }
        return results;
    }

    public Page<Project> searchDeletedProjects(String query, Pageable pageable) {
        return projectRepository.searchDeletedProjects(query, pageable);
    }

    public Page<Project> getCreatorProjects(String userId, Pageable pageable) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return Page.empty();
        Page<Project> results = projectRepository.findByAuthorIdAndStatusExact(u.getId(), ProjectStatus.PUBLISHED, pageable);
        if (results.hasContent()) results.getContent().forEach(p -> {
            if (p.getVersions() != null) p.getVersions().forEach(v -> v.setScanResult(null));
            p.setAuthor(u.getUsername());
        });
        return results;
    }

    public Page<Project> getPrivilegedCreatorProjects(String userId, Pageable pageable) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return Page.empty();
        Page<Project> results = projectRepository.findByAuthorId(u.getId(), pageable);
        if (results.hasContent()) results.getContent().forEach(p -> p.setAuthor(u.getUsername()));
        return results;
    }

    public Page<Project> getContributedProjects(String userId, Pageable pageable) {
        Query query = new Query(Criteria.where("teamMembers.userId").is(userId));
        long count = mongoTemplate.count(query, Project.class);
        List<Project> projects = mongoTemplate.find(query.with(pageable), Project.class);
        for (Project p : projects) {
            if (p.getVersions() != null) p.getVersions().forEach(v -> v.setScanResult(null));
            if (p.getAuthorId() != null && p.getAuthor() == null) {
                userRepository.findById(p.getAuthorId()).ifPresent(u -> p.setAuthor(u.getUsername()));
            }
        }
        return new org.springframework.data.domain.PageImpl<>(projects, pageable, count);
    }

    public List<Project> getPublishedProjects() {
        return projectRepository.findAllPublished();
    }

    public List<Project> getVerificationQueue() {
        Query pendingProjectsQuery = new Query(Criteria.where("status").is(ProjectStatus.PENDING));
        pendingProjectsQuery.fields().exclude("about", "comments", "galleryImages");
        List<Project> pendingProjects = mongoTemplate.find(pendingProjectsQuery, Project.class);

        Query pendingVersionsQuery = new Query(Criteria.where("status").is(ProjectStatus.PUBLISHED).and("versions.reviewStatus").is("PENDING"));
        pendingVersionsQuery.fields().exclude("about", "comments", "galleryImages");
        List<Project> pendingVersions = mongoTemplate.find(pendingVersionsQuery, Project.class);

        Set<Project> combined = new HashSet<>(pendingProjects);
        combined.addAll(pendingVersions);

        List<Project> result = new ArrayList<>(combined.stream()
                .filter(this::hasReviewReadyVersion)
                .toList());

        result.sort(Comparator.comparing(a -> a.getUpdatedAt() == null ? "" : a.getUpdatedAt()));
        return result;
    }

    private boolean hasReviewReadyVersion(Project project) {
        if (project == null) {
            return false;
        }

        List<ProjectVersion> versions = project.getVersions();
        if (versions == null || versions.isEmpty()) {
            return project.getStatus() == ProjectStatus.PENDING;
        }

        boolean hasScanningVersion = versions.stream().anyMatch(version ->
                version != null
                        && version.getScanResult() != null
                        && version.getScanResult().getStatus() == ScanStatus.SCANNING
        );

        if (hasScanningVersion) {
            return false;
        }

        if (project.getStatus() == ProjectStatus.PENDING) {
            return true;
        }

        return versions.stream().anyMatch(version -> {
            if (version == null || version.getReviewStatus() != ProjectVersion.ReviewStatus.PENDING) {
                return false;
            }
            if (version.getScanResult() == null) {
                return true;
            }
            return version.getScanResult().getStatus() != ScanStatus.SCANNING;
        });
    }
}
