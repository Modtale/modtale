package net.modtale.service.storage;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DownloadService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectService projectService;
    @Autowired private StorageService storageService;

    @Value("${app.limits.modpack-gen-per-hour:10}")
    private int modpackGenLimitPerHour;

    private final Map<String, Bucket> modpackGenBuckets = new ConcurrentHashMap<>();

    public byte[] generateBundleZip(Project mainProject, ProjectVersion mainVersion, List<String> selectedDependencies, User user) throws IOException {
        if (user != null) {
            Bucket bucket = modpackGenBuckets.computeIfAbsent(user.getId() + "_bundle", k -> Bucket.builder().addLimit(Bandwidth.classic(modpackGenLimitPerHour, Refill.greedy(modpackGenLimitPerHour, Duration.ofHours(1)))).build());
            if (!bucket.tryConsume(1)) throw new IllegalStateException("Bundle limit reached. Wait 1 hour.");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            if (mainVersion.getFileUrl() != null) {
                try {
                    byte[] mainData = storageService.download(mainVersion.getFileUrl());
                    String orig = mainVersion.getFileUrl().substring(mainVersion.getFileUrl().lastIndexOf('/') + 1);
                    if (orig.length() > 37 && orig.charAt(36) == '-') orig = orig.substring(37);
                    zos.putNextEntry(new ZipEntry(orig));
                    zos.write(mainData);
                    zos.closeEntry();
                } catch (Exception ignored) {}
            }

            if (mainVersion.getDependencies() != null) {
                for (ProjectDependency dep : mainVersion.getDependencies()) {
                    if (selectedDependencies != null && !selectedDependencies.contains(dep.getProjectId())) continue;
                    Project depProject = projectService.getRawProjectById(dep.getProjectId());
                    if (depProject == null) continue;
                    ProjectVersion depVer = depProject.getVersions().stream().filter(v -> v.getVersionNumber().equals(dep.getVersionNumber())).findFirst().orElse(null);

                    if (depVer != null && depVer.getFileUrl() != null) {
                        try {
                            byte[] fileData = storageService.download(depVer.getFileUrl());
                            String orig = depVer.getFileUrl().substring(depVer.getFileUrl().lastIndexOf('/') + 1);
                            if (orig.length() > 37 && orig.charAt(36) == '-') orig = orig.substring(37);
                            zos.putNextEntry(new ZipEntry(orig));
                            zos.write(fileData);
                            zos.closeEntry();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return baos.toByteArray();
    }
}