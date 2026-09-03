package net.modtale.model.dto.request.project;

import java.util.List;
import java.util.Map;
import net.modtale.model.project.ProjectDependency;

public class DependencyReferenceRequest {

    private String id;
    private String projectId;
    private String projectTitle;
    private String versionNumber;
    private ProjectDependency.DependencyType dependencyType = ProjectDependency.DependencyType.REQUIRED;
    private ProjectDependency.Environment environment = ProjectDependency.Environment.COMMON;
    private ProjectDependency.Source source = ProjectDependency.Source.MODTALE;
    private String externalId;
    private String externalUrl;
    private String externalFileUrl;
    private String externalFileName;
    private Long externalFileSize;
    private Map<String, String> externalFileHashes;
    private List<String> externalGameVersions;
    private Integer externalFileStatus;
    private Boolean externalDistributionAllowed;
    private String cachedFileUrl;
    private boolean hytaleProjectConfirmed;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }

    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }

    public ProjectDependency.DependencyType getDependencyType() {
        return dependencyType == null ? ProjectDependency.DependencyType.REQUIRED : dependencyType;
    }

    public void setDependencyType(ProjectDependency.DependencyType dependencyType) {
        this.dependencyType = dependencyType == null ? ProjectDependency.DependencyType.REQUIRED : dependencyType;
    }

    public ProjectDependency.Environment getEnvironment() {
        return environment == null ? ProjectDependency.Environment.COMMON : environment;
    }

    public void setEnvironment(ProjectDependency.Environment environment) {
        this.environment = environment == null ? ProjectDependency.Environment.COMMON : environment;
    }

    public ProjectDependency.Source getSource() {
        return source == null ? ProjectDependency.Source.MODTALE : source;
    }

    public void setSource(ProjectDependency.Source source) {
        this.source = source == null ? ProjectDependency.Source.MODTALE : source;
    }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public String getExternalFileUrl() { return externalFileUrl; }
    public void setExternalFileUrl(String externalFileUrl) { this.externalFileUrl = externalFileUrl; }

    public String getExternalFileName() { return externalFileName; }
    public void setExternalFileName(String externalFileName) { this.externalFileName = externalFileName; }

    public Long getExternalFileSize() { return externalFileSize; }
    public void setExternalFileSize(Long externalFileSize) { this.externalFileSize = externalFileSize; }

    public Map<String, String> getExternalFileHashes() { return externalFileHashes; }
    public void setExternalFileHashes(Map<String, String> externalFileHashes) { this.externalFileHashes = externalFileHashes; }

    public List<String> getExternalGameVersions() { return externalGameVersions; }
    public void setExternalGameVersions(List<String> externalGameVersions) { this.externalGameVersions = externalGameVersions; }

    public Integer getExternalFileStatus() { return externalFileStatus; }
    public void setExternalFileStatus(Integer externalFileStatus) { this.externalFileStatus = externalFileStatus; }

    public Boolean getExternalDistributionAllowed() { return externalDistributionAllowed; }
    public void setExternalDistributionAllowed(Boolean externalDistributionAllowed) { this.externalDistributionAllowed = externalDistributionAllowed; }

    public String getCachedFileUrl() { return cachedFileUrl; }
    public void setCachedFileUrl(String cachedFileUrl) { this.cachedFileUrl = cachedFileUrl; }

    public boolean isHytaleProjectConfirmed() { return hytaleProjectConfirmed; }
    public void setHytaleProjectConfirmed(boolean hytaleProjectConfirmed) {
        this.hytaleProjectConfirmed = hytaleProjectConfirmed;
    }
}
