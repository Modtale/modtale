package net.modtale.launcher.model.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentUser(
        String id,
        String username,
        String avatarUrl,
        String email,
        Boolean emailVerified,
        String tier,
        List<String> likedProjectIds,
        List<String> followingIds,
        List<String> adminPermissions,
        NotificationPreferences notificationPreferences
) {
    public CurrentUser {
        likedProjectIds = likedProjectIds == null ? List.of() : List.copyOf(likedProjectIds);
        followingIds = followingIds == null ? List.of() : List.copyOf(followingIds);
        adminPermissions = adminPermissions == null ? List.of() : List.copyOf(adminPermissions);
        notificationPreferences = notificationPreferences == null
                ? NotificationPreferences.defaults()
                : notificationPreferences;
    }

    public boolean likesProject(String projectId) {
        return projectId != null && likedProjectIds.contains(projectId);
    }

    public boolean followsUser(String userId) {
        return userId != null && followingIds.contains(userId);
    }

    public boolean hasAdminPermission(String permission) {
        return permission != null && adminPermissions.stream().anyMatch(permission::equalsIgnoreCase);
    }

    @Override
    public String toString() {
        return username == null || username.isBlank() ? id : username;
    }

    public record NotificationPreferences(
            String projectUpdates,
            String creatorUploads,
            String newComments,
            String newFollowers,
            String dependencyUpdates
    ) {
        public NotificationPreferences {
            projectUpdates = level(projectUpdates);
            creatorUploads = level(creatorUploads);
            newComments = level(newComments);
            newFollowers = level(newFollowers);
            dependencyUpdates = level(dependencyUpdates);
        }

        public static NotificationPreferences defaults() {
            String enabled = NotificationLevel.ON.apiValue();
            return new NotificationPreferences(enabled, enabled, enabled, enabled, enabled);
        }

        private static String level(String value) {
            return NotificationLevel.fromApiValue(value).apiValue();
        }
    }

    public enum NotificationLevel {
        ON,
        OFF;

        public String apiValue() {
            return name();
        }

        public static NotificationLevel fromApiValue(String value) {
            if (value == null || value.isBlank()) {
                return ON;
            }
            for (NotificationLevel level : values()) {
                if (level.name().equalsIgnoreCase(value.trim())) {
                    return level;
                }
            }
            return ON;
        }
    }
}
