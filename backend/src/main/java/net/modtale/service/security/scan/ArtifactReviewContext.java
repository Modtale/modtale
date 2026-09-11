package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.modtale.model.project.ProjectVersion;
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
            byte[] canonical = MAPPER.writeValueAsBytes(Arrays.asList("artifact-context-1", games, dependencies,
                    version.getManifestId(), version.getManifestVersion()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception invalidContext) {
            return null;
        }
    }
    public static boolean hasSupplementalContent(ProjectVersion version) {
        return version.getOverrideFileUrl() != null && !version.getOverrideFileUrl().isBlank()
                || version.getModpackConfigs() != null && !version.getModpackConfigs().isEmpty();
    }
}
