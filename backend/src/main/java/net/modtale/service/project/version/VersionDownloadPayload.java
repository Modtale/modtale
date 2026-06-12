package net.modtale.service.project.version;

public record VersionDownloadPayload(String filename, byte[] bytes) {
}
