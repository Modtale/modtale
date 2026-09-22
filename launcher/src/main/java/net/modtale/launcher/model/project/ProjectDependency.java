package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectDependency(
        String id,
        @JsonAlias("modId") String projectId,
        @JsonAlias("modTitle") String projectTitle,
        String versionNumber,
        String dependencyType,
        String source,
        String externalId,
        String externalUrl,
        String externalFileUrl,
        String externalFileName,
        String cachedFileUrl,
        boolean hytaleProjectConfirmed,
        @JsonAlias("imageUrl") String icon,
        String title,
        String classification,
        String slug,
        @JsonProperty("isOptional") Boolean optional,
        @JsonProperty("isEmbedded") Boolean embedded
) {
    public ProjectDependency {
        if (source != null) source = source.trim();
        if ((source == null || source.isBlank()) && projectId != null
                && projectId.startsWith("curseforge:")) {
            source = "CURSEFORGE";
        }
    }

    public ProjectDependency(
            String id,
            String projectId,
            String projectTitle,
            String versionNumber,
            String dependencyType,
            String source,
            String externalId,
            String externalUrl,
            String externalFileUrl,
            String externalFileName,
            String cachedFileUrl,
            boolean hytaleProjectConfirmed
    ) {
        this(id, projectId, projectTitle, versionNumber, dependencyType, source, externalId, externalUrl,
                externalFileUrl, externalFileName, cachedFileUrl, hytaleProjectConfirmed,
                null, null, null, null, null, null);
    }

    public boolean isOptional() {
        return Boolean.TRUE.equals(optional) || DependencyType.OPTIONAL.matches(dependencyType);
    }

    public boolean isEmbedded() {
        return Boolean.TRUE.equals(embedded) || DependencyType.EMBEDDED.matches(dependencyType);
    }

    public boolean isExternal() {
        return source != null && !source.isBlank() && !DependencySource.MODTALE.matches(source);
    }

    public boolean isCurseForge() {
        return "CURSEFORGE".equalsIgnoreCase(source);
    }

    private enum DependencyType {
        OPTIONAL,
        EMBEDDED;

        boolean matches(String value) {
            return value != null && name().equalsIgnoreCase(value.trim());
        }
    }

    private enum DependencySource {
        MODTALE;

        boolean matches(String value) {
            return value != null && name().equalsIgnoreCase(value.trim());
        }
    }
}
