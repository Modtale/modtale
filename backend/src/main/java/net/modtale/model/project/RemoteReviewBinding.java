package net.modtale.model.project;

public record RemoteReviewBinding(String projectId, String versionId, String requestId, int attempt,
        String filePath, String artifactSha256, String contextSha256, String policyVersion,
        String reviewConfigSha256, String jobId) {
    public RemoteReviewBinding {
        if (!text(projectId,128) || !text(versionId,128) || !uuid(requestId) || attempt < 1
                || !text(filePath,4096) || !digest(artifactSha256) || !digest(contextSha256)
                || policyVersion == null || !policyVersion.matches("warden-3\\.0\\.0:[0-9a-f]{64}")
                || !digest(reviewConfigSha256) || jobId != null && !uuid(jobId))
            throw new IllegalArgumentException("Invalid remote review binding");
    }
    public RemoteReviewBinding withJobId(String value) {
        if (!uuid(value) || jobId != null && !jobId.equals(value)) throw new IllegalArgumentException("Remote job identity cannot change");
        return new RemoteReviewBinding(projectId,versionId,requestId,attempt,filePath,artifactSha256,contextSha256,policyVersion,reviewConfigSha256,value);
    }
    private static boolean text(String value,int max) { return value != null && !value.isBlank() && value.length() <= max && value.chars().noneMatch(Character::isISOControl); }
    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean uuid(String value) { return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"); }
}
