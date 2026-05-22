package net.modtale.model.dto.request.project;

import java.util.List;

public class UpdateVersionRequest {
    private List<String> modIds;
    private List<String> gameVersions;
    private String changelog;
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
