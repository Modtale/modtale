package net.modtale.model.project;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProjectDependency {

    @JsonProperty("projectId")
    private String modId;

    @JsonProperty("projectTitle")
    private String modTitle;

    private String versionNumber;
    private boolean isOptional;

    public ProjectDependency() {}

    public ProjectDependency(String modId, String modTitle, String versionNumber) {
        this(modId, modTitle, versionNumber, false);
    }

    public ProjectDependency(String modId, String modTitle, String versionNumber, boolean isOptional) {
        this.modId = modId;
        this.modTitle = modTitle;
        this.versionNumber = versionNumber;
        this.isOptional = isOptional;
    }

    @JsonProperty("projectId")
    public String getModId() { return modId; }

    @JsonProperty("projectId")
    public void setModId(String modId) { this.modId = modId; }

    @JsonProperty("projectTitle")
    public String getModTitle() { return modTitle; }

    @JsonProperty("projectTitle")
    public void setModTitle(String modTitle) { this.modTitle = modTitle; }

    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }

    @JsonProperty("isOptional")
    public boolean isOptional() { return isOptional; }

    public void setOptional(boolean optional) { isOptional = optional; }
}