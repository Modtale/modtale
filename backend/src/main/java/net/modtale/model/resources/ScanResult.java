package net.modtale.model.resources;

import java.util.List;

public class ScanResult {
    private String status;
    private int riskScore;
    private List<ScanIssue> issues;
    private long scanTimestamp;

    public ScanResult() {}

    public ScanResult(String status, int riskScore, List<ScanIssue> issues) {
        this.status = status;
        this.riskScore = riskScore;
        this.issues = issues;
        this.scanTimestamp = System.currentTimeMillis();
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public List<ScanIssue> getIssues() { return issues; }
    public void setIssues(List<ScanIssue> issues) { this.issues = issues; }

    public long getScanTimestamp() { return scanTimestamp; }
    public void setScanTimestamp(long scanTimestamp) { this.scanTimestamp = scanTimestamp; }

    // Static inner class to match JSON structure
    public static class ScanIssue {
        private String severity;
        private String type;
        private String description;
        private String filePath;
        private int lineStart;
        private int lineEnd;

        public ScanIssue() {}

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public int getLineStart() { return lineStart; }
        public void setLineStart(int lineStart) { this.lineStart = lineStart; }

        public int getLineEnd() { return lineEnd; }
        public void setLineEnd(int lineEnd) { this.lineEnd = lineEnd; }
    }
}