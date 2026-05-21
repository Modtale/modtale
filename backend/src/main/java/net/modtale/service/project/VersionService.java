package net.modtale.service.project;

import net.modtale.model.dto.ManifestDependencySuggestion;
import net.modtale.model.project.*;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.storage.StorageService;
import net.modtale.service.security.FileValidationService;
import net.modtale.service.security.FileValidationService.ManifestDependency;
import net.modtale.service.security.FileValidationService.ManifestInspection;
import net.modtale.service.security.SanitizationService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.security.ScanService;
import net.modtale.service.communication.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VersionService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectService projectService;
    @Autowired private LifecycleService lifecycleService;
    @Autowired private ValidationService validationService;
    @Autowired private ScanService scanService;
    @Autowired private FileValidationService fileValidationService;
    @Autowired private SanitizationService sanitizer;
    @Autowired private StorageService storageService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private NotificationService notificationService;
    @Autowired private AccessControlService accessControlService;

    @Value("${app.limits.max-versions-per-day:5}") private int maxVersionsPerDay;
    @Value("${app.limits.max-versions-per-month:30}") private int maxVersionsPerMonth;

    public ProjectVersion findVersion(Project pack, String versionNumber) {
        if ("latest".equalsIgnoreCase(versionNumber)) return pack.getVersions().isEmpty() ? null : pack.getVersions().get(0);
        return pack.getVersions().stream().filter(v -> v.getVersionNumber().equalsIgnoreCase(versionNumber)).findFirst().orElse(null);
    }

    public Optional<ProjectVersion> getVersionByHash(String hash) {
        Query query = new Query(Criteria.where("versions.hash").is(hash));
        query.fields().include("versions.$");
        Project project = mongoTemplate.findOne(query, Project.class);
        return project != null && !project.getVersions().isEmpty() ? Optional.of(project.getVersions().get(0)) : Optional.empty();
    }

    public void updateVersion(String id, String versionId, List<String> projectIds, List<String> gameVersions, String changelog, ProjectVersion.Channel channel, User user) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, user, "VERSION_EDIT")) throw new SecurityException("Denied");
        lifecycleService.ensureEditable(project);

        ProjectVersion version = project.getVersions().stream().filter(v -> v.getId().equals(versionId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Not found"));
        if (gameVersions != null) {
            List<String> allowed = validationService.getAllowedGameVersions();
            for (String gv : gameVersions) {
                if (!allowed.contains(gv)) throw new IllegalArgumentException("Invalid game version: " + gv);
            }
            version.setGameVersions(gameVersions);
        }
        if (changelog != null) version.setChangelog(sanitizer.sanitizePlainText(changelog));
        if (channel != null) version.setChannel(channel);

        boolean isModpack = project.getClassification() == ProjectClassification.MODPACK;
        if (projectIds != null) {
            List<ProjectDependency> newDeps = new ArrayList<>();
            List<String> simpleIds = new ArrayList<>();
            for (String entry : projectIds) {
                String[] parts = entry.split(":");
                if (parts.length < 2) throw new IllegalArgumentException("Invalid format.");
                Project depProj = projectService.getRawProjectById(parts[0].trim());
                if (depProj == null || depProj.getVersions().stream().noneMatch(v -> v.getVersionNumber().equalsIgnoreCase(parts[1].trim()))) throw new IllegalArgumentException("Dep missing.");
                newDeps.add(new ProjectDependency(depProj.getId(), depProj.getTitle(), parts[1].trim(), !isModpack && parts.length >= 3 && "optional".equalsIgnoreCase(parts[2].trim())));
                simpleIds.add(depProj.getId());
            }
            if (isModpack && newDeps.size() < 2) throw new IllegalArgumentException("Min 2 deps.");
            if (isModpack && !newDeps.equals(version.getDependencies()) && version.getFileUrl() != null && version.getFileUrl().endsWith(".zip")) {
                try { storageService.deleteFile(version.getFileUrl()); } catch (Exception ignore) {}
                version.setFileUrl(null);
            }
            version.setDependencies(newDeps);
            if (isModpack && project.getVersions().get(0).getId().equals(versionId)) project.setModIds(simpleIds);
        }
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void addVersion(String id, String versionNumber, List<String> gameVersions, MultipartFile file, String changelog, List<String> projectIds, ProjectVersion.Channel channel, User user) throws Exception {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, user, "VERSION_CREATE")) throw new SecurityException("Denied");
        lifecycleService.ensureEditable(project);

        if (project.getStatus() == ProjectStatus.DRAFT && !project.getVersions().isEmpty()) throw new IllegalArgumentException("Drafts max 1 version.");

        LocalDate now = LocalDate.now();
        if (project.getVersions().stream().filter(v -> v.getReleaseDate().startsWith(now.toString())).count() >= maxVersionsPerDay) throw new IllegalStateException("Daily limit.");

        validationService.validateVersionNumber(versionNumber);
        if (project.getVersions().stream().anyMatch(v -> v.getVersionNumber().equalsIgnoreCase(versionNumber))) throw new IllegalArgumentException("Exists.");

        if (gameVersions != null) {
            List<String> allowed = validationService.getAllowedGameVersions();
            for (String gv : gameVersions) {
                if (!allowed.contains(gv)) throw new IllegalArgumentException("Invalid game version: " + gv);
            }
        }

        boolean isModpack = project.getClassification() == ProjectClassification.MODPACK;
        if (!isModpack) fileValidationService.validateProjectFile(file, project.getClassification().name());

        String filePath = null;
        String fileHash = null;

        if (file != null) {
            if (!isModpack) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream is = file.getInputStream()) {
                    byte[] buffer = new byte[8192]; int read;
                    while ((read = is.read(buffer)) > 0) md.update(buffer, 0, read);
                }
                StringBuilder hex = new StringBuilder();
                for (byte b : md.digest()) hex.append(String.format("%02x", b));
                fileHash = hex.toString();
                Query duplicateQuery = new Query(Criteria.where("versions.hash").is(fileHash).and("deletedAt").is(null));
                if (mongoTemplate.exists(duplicateQuery, Project.class)) {
                    throw new IllegalArgumentException("This file has already been uploaded to Modtale.");
                }
            }
            filePath = storageService.upload(file, "files/" + project.getClassification().name().toLowerCase());
        }

        ProjectVersion ver = new ProjectVersion();
        ver.setId(UUID.randomUUID().toString());
        ver.setVersionNumber(versionNumber);
        ver.setGameVersions(gameVersions);
        ver.setFileUrl(filePath);
        ver.setReleaseDate(LocalDateTime.now().toString());
        ver.setDownloadCount(0);
        ver.setChangelog(sanitizer.sanitizePlainText(changelog));
        ver.setChannel(channel);
        ver.setHash(fileHash);
        ver.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        ver.setDependencies(new ArrayList<>());

        List<String> simpleIds = new ArrayList<>();
        if (projectIds != null) {
            for (String entry : projectIds) {
                String[] parts = entry.split(":");
                Project depProj = projectService.getRawProjectById(parts[0].trim());
                if (depProj == null || depProj.getStatus() == ProjectStatus.DRAFT) throw new IllegalArgumentException("Dep missing.");
                ver.getDependencies().add(new ProjectDependency(depProj.getId(), depProj.getTitle(), parts[1].trim(), !isModpack && parts.length >= 3 && "optional".equalsIgnoreCase(parts[2].trim())));
                simpleIds.add(depProj.getId());
            }
        }
        if (isModpack) {
            if (ver.getDependencies().size() < 2) throw new IllegalArgumentException("Min 2 deps.");
            project.setModIds(simpleIds);
        }
        if (file != null && !isModpack) {
            ScanResult pending = new ScanResult(); pending.setStatus(ScanStatus.SCANNING);
            ver.setScanResult(pending);
        }

        project.getVersions().add(0, ver);
        projectRepository.save(project);
        projectService.evictProjectCache(project);

        if (file != null && !isModpack) scanService.performBackgroundScan(project.getId(), ver.getId(), filePath, file.getOriginalFilename(), false);
    }

    public List<ManifestDependencySuggestion> suggestManifestDependencies(String id, MultipartFile file, User user) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, user, "VERSION_CREATE")) throw new SecurityException("Denied");
        if (project.getClassification() != ProjectClassification.PLUGIN) return List.of();

        ManifestInspection manifest = fileValidationService.validateProjectFile(file, project.getClassification().name());
        if (manifest == null || manifest.getDependencies().isEmpty()) return List.of();

        Query query = new Query(Criteria.where("status").in(ProjectStatus.PUBLISHED, ProjectStatus.ARCHIVED)
                .and("deletedAt").is(null)
                .and("_id").ne(project.getId())
                .and("classification").is(ProjectClassification.PLUGIN));
        query.fields().include("title").include("slug").include("versions");
        List<Project> candidates = mongoTemplate.find(query, Project.class);

        List<ManifestDependencySuggestion> suggestions = new ArrayList<>();
        for (ManifestDependency dependency : manifest.getDependencies()) {
            Project bestProject = null;
            int bestScore = 0;

            for (Project candidate : candidates) {
                int score = scoreDependencyMatch(dependency, candidate);
                if (score > bestScore) {
                    bestScore = score;
                    bestProject = candidate;
                }
            }

            if (bestProject != null && bestScore >= 80) {
                ProjectVersion version = selectSuggestedVersion(bestProject, dependency.getVersion());
                if (version != null) {
                    suggestions.add(new ManifestDependencySuggestion(
                            dependency.getKey(),
                            dependency.getVersion(),
                            bestProject.getId(),
                            bestProject.getTitle(),
                            version.getVersionNumber(),
                            dependency.isOptional(),
                            bestScore
                    ));
                }
            }
        }

        return suggestions;
    }

    private int scoreDependencyMatch(ManifestDependency dependency, Project candidate) {
        String depName = normalizeDependencyName(dependency.getNamePart());
        String depKey = normalizeDependencyName(dependency.getKey());
        String title = normalizeDependencyName(candidate.getTitle());
        String slug = normalizeDependencyName(candidate.getSlug());

        if (depName.isEmpty()) return 0;

        if (!title.isEmpty() && depName.equals(title)) return 100;
        if (!slug.isEmpty() && depName.equals(slug)) return 95;
        if (!depKey.isEmpty() && ((!title.isEmpty() && depKey.equals(title)) || (!slug.isEmpty() && depKey.equals(slug)))) return 90;
        if (isStrongContainedMatch(depName, title)) return 85;
        if (isStrongContainedMatch(depName, slug)) return 82;
        if (isStrongFuzzyMatch(depName, title)) return 80;
        if (isStrongFuzzyMatch(depName, slug)) return 80;
        return 0;
    }

    private boolean isStrongContainedMatch(String dependencyName, String candidateName) {
        if (dependencyName.isEmpty() || candidateName.isEmpty()) return false;
        int shorter = Math.min(dependencyName.length(), candidateName.length());
        int longer = Math.max(dependencyName.length(), candidateName.length());
        if (shorter < 6) return false;
        if ((double) shorter / longer < 0.75) return false;
        return candidateName.contains(dependencyName) || dependencyName.contains(candidateName);
    }

    private boolean isStrongFuzzyMatch(String dependencyName, String candidateName) {
        if (dependencyName.isEmpty() || candidateName.isEmpty()) return false;
        int longer = Math.max(dependencyName.length(), candidateName.length());
        if (longer < 6) return false;
        int distance = levenshtein(dependencyName, candidateName);
        return distance <= 2 && ((double) distance / longer) <= 0.2;
    }

    private ProjectVersion selectSuggestedVersion(Project project, String requestedVersion) {
        if (project.getVersions() == null || project.getVersions().isEmpty()) return null;
        String exactVersion = requestedVersion == null ? "" : requestedVersion.replace(">=", "").replace("<=", "").replace(">", "").replace("<", "").replace("=", "").trim();
        if (!exactVersion.isEmpty() && !"*".equals(exactVersion)) {
            Optional<ProjectVersion> exact = project.getVersions().stream()
                    .filter(v -> v.getVersionNumber() != null && v.getVersionNumber().equalsIgnoreCase(exactVersion))
                    .findFirst();
            if (exact.isPresent()) return exact.get();
        }
        return project.getVersions().stream()
                .max(Comparator.comparing(ProjectVersion::getReleaseDate, Comparator.nullsLast(String::compareTo)))
                .orElse(project.getVersions().get(0));
    }

    private String normalizeDependencyName(String value) {
        if (value == null) return "";
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private int levenshtein(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return Integer.MAX_VALUE;
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    public void deleteVersion(String id, String versionId, User user) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, user, "VERSION_DELETE")) throw new SecurityException("Denied.");
        lifecycleService.ensureEditable(project);
        if (project.getStatus() != ProjectStatus.DRAFT && project.getVersions().size() <= 1) throw new IllegalArgumentException("Cannot delete only version.");

        if (project.getVersions().removeIf(v -> {
            if (v.getId().equals(versionId)) { if (v.getFileUrl() != null) storageService.deleteFile(v.getFileUrl()); return true; }
            return false;
        })) {
            projectRepository.save(project);
            projectService.evictProjectCache(project);
        } else throw new IllegalArgumentException("Not found.");
    }

    @Scheduled(fixedDelayString = "${app.scheduler.release-check:900000}")
    public void processScheduledReleases() {
        List<Project> projects = mongoTemplate.find(new Query(Criteria.where("versions").elemMatch(Criteria.where("reviewStatus").is("SCHEDULED").and("scheduledPublishDate").lte(LocalDateTime.now().toString()))), Project.class);
        for (Project project : projects) {
            boolean updated = false;
            List<String> released = new ArrayList<>();
            for (ProjectVersion v : project.getVersions()) {
                if (v.getReviewStatus() == ProjectVersion.ReviewStatus.SCHEDULED && v.getScheduledPublishDate() != null && LocalDateTime.parse(v.getScheduledPublishDate()).isBefore(LocalDateTime.now())) {
                    v.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
                    v.setScheduledPublishDate(null);
                    released.add(v.getVersionNumber());
                    updated = true;
                }
            }
            if (updated) {
                project.setUpdatedAt(LocalDateTime.now().toString());
                projectRepository.save(project);
                projectService.evictProjectCache(project);
                released.forEach(r -> { notificationService.notifyUpdates(project, r); notificationService.notifyDependents(project, r); });
            }
        }
    }
}
