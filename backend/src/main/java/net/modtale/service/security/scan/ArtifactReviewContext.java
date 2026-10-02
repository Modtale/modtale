package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ProjectClassification;
import java.security.*;
import java.util.*;

public final class ArtifactReviewContext {
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    private ArtifactReviewContext() {}
    public static String fingerprint(ProjectVersion version) {
        if (version == null || hasSupplementalContent(version)) return null;
        try {
            List<String> dependencies = new ArrayList<>();
            if (version.getDependencies() != null) for (var dependency : version.getDependencies()) {
                if (dependency == null || dependency.isExternal() || dependency.getVersionNumber() == null
                        || dependency.getVersionNumber().isBlank() || dependency.getProjectId() == null) return null;
                dependencies.add(MAPPER.writeValueAsString(Arrays.asList(dependency.getProjectId(),
                        dependency.getVersionNumber(), dependency.getDependencyType().name(), dependency.getSource().name())));
            }
            Collections.sort(dependencies);
            List<String> games = new ArrayList<>(version.getGameVersions() == null ? List.of() : version.getGameVersions());
            if (games.stream().anyMatch(Objects::isNull)) return null;
            Collections.sort(games);
            List<String> incompatible = canonicalIncompatible(version);
            if (incompatible == null) return null;
            byte[] canonical = MAPPER.writeValueAsBytes(Arrays.asList("artifact-context-2", games, dependencies, incompatible,
                    version.getManifestId(), version.getManifestVersion()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception invalidContext) {
            return null;
        }
    }
    /** Describes only the fields bound by fingerprint; callers must separately establish approval lineage. */
    public static List<String> changedFields(ProjectVersion baseline, ProjectVersion current) {
        if (fingerprint(baseline) == null || fingerprint(current) == null) return List.of();
        var changed = new ArrayList<String>();
        if (!canonicalGames(baseline).equals(canonicalGames(current))) changed.add("GAME_VERSIONS");
        if (!canonicalDependencies(baseline).equals(canonicalDependencies(current))) changed.add("DEPENDENCIES");
        if (!Objects.equals(canonicalIncompatible(baseline), canonicalIncompatible(current))) changed.add("INCOMPATIBLE_PROJECTS");
        if (!Objects.equals(baseline.getManifestId(), current.getManifestId())) changed.add("MANIFEST_ID");
        if (!Objects.equals(baseline.getManifestVersion(), current.getManifestVersion())) changed.add("MANIFEST_VERSION");
        return List.copyOf(changed);
    }
    private static List<String> canonicalGames(ProjectVersion version) {
        var games = new ArrayList<>(version.getGameVersions() == null ? List.<String>of() : version.getGameVersions());
        Collections.sort(games);
        return games;
    }
    private static List<String> canonicalIncompatible(ProjectVersion version) {
        var incompatible = new ArrayList<>(version.getIncompatibleProjectIds() == null
                ? List.<String>of() : version.getIncompatibleProjectIds());
        if (incompatible.stream().anyMatch(id -> id == null || id.isBlank())) return null;
        Collections.sort(incompatible);
        return incompatible;
    }
    private static List<String> canonicalDependencies(ProjectVersion version) {
        var dependencies = new ArrayList<String>();
        if (version.getDependencies() != null) for (var dependency : version.getDependencies()) {
            try { dependencies.add(MAPPER.writeValueAsString(Arrays.asList(dependency.getProjectId(), dependency.getVersionNumber(),
                    dependency.getDependencyType().name(), dependency.getSource().name()))); }
            catch (Exception invalid) { throw new IllegalArgumentException("Invalid review dependency", invalid); }
        }
        Collections.sort(dependencies);
        return dependencies;
    }
    public static String automaticallyReviewableFingerprint(ProjectVersion version) {
        if (version == null || version.getDependencies() != null && !version.getDependencies().isEmpty()) return null;
        return fingerprint(version);
    }
    public static org.springframework.data.mongodb.core.query.Criteria bindSnapshot(
            org.springframework.data.mongodb.core.query.Criteria criteria, ProjectVersion version) {
        return criteria.and("versionMutation").is(version.getVersionMutation()).and("retainedRemoteReview").is(version.getRetainedRemoteReview())
                .and("replacementSecurityHold").is(version.getReplacementSecurityHold())
                .and("reviewReplacement").is(version.getReviewReplacement())
                .and("findingReviewHead").is(version.getFindingReviewHead()).and("gameVersions").is(version.getGameVersions())
                .and("dependencies").is(version.getDependencies())
                .and("incompatibleProjectIds").is(version.getIncompatibleProjectIds())
                .and("manifestId").is(version.getManifestId())
                .and("manifestVersion").is(version.getManifestVersion())
                .and("overrideFileUrl").is(version.getOverrideFileUrl())
                .and("modpackConfigs").is(version.getModpackConfigs());
    }
    public static boolean hasSupplementalContent(ProjectVersion version) {
        return version.getOverrideFileUrl() != null && !version.getOverrideFileUrl().isBlank()
                || version.getModpackConfigs() != null && !version.getModpackConfigs().isEmpty();
    }
    public static String inspectionFileReference(ProjectVersion version) {
        return inspectionFileReference(version, null);
    }
    public static String inspectionFileReference(ProjectVersion version, ProjectClassification classification) {
        if (version == null) return null;
        if (classification == ProjectClassification.MODPACK && version.getOverrideFileUrl() != null
                && !version.getOverrideFileUrl().isBlank()) return version.getOverrideFileUrl();
        if (version.getFileUrl() != null && !version.getFileUrl().isBlank()) return version.getFileUrl();
        return version.getOverrideFileUrl();
    }
}
