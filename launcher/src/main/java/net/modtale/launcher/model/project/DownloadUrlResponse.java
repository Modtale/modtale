package net.modtale.launcher.model.project;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DownloadUrlResponse(
        String downloadUrl,
        int expiresIn,
        String fileName,
        Long fileSize,
        Map<String, String> hashes,
        String source
) {
    public DownloadUrlResponse {
        hashes = hashes == null ? Map.of() : Map.copyOf(hashes);
    }

    public DownloadUrlResponse(String downloadUrl, int expiresIn) {
        this(downloadUrl, expiresIn, null, null, Map.of(), "MODTALE");
    }
}
