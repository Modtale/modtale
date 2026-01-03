package net.modtale.model.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ModDependency {
    private String modId;
    private String modTitle;
    private String versionNumber;
    private boolean isOptional;

    public ModDependency() {}

    public ModDependency(String modId, String modTitle, String versionNumber) {
        this(modId, modTitle, versionNumber, false);
    }

    public ModDependency(String modId, String modTitle, String versionNumber, boolean isOptional) {
        this.modId = modId;
        this.modTitle = modTitle;
        this.versionNumber = versionNumber;
        this.isOptional = isOptional;
    }

    public String getModId() { return modId; }
    public void setModId(String modId) { this.modId = modId; }

    public String getModTitle() { return modTitle; }
    public void setModTitle(String modTitle) { this.modTitle = modTitle; }

    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }

    @JsonProperty("isOptional")
    public boolean isOptional() { return isOptional; }

    public void setOptional(boolean optional) { isOptional = optional; }
}