package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import net.modtale.model.project.ProjectVersion;
import org.springframework.web.multipart.MultipartFile;

public class CreateVersionRequest {

    @NotBlank(message = "A version number is required before we can upload a project version.")
    private String versionNumber;

    private List<@NotBlank(message = "Game version entries cannot be blank.") String> gameVersions;
    private MultipartFile file;
    private List<DependencyReferenceRequest> dependencies;
    private List<@NotBlank(message = "Incompatible project entries cannot be blank.") String> incompatibleProjectIds;

    @Size(max = 50000, message = "Version changelogs cannot exceed 50,000 characters.")
    private String changelog;

    private ProjectVersion.Channel channel = ProjectVersion.Channel.RELEASE;

    public String getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }

    public List<String> getGameVersions() {
        return gameVersions;
    }

    public void setGameVersions(List<String> gameVersions) {
        this.gameVersions = gameVersions;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public List<DependencyReferenceRequest> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<DependencyReferenceRequest> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getIncompatibleProjectIds() {
        return incompatibleProjectIds;
    }

    public void setIncompatibleProjectIds(List<String> incompatibleProjectIds) {
        this.incompatibleProjectIds = incompatibleProjectIds;
    }

    public String getChangelog() {
        return changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public ProjectVersion.Channel getChannel() {
        return channel;
    }

    public void setChannel(ProjectVersion.Channel channel) {
        this.channel = channel;
    }
}
