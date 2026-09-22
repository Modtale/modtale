package net.modtale.model.dto.project;

public class ManifestDependencySuggestion {
    private String manifestKey;
    private String requestedVersion;
    private String projectId;
    private String projectTitle;
    private String versionNumber;
    private boolean optional;
    private int confidence;

    public ManifestDependencySuggestion() {}

    public ManifestDependencySuggestion(String manifestKey, String requestedVersion, String projectId, String projectTitle, String versionNumber, boolean optional, int confidence) {
        this.manifestKey = manifestKey;
        this.requestedVersion = requestedVersion;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.versionNumber = versionNumber;
        this.optional = optional;
        this.confidence = confidence;
    }

    public String getManifestKey() { return manifestKey; }
    public void setManifestKey(String manifestKey) { this.manifestKey = manifestKey; }

    public String getRequestedVersion() { return requestedVersion; }
    public void setRequestedVersion(String requestedVersion) { this.requestedVersion = requestedVersion; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }

    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }

    public boolean isOptional() { return optional; }
    public void setOptional(boolean optional) { this.optional = optional; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
}
