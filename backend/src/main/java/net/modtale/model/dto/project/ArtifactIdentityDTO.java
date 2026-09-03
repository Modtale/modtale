package net.modtale.model.dto.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ArtifactIdentityDTO {

    private ArtifactIdentityDTO() {}

    public record Request(
            @Valid @Size(max = 100) List<Artifact> artifacts
    ) {
        public Request {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }

    public record Artifact(
            @NotBlank @Size(max = 180) String key,
            @Pattern(regexp = "(?i)^[a-f0-9]{64}$") String sha256,
            @Min(0) @Max(4294967295L) Long curseForgeFingerprint,
            @Size(max = 240) String manifestId,
            @Size(max = 120) String version,
            @Size(max = 1000) String website
    ) {}

    public record Response(List<Match> matches) {
        public Response {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }
    }

    public record Match(
            String key,
            String source,
            String projectId,
            String slug,
            String title,
            String classification,
            String versionNumber,
            String versionId,
            String evidence,
            int confidence
    ) {}
}
