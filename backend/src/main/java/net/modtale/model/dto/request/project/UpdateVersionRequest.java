package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class UpdateVersionRequest {
    private List<@NotBlank(message = "Dependency entries cannot be blank.") String> modIds;
    private List<@NotBlank(message = "Game version entries cannot be blank.") String> gameVersions;

    @Size(max = 50000, message = "Version changelogs cannot exceed 50,000 characters.")
    private String changelog;

    @Pattern(
            regexp = "(?i)RELEASE|BETA|ALPHA",
            message = "Version channels must be RELEASE, BETA, or ALPHA."
    )
    private String channel;

    public List<String> getModIds() {
        return modIds;
    }

    public void setModIds(List<String> modIds) {
        this.modIds = modIds;
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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
