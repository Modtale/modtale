package net.modtale.model.dto.request.project;

import net.modtale.model.project.ProjectDependency;

public class DependencyReferenceRequest {

    private String id;
    private String projectId;
    private String projectTitle;
    private String versionNumber;
    private ProjectDependency.DependencyType dependencyType = ProjectDependency.DependencyType.REQUIRED;
    private ProjectDependency.Source source = ProjectDependency.Source.MODTALE;
    private String externalId;
    private String externalUrl;
    private String externalFileUrl;
    private String externalFileName;
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

    public String getCachedFileUrl() { return cachedFileUrl; }
    public void setCachedFileUrl(String cachedFileUrl) { this.cachedFileUrl = cachedFileUrl; }

    public boolean isHytaleProjectConfirmed() { return hytaleProjectConfirmed; }
    public void setHytaleProjectConfirmed(boolean hytaleProjectConfirmed) {
        this.hytaleProjectConfirmed = hytaleProjectConfirmed;
    }
}
