package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import net.modtale.model.project.ProjectVersion;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectVersionDTO {
    private String id;
    private String versionNumber;
    private List<String> gameVersions;
    private String fileUrl;
    private int downloadCount;
    private String releaseDate;
    private String changelog;
    private List<ProjectDependencyDTO> dependencies;
    private List<String> incompatibleProjectIds;
    private ProjectVersion.Channel channel;

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
    public List<ProjectDependencyDTO> getDependencies() { return dependencies; }
    public void setDependencies(List<ProjectDependencyDTO> dependencies) { this.dependencies = dependencies; }
    public List<String> getIncompatibleProjectIds() { return incompatibleProjectIds; }
    public void setIncompatibleProjectIds(List<String> incompatibleProjectIds) { this.incompatibleProjectIds = incompatibleProjectIds; }
    public ProjectVersion.Channel getChannel() { return channel; }
    public void setChannel(ProjectVersion.Channel channel) { this.channel = channel; }
}
