package net.modtale.model.resources;

import java.util.List;

public class ModVersion {
    private String id;
    private String versionNumber;
    private List<String> gameVersions;
    private String fileUrl;
    private int downloadCount;
    private String releaseDate;
    private String changelog;
    private List<ModDependency> dependencies;
    private Channel channel;

    private ScanResult scanResult;

    private ReviewStatus reviewStatus = ReviewStatus.PENDING;
    private String rejectionReason;

    // Determines when the version should automatically transition to APPROVED
    private String scheduledPublishDate;

    public enum Channel { RELEASE, BETA, ALPHA }
    public enum ReviewStatus { PENDING, APPROVED, REJECTED }

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

    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }

    public ScanResult getScanResult() { return scanResult; }
    public void setScanResult(ScanResult scanResult) { this.scanResult = scanResult; }

    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getScheduledPublishDate() { return scheduledPublishDate; }
    public void setScheduledPublishDate(String scheduledPublishDate) { this.scheduledPublishDate = scheduledPublishDate; }
}