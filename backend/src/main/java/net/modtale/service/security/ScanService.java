package net.modtale.service.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.storage.StorageService;
import net.modtale.service.user.AccountService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.communication.WebhookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ScanService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private WardenClientService wardenService;
    @Autowired private StorageService storageService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ProjectService projectService;
    @Autowired private NotificationService notificationService;
    @Autowired private WebhookService webhookService;
    @Autowired private AccountService accountService;
    @Autowired private AccessControlService accessControlService;

    @Value("${app.limits.rescans-per-day:5}")
    private int rescanLimitPerDay;

    private final Map<String, Bucket> rescanBuckets = new ConcurrentHashMap<>();

    public void triggerRescan(String projectId, String versionId) {
        User user = accountService.getCurrentUser();
        if (user != null && !accessControlService.isAdmin(user)) {
            Bucket bucket = rescanBuckets.computeIfAbsent(user.getId(),
                    k -> Bucket.builder().addLimit(Bandwidth.classic(rescanLimitPerDay, Refill.greedy(rescanLimitPerDay, Duration.ofDays(1)))).build());
            if (!bucket.tryConsume(1)) throw new IllegalStateException("Daily rescan limit reached. Please wait 24 hours.");
        }

        Project project = projectService.getRawProjectById(projectId);
        if (project == null) throw new IllegalArgumentException("Project not found");

        ProjectVersion version = project.getVersions().stream()
                .filter(v -> v.getId().equals(versionId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Version not found"));

        if (version.getFileUrl() == null) throw new IllegalArgumentException("Version has no file to scan");

        ScanResult pending = new ScanResult();
        pending.setStatus(ScanStatus.SCANNING);
        version.setScanResult(pending);

        projectRepository.save(project);
        projectService.evictProjectCache(project);

        String originalFilename = version.getFileUrl().substring(version.getFileUrl().lastIndexOf('/') + 1);
        if (originalFilename.length() > 37 && originalFilename.charAt(36) == '-') originalFilename = originalFilename.substring(37);

        performBackgroundScan(projectId, versionId, version.getFileUrl(), originalFilename, true);
    }

    @Async
    public void performBackgroundScan(String projectId, String versionId, String filePath, String originalFilename, boolean isManualRescan) {
        try {
            byte[] fileBytes = storageService.download(filePath);
            ScanResult scanResult = wardenService.scanFile(fileBytes, originalFilename);

            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            Update update = new Update().set("versions.$.scanResult", scanResult);
            boolean approvedImmediately = false;

            if (scanResult.getStatus() == ScanStatus.CLEAN) {
                if (isManualRescan) {
                    update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.APPROVED);
                    update.set("updatedAt", LocalDateTime.now().toString());
                    approvedImmediately = true;
                } else {
                    long delayMinutes = ThreadLocalRandom.current().nextLong(30, 1440);
                    update.set("versions.$.scheduledPublishDate", LocalDateTime.now().plusMinutes(delayMinutes).toString());
                    update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.SCHEDULED);
                }
            }

            Query query = new Query(Criteria.where("_id").is(projectId).and("versions._id").is(versionId));
            mongoTemplate.updateFirst(query, update, Project.class);
            projectService.evictProjectCache(project);

            ProjectVersion ver = project.getVersions().stream().filter(v -> v.getId().equals(versionId)).findFirst().orElse(null);

            if (scanResult.getStatus() != ScanStatus.CLEAN) {
                webhookService.triggerAdminFlaggedVersionWebhook(project, ver, scanResult);
            }

            if (project.getStatus() == ProjectStatus.PENDING) {
                boolean stillScanning = project.getVersions().stream().anyMatch(v -> !v.getId().equals(versionId) && v.getScanResult() != null && v.getScanResult().getStatus() == ScanStatus.SCANNING);
                if (!stillScanning) webhookService.triggerAdminNewProjectWebhook(project);
            }

            if (approvedImmediately && project.getStatus() == ProjectStatus.PUBLISHED && ver != null) {
                notificationService.notifyUpdates(project, ver.getVersionNumber());
                notificationService.notifyDependents(project, ver.getVersionNumber());
            }

        } catch (Exception e) {
            ScanResult failed = new ScanResult();
            failed.setStatus(ScanStatus.FAILED);
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(projectId).and("versions._id").is(versionId)), new Update().set("versions.$.scanResult", failed), Project.class);
            Project project = projectRepository.findById(projectId).orElse(null);
            projectService.evictProjectCache(project);

            if (project != null) {
                ProjectVersion ver = project.getVersions().stream().filter(v -> v.getId().equals(versionId)).findFirst().orElse(null);
                webhookService.triggerAdminFlaggedVersionWebhook(project, ver, failed);
                if (project.getStatus() == ProjectStatus.PENDING && project.getVersions().stream().noneMatch(v -> !v.getId().equals(versionId) && v.getScanResult() != null && v.getScanResult().getStatus() == ScanStatus.SCANNING)) {
                    webhookService.triggerAdminNewProjectWebhook(project);
                }
            }
        }
    }
}