package net.modtale.model.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Document(collection = "api_keys")
public class ApiKey {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;
    private String keyHash;
    private String prefix;

    private Tier tier;

    private Map<String, Set<ApiPermission>> contextPermissions;

    private LocalDateTime lastUsed;
    private LocalDateTime createdAt;

    public enum Tier {
        USER,
        ENTERPRISE
    }

    public enum ApiPermission {
        PROJECT_READ, PROJECT_CREATE, PROJECT_EDIT_METADATA, PROJECT_EDIT_ICON, PROJECT_EDIT_BANNER,
        PROJECT_DELETE, PROJECT_TRANSFER_REQUEST, PROJECT_TRANSFER_RESOLVE, PROJECT_FAVORITE,
        PROJECT_STATUS_SUBMIT, PROJECT_STATUS_REVERT, PROJECT_STATUS_ARCHIVE, PROJECT_STATUS_UNLIST,
        PROJECT_STATUS_PUBLISH, PROJECT_GALLERY_ADD, PROJECT_GALLERY_REMOVE, PROJECT_TEAM_INVITE,
        PROJECT_TEAM_REMOVE, PROJECT_MEMBER_EDIT_ROLE, VERSION_READ, VERSION_CREATE, VERSION_EDIT,
        VERSION_DELETE, VERSION_DOWNLOAD, COMMENT_READ, COMMENT_CREATE, COMMENT_EDIT, COMMENT_DELETE,
        COMMENT_REPLY, ORG_READ, ORG_CREATE, ORG_EDIT_METADATA, ORG_EDIT_AVATAR, ORG_EDIT_BANNER, ORG_DELETE,
        ORG_MEMBER_READ, ORG_MEMBER_INVITE, ORG_MEMBER_REMOVE, ORG_MEMBER_EDIT_ROLE, ORG_INVITE_ACCEPT,
        ORG_INVITE_DECLINE, ORG_CONNECTION_MANAGE, PROFILE_READ, PROFILE_EDIT_BASIC, PROFILE_EDIT_AVATAR,
        PROFILE_EDIT_BANNER, PROFILE_DELETE, PROFILE_FOLLOW, PROFILE_UNFOLLOW, PROFILE_CONNECTION_MANAGE,
        PROFILE_NOTIFICATION_MANAGE, NOTIFICATION_READ, NOTIFICATION_UPDATE, NOTIFICATION_DELETE
    }

    public ApiKey() {}

    public ApiKey(String userId, String name, String keyHash, String prefix) {
        this.userId = userId;
        this.name = name;
        this.keyHash = keyHash;
        this.prefix = prefix;
        this.tier = Tier.USER;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public Tier getTier() { return tier != null ? tier : Tier.USER; }
    public void setTier(Tier tier) { this.tier = tier; }

    public Map<String, Set<ApiPermission>> getContextPermissions() {
        if (this.contextPermissions == null || this.contextPermissions.isEmpty()) {
            Map<String, Set<ApiPermission>> legacy = new HashMap<>();
            legacy.put("PERSONAL", EnumSet.allOf(ApiPermission.class));
            return legacy;
        }
        return this.contextPermissions;
    }

    public void setContextPermissions(Map<String, Set<ApiPermission>> contextPermissions) {
        this.contextPermissions = contextPermissions;
    }

    public LocalDateTime getLastUsed() { return lastUsed; }
    public void setLastUsed(LocalDateTime lastUsed) { this.lastUsed = lastUsed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}