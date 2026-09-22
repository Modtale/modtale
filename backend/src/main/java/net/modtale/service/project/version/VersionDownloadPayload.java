package net.modtale.service.project.version;

public record VersionDownloadPayload(String filename, byte[] bytes, java.net.URI redirectUri) {
    public VersionDownloadPayload(String filename, byte[] bytes) {
        this(filename, bytes, null);
    }
}
