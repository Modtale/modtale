package net.modtale.launcher.ui.common;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import net.modtale.launcher.model.project.*;
import net.modtale.launcher.model.user.*;
import net.modtale.launcher.model.notification.LauncherNotification;

/** Inert, asset-free sizing content. Only pass to fresh render trees that are immediately masked. */
public final class LauncherSkeletonContent {
    private LauncherSkeletonContent() {}
    public static final String DATE = "2026-01-01T12:00:00Z";
    public static final String PROSE = "Discover new possibilities for your world with this community project.\n\n"
            + "## Getting started\n\nInstall the latest release and explore the available features.\n\n"
            + "- Configure your world\n- Explore new content\n- Play with friends";

    public static ProjectVersion version() {
        return new ProjectVersion("loading-version", "1.2.0", List.of("2026.1"), "", 1200,
                DATE, "## What's new\n\nImproved compatibility and new features for your world.", List.of(), "RELEASE");
    }
    public static ProjectSummary project() {
        return new ProjectSummary("loading-project", "loading-project", "Community Project",
                "Discover new possibilities for your world with this community project.", "", "Creator", "", "",
                "MOD", 12000, 900, DATE, List.of(version()));
    }
    public static ProjectDetail detail() {
        ProjectSummary p = project();
        return new ProjectDetail(p.id(), p.slug(), p.title(), PROSE, p.description(), "", p.author(), "", "",
                "MOD", p.downloadCount(), p.favoriteCount(), DATE, "MIT", "", Map.of(),
                List.of("Utility", "Multiplayer"), List.of(), Map.of(), true, true, "wiki", List.of(version()));
    }
    public static CreatorProfile creator() {
        return new CreatorProfile("loading-creator", "Community Creator", "", "",
                "Building new experiences for the Hytale community.", DATE, "CREATOR", List.of(), "USER",
                List.of(), List.of("follower"), List.of(), List.of(), List.of(), List.of());
    }
    public static UserSummary user() {
        return new UserSummary("loading-user", "Community Creator", "", "", "", DATE, "CREATOR",
                List.of("CREATOR"), "USER", List.of());
    }
    public static ProjectComment comment() {
        return new ProjectComment("loading-comment", "", "Community member", null,
                "Thanks for sharing this project! Looking forward to exploring the latest update.", DATE, DATE,
                false, 12, 0, null, List.of(), List.of(), null, false, List.of());
    }
    public static LauncherNotification notification() {
        return new LauncherNotification("loading-notification", "New project release",
                "A creator you follow published a new update.", "", "", false, "PROJECT_UPDATE", Map.of(),
                LocalDateTime.of(2026, 1, 1, 12, 0));
    }
    public static WikiBundle wiki() {
        var json = JsonNodeFactory.instance;
        var metadata = json.objectNode();
        var pages = metadata.putArray("pages");
        for (String title : List.of("Overview", "Installation", "Configuration", "Reference")) {
            pages.addObject().put("id", title).put("slug", title).put("title", title);
        }
        return new WikiBundle(null, metadata,
                json.objectNode().put("title", "Getting Started").put("content", PROSE), "Overview");
    }
}
