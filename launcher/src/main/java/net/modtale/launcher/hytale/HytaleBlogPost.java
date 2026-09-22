package net.modtale.launcher.hytale;

import java.time.Instant;

public record HytaleBlogPost(String title, String url, String imageUrl, Instant publishedAt) {

    public HytaleBlogPost {
        title = title == null ? "" : title.trim();
        url = url == null ? "" : url.trim();
        imageUrl = imageUrl == null ? "" : imageUrl.trim();
        publishedAt = publishedAt == null ? Instant.EPOCH : publishedAt;
    }
}
