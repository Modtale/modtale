package net.modtale.model.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import org.springframework.data.annotation.Transient;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectDependency {

    public enum Source {
        MODTALE,
        CURSEFORGE,
        GITHUB,
        WEBSITE,
        OTHER
    }

    public enum DependencyType {
        REQUIRED,
        OPTIONAL,
        EMBEDDED
    }

    private String id = UUID.randomUUID().toString();
    private String projectId;
    private String projectTitle;
    private String versionNumber;
    @Transient
    private String icon;
    @Transient
    private String title;
    @Transient
    private ProjectClassification classification;
    @Transient
    private String slug;
    private DependencyType dependencyType = DependencyType.REQUIRED;
    private Source source = Source.MODTALE;
    private String externalId;
    private String externalUrl;
    private String externalFileUrl;
    private String externalFileName;
    private String cachedFileUrl;
    private boolean hytaleProjectConfirmed;

    public ProjectDependency() {}

    public ProjectDependency(String projectId, String projectTitle, String versionNumber) {
        this(projectId, projectTitle, versionNumber, DependencyType.REQUIRED);
    }

    public ProjectDependency(String projectId, String projectTitle, String versionNumber, DependencyType dependencyType) {
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.versionNumber = versionNumber;
        setDependencyType(dependencyType);
    }

    public static ProjectDependency modtale(String projectId, String projectTitle, String versionNumber, DependencyType dependencyType) {
        ProjectDependency dependency = new ProjectDependency(projectId, projectTitle, versionNumber, dependencyType);
        dependency.setSource(Source.MODTALE);
        return dependency;
    }

    public static ProjectDependency curseForge(
            String externalId,
            String title,
            String versionNumber,
            String externalUrl,
            DependencyType dependencyType
    ) {
        return external(Source.CURSEFORGE, externalId, title, versionNumber, externalUrl, dependencyType);
    }

    public static ProjectDependency external(
            Source source,
            String externalId,
            String title,
            String versionNumber,
            String externalUrl,
            DependencyType dependencyType
    ) {
        Source externalSource = source == null || source == Source.MODTALE ? Source.OTHER : source;
        ProjectDependency dependency = new ProjectDependency(externalSource.name().toLowerCase() + ":" + externalId, title, versionNumber, dependencyType);
        dependency.setSource(externalSource);
        dependency.setExternalId(externalId);
        dependency.setExternalUrl(externalUrl);
        return dependency;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }

    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public ProjectClassification getClassification() { return classification; }
    public void setClassification(ProjectClassification classification) { this.classification = classification; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public DependencyType getDependencyType() { return dependencyType == null ? DependencyType.REQUIRED : dependencyType; }
    public void setDependencyType(DependencyType dependencyType) {
        this.dependencyType = dependencyType == null ? DependencyType.REQUIRED : dependencyType;
    }

    public Source getSource() { return source == null ? Source.MODTALE : source; }
    public void setSource(Source source) { this.source = source == null ? Source.MODTALE : source; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public String getExternalFileUrl() { return externalFileUrl; }
    public void setExternalFileUrl(String externalFileUrl) { this.externalFileUrl = externalFileUrl; }

    public String getExternalFileName() { return externalFileName; }
    public void setExternalFileName(String externalFileName) { this.externalFileName = externalFileName; }

    public String getCachedFileUrl() { return cachedFileUrl; }
    public void setCachedFileUrl(String cachedFileUrl) { this.cachedFileUrl = cachedFileUrl; }

    public boolean isExternal() {
        return getSource() != Source.MODTALE;
    }

    public boolean isHytaleProjectConfirmed() { return hytaleProjectConfirmed; }
    public void setHytaleProjectConfirmed(boolean hytaleProjectConfirmed) {
        this.hytaleProjectConfirmed = hytaleProjectConfirmed;
    }

    @JsonProperty("isEmbedded")
    public boolean isEmbedded() { return getDependencyType() == DependencyType.EMBEDDED; }

    @JsonProperty("isOptional")
    public boolean isOptional() { return getDependencyType() == DependencyType.OPTIONAL; }
}
