package net.modtale.model.dto;

import net.modtale.model.resources.ModDependency;
import net.modtale.model.resources.ModVersion;
import java.util.List;

public class ModVersionDTO {
    private String id;
    private String versionNumber;
    private List<String> gameVersions;
    private String fileUrl;
    private int downloadCount;
    private String releaseDate;
    private String changelog;
    private List<ModDependency> dependencies;
    private ModVersion.Channel channel;

    public static ModVersionDTO fromEntity(ModVersion version) {
        if (version == null) return null;
        ModVersionDTO dto = new ModVersionDTO();
        dto.setId(version.getId());
        dto.setVersionNumber(version.getVersionNumber());
        dto.setGameVersions(version.getGameVersions());
        dto.setFileUrl(version.getFileUrl());
        dto.setDownloadCount(version.getDownloadCount());
        dto.setReleaseDate(version.getReleaseDate());
        dto.setChangelog(version.getChangelog());
        dto.setDependencies(version.getDependencies());
        dto.setChannel(version.getChannel());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public List<String> getGameVersions() { return gameVersions; }
    public void setGameVersions(List<String> gameVersions) { this.gameVersions = gameVersions; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }
    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = changelog; }
    public List<ModDependency> getDependencies() { return dependencies; }
    public void setDependencies(List<ModDependency> dependencies) { this.dependencies = dependencies; }
    public ModVersion.Channel getChannel() { return channel; }
    public void setChannel(ModVersion.Channel channel) { this.channel = channel; }
}