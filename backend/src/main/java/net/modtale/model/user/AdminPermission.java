package net.modtale.model.user;

import java.util.EnumSet;
import java.util.Set;

public enum AdminPermission {
    PROJECT_REVIEW_READ,
    PROJECT_REVIEW_DECIDE,
    PROJECT_MANAGE_READ,
    PROJECT_MODERATE,
    PROJECT_DELETE,
    PROJECT_RESTORE,
    PROJECT_VERSION_DELETE,
    PROJECT_VERSION_RESCAN,
    PROJECT_RAW_EDIT,
    REPORT_READ,
    REPORT_RESOLVE,
    USER_READ,
    USER_DELETE,
    EMAIL_BAN_READ,
    EMAIL_BAN_MANAGE,
    USER_TIER_MANAGE,
    USER_PERMISSION_MANAGE,
    USER_RAW_READ,
    USER_RAW_EDIT,
    AUDIT_LOG_READ,
    PLATFORM_ANALYTICS_READ,
    STATUS_INCIDENT_READ,
    STATUS_INCIDENT_MANAGE;

    public static Set<AdminPermission> allPermissions() {
        return EnumSet.allOf(AdminPermission.class);
    }

    public static boolean hasAnyPermission(User user) {
        return !effectivePermissions(user).isEmpty();
    }

    public static boolean hasPermission(User user, AdminPermission permission) {
        return permission != null && effectivePermissions(user).contains(permission);
    }

    public static Set<AdminPermission> effectivePermissions(User user) {
        EnumSet<AdminPermission> permissions = EnumSet.noneOf(AdminPermission.class);
        if (user == null) {
            return permissions;
        }

        if (user.getAdminPermissions() != null) {
            permissions.addAll(user.getAdminPermissions());
        }

        return permissions;
    }
}
