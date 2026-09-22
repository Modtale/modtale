package net.modtale.launcher.news;

import java.time.Instant;

public record LauncherNewsPost(String title, String url, String imageUrl, Instant publishedAt, String source) {}
