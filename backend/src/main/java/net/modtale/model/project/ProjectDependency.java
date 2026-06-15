package net.modtale.model.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Transient;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectDependency {

    @JsonProperty("projectId")
    private String modId;

    @JsonProperty("projectTitle")
    private String modTitle;

    private String versionNumber;
    @Transient
    private String icon;
    @Transient
    private String title;
    @Transient
    private ProjectClassification classification;
    @Transient
    private String slug;
    private boolean isOptional;
    private boolean isEmbedded;

    public ProjectDependency() {}

    public ProjectDependency(String modId, String modTitle, String versionNumber) {
        this(modId, modTitle, versionNumber, false, false);
    }

    public ProjectDependency(String modId, String modTitle, String versionNumber, boolean isOptional) {
        this(modId, modTitle, versionNumber, isOptional, false);
    }

    public ProjectDependency(String modId, String modTitle, String versionNumber, boolean isOptional, boolean isEmbedded) {
        this.modId = modId;
        this.modTitle = modTitle;
        this.versionNumber = versionNumber;
        this.isOptional = isOptional;
        this.isEmbedded = isEmbedded;
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

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public ProjectClassification getClassification() { return classification; }
    public void setClassification(ProjectClassification classification) { this.classification = classification; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    @JsonProperty("isOptional")
    public boolean isOptional() { return isOptional; }

    public void setOptional(boolean optional) { isOptional = optional; }

    @JsonProperty("isEmbedded")
    public boolean isEmbedded() { return isEmbedded; }

    public void setEmbedded(boolean embedded) { isEmbedded = embedded; }
}
