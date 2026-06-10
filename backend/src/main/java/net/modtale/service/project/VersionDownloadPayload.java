package net.modtale.service.project;

public record VersionDownloadPayload(String filename, byte[] bytes) {
}
