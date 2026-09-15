package net.modtale.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanResult {
    private ScanStatus status;
    private String verdict;
    private String riskLevel;
    private String scanState;

    private int riskScore;
    private int confidenceScore;
    private int scanAttempt;

    private long scanTimestamp;
    private long holdUntilTimestamp;

    private int knownIssueCount;
    private int newIssueCount;
    private int escalatedIssueCount;

    private List<String> reviewerNotes = new ArrayList<>();
    private ScanSummary summary;
    private List<ReviewTarget> reviewTargets = new ArrayList<>();
    private List<ScanIssue> issues = new ArrayList<>();

    public ScanResult() {}

    public ScanResult(ScanStatus status, int riskScore, List<ScanIssue> issues) {
        this.status = status;
        this.riskScore = riskScore;
        this.issues = issues == null ? new ArrayList<>() : issues;
        this.scanTimestamp = System.currentTimeMillis();
    }

    public ScanStatus getStatus() { return status; }
    public void setStatus(ScanStatus status) { this.status = status; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getScanState() { return scanState; }
    public void setScanState(String scanState) { this.scanState = scanState; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public int getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }

    public int getScanAttempt() { return scanAttempt; }
    public void setScanAttempt(int scanAttempt) { this.scanAttempt = scanAttempt; }

    public List<ScanIssue> getIssues() { return issues; }
    public void setIssues(List<ScanIssue> issues) { this.issues = issues == null ? new ArrayList<>() : issues; }

    public long getScanTimestamp() { return scanTimestamp; }
    public void setScanTimestamp(long scanTimestamp) { this.scanTimestamp = scanTimestamp; }

    public long getHoldUntilTimestamp() { return holdUntilTimestamp; }
    public void setHoldUntilTimestamp(long holdUntilTimestamp) { this.holdUntilTimestamp = holdUntilTimestamp; }

    public int getKnownIssueCount() { return knownIssueCount; }
    public void setKnownIssueCount(int knownIssueCount) { this.knownIssueCount = knownIssueCount; }

    public int getNewIssueCount() { return newIssueCount; }
    public void setNewIssueCount(int newIssueCount) { this.newIssueCount = newIssueCount; }

    public int getEscalatedIssueCount() { return escalatedIssueCount; }
    public void setEscalatedIssueCount(int escalatedIssueCount) { this.escalatedIssueCount = escalatedIssueCount; }

    public List<String> getReviewerNotes() { return reviewerNotes; }
    public void setReviewerNotes(List<String> reviewerNotes) { this.reviewerNotes = reviewerNotes == null ? new ArrayList<>() : reviewerNotes; }

    public ScanSummary getSummary() { return summary; }
    public void setSummary(ScanSummary summary) { this.summary = summary; }

    public List<ReviewTarget> getReviewTargets() { return reviewTargets; }
    public void setReviewTargets(List<ReviewTarget> reviewTargets) { this.reviewTargets = reviewTargets == null ? new ArrayList<>() : reviewTargets; }

    public boolean hasFindings() {
        return issues != null && !issues.isEmpty();
    }

    public boolean isActionableVerdict() {
        return verdict != null && !"AUTO_APPROVE".equalsIgnoreCase(verdict);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScanSummary {
        private int totalIssues;
        private int criticalIssues;
        private int highIssues;
        private int mediumIssues;
        private int lowIssues;
        private int lowConfidenceIssues;
        private int filesScanned;
        private int classFilesScanned;
        private int archivesScanned;
        private int recoverableErrors;
        private int uniquePackageRoots;
        private int dominantPackageRootClasses;
        private int dominantPackageRootSharePercent;
        private int maxArchiveDepthReached;
        private int oversizedEntriesSkipped;
        private int nestedArchiveReadFailures;
        private int correlatedThreatClusters;
        private int alwaysReviewIssues;
        private int suppressedNoiseIssues;

        public int getTotalIssues() { return totalIssues; }
        public void setTotalIssues(int totalIssues) { this.totalIssues = totalIssues; }
        public int getCriticalIssues() { return criticalIssues; }
        public void setCriticalIssues(int criticalIssues) { this.criticalIssues = criticalIssues; }
        public int getHighIssues() { return highIssues; }
        public void setHighIssues(int highIssues) { this.highIssues = highIssues; }
        public int getMediumIssues() { return mediumIssues; }
        public void setMediumIssues(int mediumIssues) { this.mediumIssues = mediumIssues; }
        public int getLowIssues() { return lowIssues; }
        public void setLowIssues(int lowIssues) { this.lowIssues = lowIssues; }
        public int getLowConfidenceIssues() { return lowConfidenceIssues; }
        public void setLowConfidenceIssues(int lowConfidenceIssues) { this.lowConfidenceIssues = lowConfidenceIssues; }
        public int getFilesScanned() { return filesScanned; }
        public void setFilesScanned(int filesScanned) { this.filesScanned = filesScanned; }
        public int getClassFilesScanned() { return classFilesScanned; }
        public void setClassFilesScanned(int classFilesScanned) { this.classFilesScanned = classFilesScanned; }
        public int getArchivesScanned() { return archivesScanned; }
        public void setArchivesScanned(int archivesScanned) { this.archivesScanned = archivesScanned; }
        public int getRecoverableErrors() { return recoverableErrors; }
        public void setRecoverableErrors(int recoverableErrors) { this.recoverableErrors = recoverableErrors; }
        public int getUniquePackageRoots() { return uniquePackageRoots; }
        public void setUniquePackageRoots(int uniquePackageRoots) { this.uniquePackageRoots = uniquePackageRoots; }
        public int getDominantPackageRootClasses() { return dominantPackageRootClasses; }
        public void setDominantPackageRootClasses(int dominantPackageRootClasses) { this.dominantPackageRootClasses = dominantPackageRootClasses; }
        public int getDominantPackageRootSharePercent() { return dominantPackageRootSharePercent; }
        public void setDominantPackageRootSharePercent(int dominantPackageRootSharePercent) { this.dominantPackageRootSharePercent = dominantPackageRootSharePercent; }
        public int getMaxArchiveDepthReached() { return maxArchiveDepthReached; }
        public void setMaxArchiveDepthReached(int maxArchiveDepthReached) { this.maxArchiveDepthReached = maxArchiveDepthReached; }
        public int getOversizedEntriesSkipped() { return oversizedEntriesSkipped; }
        public void setOversizedEntriesSkipped(int oversizedEntriesSkipped) { this.oversizedEntriesSkipped = oversizedEntriesSkipped; }
        public int getNestedArchiveReadFailures() { return nestedArchiveReadFailures; }
        public void setNestedArchiveReadFailures(int nestedArchiveReadFailures) { this.nestedArchiveReadFailures = nestedArchiveReadFailures; }
        public int getCorrelatedThreatClusters() { return correlatedThreatClusters; }
        public void setCorrelatedThreatClusters(int correlatedThreatClusters) { this.correlatedThreatClusters = correlatedThreatClusters; }
        public int getAlwaysReviewIssues() { return alwaysReviewIssues; }
        public void setAlwaysReviewIssues(int alwaysReviewIssues) { this.alwaysReviewIssues = alwaysReviewIssues; }
        public int getSuppressedNoiseIssues() { return suppressedNoiseIssues; }
        public void setSuppressedNoiseIssues(int suppressedNoiseIssues) { this.suppressedNoiseIssues = suppressedNoiseIssues; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReviewTarget {
        private String filePath;
        private String priority;
        private String reason;
        private int issueCount;
        private int cumulativeImpact;
        private List<String> relatedChecks = new ArrayList<>();
        private boolean alwaysReview;
        private List<String> tactics = new ArrayList<>();

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public int getIssueCount() { return issueCount; }
        public void setIssueCount(int issueCount) { this.issueCount = issueCount; }
        public int getCumulativeImpact() { return cumulativeImpact; }
        public void setCumulativeImpact(int cumulativeImpact) { this.cumulativeImpact = cumulativeImpact; }
        public List<String> getRelatedChecks() { return relatedChecks; }
        public void setRelatedChecks(List<String> relatedChecks) { this.relatedChecks = relatedChecks == null ? new ArrayList<>() : relatedChecks; }
        public boolean isAlwaysReview() { return alwaysReview; }
        public void setAlwaysReview(boolean alwaysReview) { this.alwaysReview = alwaysReview; }
        public List<String> getTactics() { return tactics; }
        public void setTactics(List<String> tactics) { this.tactics = tactics == null ? new ArrayList<>() : tactics; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScanIssue {
        private String severity;
        private String type;
        private String category;
        private String description;
        private String filePath;
        private int lineStart;
        private int lineEnd;

        private int scoreImpact;
        private int confidence;
        private String reviewPriority;
        private String evidenceLevel;
        private String reviewCadence;
        private boolean noiseSuppressed;
        private List<String> tactics = new ArrayList<>();

        private boolean resolved;
        private String fingerprint;
        private boolean knownIssue;
        private boolean escalated;
        private String baselineVersion;
        private int baselineScoreImpact;
        private String baselineSeverity;

        public ScanIssue() {}

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public int getLineStart() { return lineStart; }
        public void setLineStart(int lineStart) { this.lineStart = lineStart; }

        public int getLineEnd() { return lineEnd; }
        public void setLineEnd(int lineEnd) { this.lineEnd = lineEnd; }

        public int getScoreImpact() { return scoreImpact; }
        public void setScoreImpact(int scoreImpact) { this.scoreImpact = scoreImpact; }

        public int getConfidence() { return confidence; }
        public void setConfidence(int confidence) { this.confidence = confidence; }

        public String getReviewPriority() { return reviewPriority; }
        public void setReviewPriority(String reviewPriority) { this.reviewPriority = reviewPriority; }

        public String getEvidenceLevel() { return evidenceLevel; }
        public void setEvidenceLevel(String evidenceLevel) { this.evidenceLevel = evidenceLevel; }

        public String getReviewCadence() { return reviewCadence; }
        public void setReviewCadence(String reviewCadence) { this.reviewCadence = reviewCadence; }

        public boolean isNoiseSuppressed() { return noiseSuppressed; }
        public void setNoiseSuppressed(boolean noiseSuppressed) { this.noiseSuppressed = noiseSuppressed; }

        public List<String> getTactics() { return tactics; }
        public void setTactics(List<String> tactics) { this.tactics = tactics == null ? new ArrayList<>() : tactics; }

        public boolean isResolved() { return resolved; }
        public void setResolved(boolean resolved) { this.resolved = resolved; }

        public String getFingerprint() { return fingerprint; }
        public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

        public boolean isKnownIssue() { return knownIssue; }
        public void setKnownIssue(boolean knownIssue) { this.knownIssue = knownIssue; }

        public boolean isEscalated() { return escalated; }
        public void setEscalated(boolean escalated) { this.escalated = escalated; }

        public String getBaselineVersion() { return baselineVersion; }
        public void setBaselineVersion(String baselineVersion) { this.baselineVersion = baselineVersion; }

        public int getBaselineScoreImpact() { return baselineScoreImpact; }
        public void setBaselineScoreImpact(int baselineScoreImpact) { this.baselineScoreImpact = baselineScoreImpact; }

        public String getBaselineSeverity() { return baselineSeverity; }
        public void setBaselineSeverity(String baselineSeverity) { this.baselineSeverity = baselineSeverity; }
    }
}
