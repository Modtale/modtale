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

    public byte[] generateModpackZip(Project pack, ProjectVersion version, User user) throws IOException {
        if (user != null) {
            Bucket bucket = modpackGenBuckets.computeIfAbsent(user.getId(),
                    k -> Bucket.builder().addLimit(Bandwidth.classic(modpackGenLimitPerHour, Refill.greedy(modpackGenLimitPerHour, Duration.ofHours(1)))).build());
            if (!bucket.tryConsume(1)) {
                throw new IllegalStateException("Modpack generation limit reached. Please wait a while before trying again.");
            }
        }

        if (version.getFileUrl() != null) {
            try {
                return storageService.download(version.getFileUrl());
            } catch (Exception e) {
                version.setFileUrl(null);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry readme = new ZipEntry("modpack.json");
            zos.putNextEntry(readme);
            StringBuilder json = new StringBuilder("{\n  \"name\": \"" + pack.getTitle() + "\",\n  \"files\": [\n");

            if (version.getDependencies() != null) {
                for (int i = 0; i < version.getDependencies().size(); i++) {
                    ProjectDependency dep = version.getDependencies().get(i);
                    json.append("    { \"id\": \"").append(dep.getModId())
                            .append("\", \"version\": \"").append(dep.getVersionNumber()).append("\" }");
                    if (i < version.getDependencies().size() - 1) json.append(",");
                    json.append("\n");
                }
            }
            json.append("  ]\n}");
            zos.write(json.toString().getBytes());
            zos.closeEntry();

            if (version.getDependencies() != null) {
                for (ProjectDependency dep : version.getDependencies()) {
                    Project depProj = projectService.getRawProjectById(dep.getModId());
                    if (depProj == null) continue;

                    ProjectVersion depVer = depProj.getVersions().stream()
                            .filter(v -> v.getVersionNumber().equals(dep.getVersionNumber()))
                            .findFirst()
                            .orElse(null);

                    if (depVer != null && depVer.getFileUrl() != null) {
                        try {
                            byte[] fileData = storageService.download(depVer.getFileUrl());

                            String folder = depProj.getClassification() != null && "PLUGIN".equals(depProj.getClassification().name()) ? "plugins/" : "asset-packs/";
                            String originalFilename = depVer.getFileUrl().substring(depVer.getFileUrl().lastIndexOf('/') + 1);
                            if (originalFilename.length() > 37 && originalFilename.charAt(36) == '-') {
                                originalFilename = originalFilename.substring(37);
                            }

                            ZipEntry entry = new ZipEntry(folder + originalFilename);
                            zos.putNextEntry(entry);
                            zos.write(fileData);
                            zos.closeEntry();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        byte[] zipBytes = baos.toByteArray();

        try {
            String fileName = (pack.getSlug() != null && !pack.getSlug().isEmpty() ? pack.getSlug() : pack.getId()) + "-" + version.getVersionNumber() + ".zip";
            org.springframework.web.multipart.MultipartFile multipart = new InMemoryMultipartFile(fileName, zipBytes);
            String uploadPath = storageService.upload(multipart, "modpacks");

            version.setFileUrl(uploadPath);
            projectRepository.save(pack);
        } catch (Exception ignored) {}

        return zipBytes;
    }

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
                    if (dep.isEmbedded()) continue;
                    if (selectedDependencies != null && !selectedDependencies.contains(dep.getModId())) continue;
                    Project depProject = projectService.getRawProjectById(dep.getModId());
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

    private static class InMemoryMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final String name;
        private final byte[] content;

        public InMemoryMultipartFile(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "application/zip"; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() throws IOException { return content; }
        @Override public java.io.InputStream getInputStream() throws IOException { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException { org.springframework.util.FileCopyUtils.copy(content, dest); }
    }
}
