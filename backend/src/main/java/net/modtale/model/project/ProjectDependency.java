package net.modtale.model.project;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProjectDependency {
    private String projectId;
    private String projectTitle;
    private String versionNumber;
    private boolean isOptional;

    public ProjectDependency() {}

    public ProjectDependency(String projectId, String projectTitle, String versionNumber) {
        this(projectId, projectTitle, versionNumber, false);
    }

    public ProjectDependency(String projectId, String projectTitle, String versionNumber, boolean isOptional) {
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.versionNumber = versionNumber;
        this.isOptional = isOptional;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }

    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }

    @JsonProperty("isOptional")
    public boolean isOptional() { return isOptional; }

    public void setOptional(boolean optional) { isOptional = optional; }
}