package net.modtale.model.project;

import java.util.List;

public class ProjectVersion {
    private String id;
    private String versionNumber;
    private List<String> gameVersions;
    private String fileUrl;
    private String hash;
    private int downloadCount;
    private String releaseDate;
    private String changelog;
    private List<ProjectDependency> dependencies;
    private List<String> incompatibleProjectIds;
    private Channel channel;

    private ScanResult scanResult;
    private List<ApprovedIssueBaseline> approvedIssueBaselines;

    private ReviewStatus reviewStatus = ReviewStatus.PENDING;
    private String rejectionReason;

    private String scheduledPublishDate;

    public enum Channel { RELEASE, BETA, ALPHA }
    public enum ReviewStatus { PENDING, SCHEDULED, APPROVED, REJECTED }

    public static class ApprovedIssueBaseline {
        private String fingerprint;
        private String looseFingerprint;
        private String severity;
        private int scoreImpact;
        private int confidence;
        private long approvedAt;

        public ApprovedIssueBaseline() {}

        public ApprovedIssueBaseline(
                String fingerprint,
                String looseFingerprint,
                String severity,
                int scoreImpact,
                int confidence,
                long approvedAt
        ) {
            this.fingerprint = fingerprint;
            this.looseFingerprint = looseFingerprint;
            this.severity = severity;
            this.scoreImpact = scoreImpact;
            this.confidence = confidence;
            this.approvedAt = approvedAt;
        }

        public String getFingerprint() { return fingerprint; }
        public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

        public String getLooseFingerprint() { return looseFingerprint; }
        public void setLooseFingerprint(String looseFingerprint) { this.looseFingerprint = looseFingerprint; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public int getScoreImpact() { return scoreImpact; }
        public void setScoreImpact(int scoreImpact) { this.scoreImpact = scoreImpact; }

        public int getConfidence() { return confidence; }
        public void setConfidence(int confidence) { this.confidence = confidence; }

        public long getApprovedAt() { return approvedAt; }
        public void setApprovedAt(long approvedAt) { this.approvedAt = approvedAt; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }

    public List<String> getGameVersions() { return gameVersions; }
    public void setGameVersions(List<String> gameVersions) { this.gameVersions = gameVersions; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = changelog; }

    public List<ProjectDependency> getDependencies() { return dependencies; }
    public void setDependencies(List<ProjectDependency> dependencies) { this.dependencies = dependencies; }

    public List<String> getIncompatibleProjectIds() { return incompatibleProjectIds; }
    public void setIncompatibleProjectIds(List<String> incompatibleProjectIds) { this.incompatibleProjectIds = incompatibleProjectIds; }

    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }

    public ScanResult getScanResult() { return scanResult; }
    public void setScanResult(ScanResult scanResult) { this.scanResult = scanResult; }

    public List<ApprovedIssueBaseline> getApprovedIssueBaselines() { return approvedIssueBaselines; }
    public void setApprovedIssueBaselines(List<ApprovedIssueBaseline> approvedIssueBaselines) { this.approvedIssueBaselines = approvedIssueBaselines; }

    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getScheduledPublishDate() { return scheduledPublishDate; }
    public void setScheduledPublishDate(String scheduledPublishDate) { this.scheduledPublishDate = scheduledPublishDate; }
}
