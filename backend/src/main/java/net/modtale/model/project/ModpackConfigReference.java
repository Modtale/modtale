package net.modtale.model.project;

public record ModpackConfigReference(String projectId, String source, String path, String sha256) {
    public String ownerKey() { return source + ":" + projectId; }
}
