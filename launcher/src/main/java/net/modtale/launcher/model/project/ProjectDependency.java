package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectDependency(
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
        boolean hytaleProjectConfirmed,
        String icon,
        String title,
        String classification,
        String slug,
        @JsonProperty("isOptional") Boolean optional,
        @JsonProperty("isEmbedded") Boolean embedded
) {
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
        return source != null && !DependencySource.MODTALE.matches(source);
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
