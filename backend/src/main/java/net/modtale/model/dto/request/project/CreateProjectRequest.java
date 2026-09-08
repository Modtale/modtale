package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.modtale.model.project.ProjectClassification;

public class CreateProjectRequest {
    @NotBlank(message = "A project title is required before we can create a draft.")
    @Size(max = 100, message = "Project titles must be 100 characters or fewer.")
    private String title;

    @NotNull(message = "A project classification is required before we can create a draft.")
    private ProjectClassification classification;

    @Size(max = 250, message = "The short summary cannot exceed 250 characters.")
    private String description;
    private String owner;

    @Pattern(
            regexp = "^$|^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$",
            message = "Project slugs must be 3-50 lowercase characters and may include dashes."
    )
    private String slug;

    @Size(max = 50000, message = "The full description cannot exceed 50,000 characters.")
    private String about;

    @Pattern(regexp = "https://www\\.curseforge\\.com/hytale/(mods|prefabs|worlds|bootstrap|translations)/[a-z0-9-]+/?",
            message = "The import source must be a Hytale CurseForge project URL.")
    private String curseForgeUrl;

    @Size(max = 2048)
    @Pattern(regexp = "https://(?:[a-zA-Z0-9-]+\\.)*forgecdn\\.net/[^\\s?#]+",
            message = "Imported icons must use a CurseForge CDN URL.")
    private String imageUrl;

    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public String getCurseForgeUrl() { return curseForgeUrl; }
    public void setCurseForgeUrl(String curseForgeUrl) { this.curseForgeUrl = curseForgeUrl; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ProjectClassification getClassification() {
        return classification;
    }

    public void setClassification(ProjectClassification classification) {
        this.classification = classification;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }
}
