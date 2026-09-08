package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public final class ArtifactIdentity {
    private ArtifactIdentity() {}

    public record Request(List<Artifact> artifacts) {
        public Request { artifacts = artifacts == null ? List.of() : List.copyOf(artifacts); }
    }

    public record Artifact(String key, String sha256, Long curseForgeFingerprint,
                           String manifestId, String version, String website) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(List<Match> matches) {
        public Response { matches = matches == null ? List.of() : List.copyOf(matches); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(String key, String source, String projectId, String slug, String title,
                        String classification, String versionNumber, String versionId,
                        String evidence, int confidence) {}
}
