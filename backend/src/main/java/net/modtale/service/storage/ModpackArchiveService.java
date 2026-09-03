package net.modtale.service.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.exception.StorageDownloadException;
import net.modtale.exception.StorageUploadException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ModpackTarget;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ModpackArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(ModpackArchiveService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final long DETERMINISTIC_ZIP_TIME = 0L;
    private static final String LEGACY_MANIFEST = "modpack.json";
    private static final String MANIFEST = "manifest.json";
    private static final String LOCKFILE = "modtale.lock.json";

    private final ProjectRepository projectRepository;
    private final DownloadArchiveSupport archiveSupport;

    ModpackArchiveService(ProjectRepository projectRepository, DownloadArchiveSupport archiveSupport) {
        this.projectRepository = projectRepository;
        this.archiveSupport = archiveSupport;
    }

    byte[] generateModpackZip(Project pack, ProjectVersion version) throws IOException {
        return generateModpackZip(pack, version, ModpackTarget.UNIVERSAL);
    }

    byte[] generateModpackZip(Project pack, ProjectVersion version, ModpackTarget target) throws IOException {
        ModpackTarget effectiveTarget = target == null ? ModpackTarget.UNIVERSAL : target;
        byte[] cachedArchive = effectiveTarget == ModpackTarget.UNIVERSAL ? downloadCachedArchive(pack, version) : null;
        if (cachedArchive != null) {
            return cachedArchive;
        }

        byte[] zipBytes = buildArchive(pack, version, effectiveTarget);
        if (effectiveTarget == ModpackTarget.UNIVERSAL) {
            cacheArchive(pack, version, zipBytes);
        }
        return zipBytes;
    }

    private byte[] downloadCachedArchive(Project pack, ProjectVersion version) {
        if (version.getFileUrl() == null) {
            return null;
        }

        try {
            byte[] cachedArchive = archiveSupport.download(version.getFileUrl());
            if (cachedArchive != null && cachedArchive.length > 0) {
                try {
                    ModpackArchiveValidator.validate(cachedArchive);
                    return cachedArchive;
                } catch (IOException ex) {
                    logger.warn("Cached modpack archive failed format or integrity validation for project={} version={}. Rebuilding archive.",
                            pack.getId(), version.getVersionNumber(), ex);
                    version.setFileUrl(null);
                    return null;
                }
            }
            logger.warn("Cached modpack archive was empty for project={} version={}. Rebuilding archive.",
                    pack.getId(), version.getVersionNumber());
            version.setFileUrl(null);
            return null;
        } catch (StorageDownloadException ex) {
            logger.warn("Cached modpack archive could not be downloaded for project={} version={}. Rebuilding archive.",
                    pack.getId(), version.getVersionNumber(), ex);
            version.setFileUrl(null);
            return null;
        }
    }

    private byte[] buildArchive(Project pack, ProjectVersion version, ModpackTarget target) throws IOException {
        List<PreparedDependency> preparedDependencies = prepareDependencies(version, target);
        List<ModpackOverrideArchive.OverrideFile> overrides = prepareOverrides(version, target);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeJsonEntry(zip, LEGACY_MANIFEST, legacyManifest(pack, version, target));
            writeJsonEntry(zip, MANIFEST, authorManifest(pack, version, target));
            writeJsonEntry(zip, LOCKFILE, lockfile(pack, version, preparedDependencies, overrides, target));
            for (PreparedDependency prepared : preparedDependencies) {
                if (prepared.bytes() != null) {
                    writeBinaryEntry(zip, prepared.path(), prepared.bytes());
                }
            }
            for (ModpackOverrideArchive.OverrideFile override : overrides) {
                writeBinaryEntry(zip, override.path(), override.bytes());
            }
        }
        byte[] archive = output.toByteArray();
        ModpackArchiveValidator.validate(archive);
        return archive;
    }

    private List<ModpackOverrideArchive.OverrideFile> prepareOverrides(ProjectVersion version, ModpackTarget target) throws IOException {
        String overrideFileUrl = trimToNull(version.getOverrideFileUrl());
        if (overrideFileUrl == null) return List.of();
        byte[] archive;
        try {
            archive = archiveSupport.download(overrideFileUrl);
        } catch (StorageDownloadException ex) {
            throw new IOException("Cannot download the modpack override bundle.", ex);
        }
        return ModpackOverrideArchive.read(new ByteArrayInputStream(archive)).stream()
                .filter(file -> target.includes(file.environment()))
                .toList();
    }

    private List<PreparedDependency> prepareDependencies(ProjectVersion version, ModpackTarget target) throws IOException {
        if (version.getDependencies() == null) {
            return List.of();
        }

        Set<String> archiveKeys = new HashSet<>();
        archiveKeys.add(LEGACY_MANIFEST.toLowerCase(Locale.ROOT));
        archiveKeys.add(MANIFEST.toLowerCase(Locale.ROOT));
        archiveKeys.add(LOCKFILE.toLowerCase(Locale.ROOT));
        List<PreparedDependency> prepared = new ArrayList<>();
        for (ProjectDependency dependency : version.getDependencies()) {
            if (!includedInTarget(dependency, target)) {
                continue;
            }
            prepared.add(dependency.isExternal()
                    ? prepareExternalDependency(dependency, archiveKeys)
                    : prepareModtaleDependency(dependency, archiveKeys));
        }
        return prepared;
    }

    private PreparedDependency prepareModtaleDependency(
            ProjectDependency dependency,
            Set<String> archiveKeys
    ) throws IOException {
        DownloadArchiveSupport.ResolvedDependency resolved = archiveSupport.resolveDependency(dependency);
        if (resolved == null || trimToNull(resolved.version().getFileUrl()) == null) {
            throw new IOException("Cannot resolve bundled Modtale dependency "
                    + dependencyLabel(dependency) + " at version " + dependency.getVersionNumber() + ".");
        }

        byte[] bytes;
        try {
            bytes = archiveSupport.download(resolved.version().getFileUrl());
        } catch (StorageDownloadException ex) {
            throw new IOException("Cannot download bundled Modtale dependency " + dependencyLabel(dependency) + ".", ex);
        }
        if (bytes == null || bytes.length == 0) {
            throw new IOException("Bundled Modtale dependency " + dependencyLabel(dependency) + " is empty.");
        }

        String filename = archiveSupport.extractOriginalFilename(resolved.version().getFileUrl());
        String path = uniqueArchiveEntryName(archiveKeys, sanitizeArchiveFilename(filename));
        return PreparedDependency.bundled(dependency, path, bytes);
    }

    private PreparedDependency prepareExternalDependency(
            ProjectDependency dependency,
            Set<String> archiveKeys
    ) {
        if (dependency.getSource() == ProjectDependency.Source.CURSEFORGE) {
            return PreparedDependency.reference(dependency);
        }

        String cachedFileUrl = trimToNull(dependency.getCachedFileUrl());
        if (cachedFileUrl == null) {
            return PreparedDependency.reference(dependency);
        }
        try {
            byte[] bytes = archiveSupport.download(cachedFileUrl);
            if (bytes == null || bytes.length == 0) {
                return PreparedDependency.reference(dependency);
            }
            String path = uniqueArchiveEntryName(archiveKeys, externalFilename(dependency));
            return PreparedDependency.bundled(dependency, path, bytes);
        } catch (StorageDownloadException ex) {
            logger.warn("Unable to include cached external dependency {} from {} in generated modpack archive.",
                    dependency.getProjectTitle(), cachedFileUrl, ex);
            return PreparedDependency.reference(dependency);
        }
    }

    private Map<String, Object> legacyManifest(Project pack, ProjectVersion version, ModpackTarget target) {
        Map<String, Object> manifest = packIdentity(pack, version);
        manifest.put("name", pack.getTitle());
        manifest.put("formatVersion", 1);
        manifest.put("game", "hytale");
        manifest.put("target", target.name());
        manifest.put("files", authorDependencies(version, target));
        return manifest;
    }

    private Map<String, Object> authorManifest(Project pack, ProjectVersion version, ModpackTarget target) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "modtale-pack");
        manifest.put("schemaVersion", 1);
        manifest.put("pack", packIdentity(pack, version));
        manifest.put("target", target.name());
        Map<String, Object> game = new LinkedHashMap<>();
        game.put("id", "hytale");
        game.put("versions", version.getGameVersions() == null ? List.of() : version.getGameVersions());
        manifest.put("game", game);
        manifest.put("dependencies", authorDependencies(version, target));
        return manifest;
    }

    private List<Map<String, Object>> authorDependencies(ProjectVersion version, ModpackTarget target) {
        if (version.getDependencies() == null) {
            return List.of();
        }
        List<Map<String, Object>> dependencies = new ArrayList<>();
        for (ProjectDependency dependency : version.getDependencies()) {
            if (!includedInTarget(dependency, target)) {
                continue;
            }
            Map<String, Object> item = baseDependency(dependency);
            if (dependency.isExternal()) {
                putIfPresent(item, "externalId", dependency.getExternalId());
                putIfPresent(item, "url", dependency.getExternalUrl());
                putIfPresent(item, "externalFileUrl", dependency.getExternalFileUrl());
                putIfPresent(item, "externalFileName", dependency.getExternalFileName());
                item.put("distribution", "REFERENCE_ONLY");
            }
            dependencies.add(item);
        }
        return dependencies;
    }

    private Map<String, Object> lockfile(
            Project pack,
            ProjectVersion version,
            List<PreparedDependency> preparedDependencies,
            List<ModpackOverrideArchive.OverrideFile> overrides,
            ModpackTarget target
    ) {
        Map<String, Object> lock = new LinkedHashMap<>();
        lock.put("format", "modtale-lock");
        lock.put("lockVersion", 1);
        lock.put("pack", packIdentity(pack, version));
        lock.put("target", target.name());
        lock.put("gameVersions", version.getGameVersions() == null ? List.of() : version.getGameVersions());

        List<Map<String, Object>> entries = new ArrayList<>();
        for (PreparedDependency prepared : preparedDependencies) {
            ProjectDependency dependency = prepared.dependency();
            Map<String, Object> item = baseDependency(dependency);
            item.put("distribution", prepared.bytes() == null ? "REFERENCE_ONLY" : "BUNDLED");
            if (prepared.bytes() != null) {
                item.put("path", prepared.path());
                item.put("size", prepared.bytes().length);
                item.put("hashes", Map.of("sha256", sha256(prepared.bytes())));
            } else {
                putIfPresent(item, "url", dependency.getExternalUrl());
                String externalFileUrl = trimToNull(dependency.getExternalFileUrl());
                if (externalFileUrl == null && dependency.getSource() == ProjectDependency.Source.CURSEFORGE) {
                    // Older CurseForge dependencies stored the exact file page only in externalUrl.
                    // Preserve those records while emitting the stricter v1 lockfile shape.
                    externalFileUrl = trimToNull(dependency.getExternalUrl());
                }
                putIfPresent(item, "fileUrl", externalFileUrl);
                if (dependency.getSource() == ProjectDependency.Source.CURSEFORGE) {
                    Map<String, Object> provider = new LinkedHashMap<>();
                    String externalId = trimToNull(dependency.getExternalId());
                    if (externalId != null && externalId.matches("\\d+")) {
                        provider.put("projectId", externalId);
                    } else {
                        putIfPresent(provider, "projectSlug", externalId);
                    }
                    putIfPresent(provider, "fileId", curseForgeFileId(dependency));
                    putIfPresent(provider, "fileName", dependency.getExternalFileName());
                    if (dependency.getExternalFileSize() != null && dependency.getExternalFileSize() > 0) {
                        provider.put("fileSize", dependency.getExternalFileSize());
                    }
                    if (dependency.getExternalFileHashes() != null && !dependency.getExternalFileHashes().isEmpty()) {
                        provider.put("hashes", new TreeMap<>(dependency.getExternalFileHashes()));
                    }
                    if (dependency.getExternalGameVersions() != null && !dependency.getExternalGameVersions().isEmpty()) {
                        provider.put("gameVersions", dependency.getExternalGameVersions());
                    }
                    if (dependency.getExternalFileStatus() != null) {
                        provider.put("fileStatus", dependency.getExternalFileStatus());
                    }
                    if (dependency.getExternalDistributionAllowed() != null) {
                        provider.put("distributionAllowed", dependency.getExternalDistributionAllowed());
                    }
                    item.put("provider", provider);
                }
            }
            entries.add(item);
        }
        lock.put("entries", entries);
        List<Map<String, Object>> overrideEntries = new ArrayList<>();
        for (ModpackOverrideArchive.OverrideFile override : overrides) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", override.path());
            item.put("environment", override.environment().name());
            item.put("size", override.bytes().length);
            item.put("hashes", Map.of("sha256", sha256(override.bytes())));
            overrideEntries.add(item);
        }
        lock.put("overrides", overrideEntries);
        return lock;
    }

    private boolean includedInTarget(ProjectDependency dependency, ModpackTarget target) {
        return target.includes(dependency.getEnvironment());
    }

    private Map<String, Object> packIdentity(Project pack, ProjectVersion version) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("packId", nullToEmpty(pack.getId()));
        identity.put("versionId", nullToEmpty(version.getId()));
        identity.put("versionNumber", nullToEmpty(version.getVersionNumber()));
        identity.put("name", nullToEmpty(pack.getTitle()));
        return identity;
    }

    private Map<String, Object> baseDependency(ProjectDependency dependency) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", nullToEmpty(dependency.getProjectId()));
        item.put("title", nullToEmpty(dependency.getProjectTitle()));
        item.put("version", nullToEmpty(dependency.getVersionNumber()));
        item.put("source", dependency.getSource().name());
        item.put("dependencyType", dependency.getDependencyType().name());
        item.put("environment", dependency.getEnvironment().name());
        return item;
    }

    private void writeJsonEntry(ZipOutputStream zip, String name, Map<String, Object> value) throws IOException {
        byte[] bytes = OBJECT_MAPPER.writeValueAsString(value).concat("\n").getBytes(StandardCharsets.UTF_8);
        writeBinaryEntry(zip, name, bytes);
    }

    private void writeBinaryEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(DETERMINISTIC_ZIP_TIME);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private String uniqueArchiveEntryName(Set<String> archiveKeys, String filename) {
        String candidate = filename;
        int counter = 2;
        while (!archiveKeys.add(candidate.toLowerCase(Locale.ROOT))) {
            int extensionStart = filename.lastIndexOf('.');
            candidate = extensionStart > 0
                    ? filename.substring(0, extensionStart) + "-" + counter + filename.substring(extensionStart)
                    : filename + "-" + counter;
            counter++;
        }
        return candidate;
    }

    private String externalFilename(ProjectDependency dependency) {
        String filename = trimToNull(dependency.getExternalFileName());
        if (filename == null && trimToNull(dependency.getCachedFileUrl()) != null) {
            filename = archiveSupport.extractOriginalFilename(dependency.getCachedFileUrl());
        }
        if (filename != null) {
            return sanitizeArchiveFilename(filename);
        }

        String title = dependency.getProjectTitle() == null ? dependency.getProjectId() : dependency.getProjectTitle();
        String version = dependency.getVersionNumber() == null ? "latest" : dependency.getVersionNumber();
        String base = (title + "-" + version)
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return (base.isBlank() ? "external-dependency" : base) + ".jar";
    }

    private String sanitizeArchiveFilename(String filename) {
        String leafName = nullToEmpty(filename).replace('\\', '/');
        leafName = leafName.substring(leafName.lastIndexOf('/') + 1);
        String sanitized = leafName
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "dependency.jar";
        }
        String lower = sanitized.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip") ? sanitized : sanitized + ".jar";
    }

    private String curseForgeFileId(ProjectDependency dependency) {
        String value = trimToNull(dependency.getExternalFileUrl());
        if (value == null) {
            value = trimToNull(dependency.getExternalUrl());
        }
        if (value == null) {
            return null;
        }
        try {
            String[] segments = URI.create(value).getPath().split("/");
            for (int index = 0; index < segments.length - 1; index++) {
                if ("files".equalsIgnoreCase(segments[index]) && segments[index + 1].matches("\\d+")) {
                    return segments[index + 1];
                }
            }
        } catch (IllegalArgumentException ignored) {
            // URLs are validated before archive generation. Legacy records may be malformed,
            // so omit the provider file ID instead of making archive generation fail.
        }
        return null;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", ex);
        }
    }

    private void cacheArchive(Project pack, ProjectVersion version, byte[] zipBytes) {
        try {
            String fileName = (pack.getSlug() != null && !pack.getSlug().isEmpty() ? pack.getSlug() : pack.getId())
                    + "-"
                    + version.getVersionNumber()
                    + ".zip";
            String uploadPath = archiveSupport.upload(
                    archiveSupport.newZipMultipartFile(fileName, zipBytes),
                    "modpacks"
            );

            version.setFileUrl(uploadPath);
            projectRepository.save(pack);
        } catch (StorageUploadException ex) {
            logger.warn("Generated modpack archive could not be cached for project={} version={}",
                    pack.getId(), version.getVersionNumber(), ex);
        }
    }

    private void putIfPresent(Map<String, Object> target, String name, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            target.put(name, normalized);
        }
    }

    private String dependencyLabel(ProjectDependency dependency) {
        String title = trimToNull(dependency.getProjectTitle());
        return title == null ? nullToEmpty(dependency.getProjectId()) : title;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record PreparedDependency(ProjectDependency dependency, String path, byte[] bytes) {
        private static PreparedDependency bundled(ProjectDependency dependency, String path, byte[] bytes) {
            return new PreparedDependency(dependency, path, bytes);
        }

        private static PreparedDependency reference(ProjectDependency dependency) {
            return new PreparedDependency(dependency, null, null);
        }
    }
}
