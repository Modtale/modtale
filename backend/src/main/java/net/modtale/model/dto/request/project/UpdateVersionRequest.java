package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import net.modtale.model.project.ProjectVersion;

public class UpdateVersionRequest {
    private List<@NotBlank(message = "Dependency entries cannot be blank.") String> modIds;
    private List<@NotBlank(message = "Incompatible project entries cannot be blank.") String> incompatibleProjectIds;
    private List<@NotBlank(message = "Game version entries cannot be blank.") String> gameVersions;

    @Size(max = 50000, message = "Version changelogs cannot exceed 50,000 characters.")
    private String changelog;

    private ProjectVersion.Channel channel;

    public List<String> getModIds() {
        return modIds;
    }

    public void setModIds(List<String> modIds) {
        this.modIds = modIds;
    }

    public List<String> getIncompatibleProjectIds() {
        return incompatibleProjectIds;
    }

    public void setIncompatibleProjectIds(List<String> incompatibleProjectIds) {
        this.incompatibleProjectIds = incompatibleProjectIds;
    }

    public List<String> getGameVersions() {
        return gameVersions;
    }

    public void setGameVersions(List<String> gameVersions) {
        this.gameVersions = gameVersions;
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
