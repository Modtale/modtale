package net.modtale.model.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.AdminPermission;
import net.modtale.model.user.User;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    private String id;
    private String username;
    private String email;
    private Boolean emailVerified;
    private Boolean hasPassword;
    private Boolean mfaEnabled;
    private String avatarUrl;
    private String bannerUrl;
    private String bio;
    private String createdAt;
    private ApiKey.Tier tier;
    private List<String> roles;
    private Set<AdminPermission> adminPermissions;
    private User.AccountType accountType;
    private List<User.OrganizationRole> organizationRoles;
    private List<User.OrganizationMember> organizationMembers;
    private List<User.OrganizationMember> pendingOrgInvites;
    private List<String> likedModIds;
    private List<String> followingIds;
    private List<String> followerIds;
    private List<User.ConnectedAccount> connectedAccounts;
    private List<String> badges;
    private User.NotificationPreferences notificationPreferences;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }
    public Boolean getHasPassword() { return hasPassword; }
    public void setHasPassword(Boolean hasPassword) { this.hasPassword = hasPassword; }
    public Boolean getMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(Boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public ApiKey.Tier getTier() { return tier; }
    public void setTier(ApiKey.Tier tier) { this.tier = tier; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public Set<AdminPermission> getAdminPermissions() { return adminPermissions; }
    public void setAdminPermissions(Set<AdminPermission> adminPermissions) { this.adminPermissions = adminPermissions; }
    public User.AccountType getAccountType() { return accountType; }
    public void setAccountType(User.AccountType accountType) { this.accountType = accountType; }
    public List<User.OrganizationRole> getOrganizationRoles() { return organizationRoles; }
    public void setOrganizationRoles(List<User.OrganizationRole> organizationRoles) { this.organizationRoles = organizationRoles; }
    public List<User.OrganizationMember> getOrganizationMembers() { return organizationMembers; }
    public void setOrganizationMembers(List<User.OrganizationMember> organizationMembers) { this.organizationMembers = organizationMembers; }
    public List<User.OrganizationMember> getPendingOrgInvites() { return pendingOrgInvites; }
    public void setPendingOrgInvites(List<User.OrganizationMember> pendingOrgInvites) { this.pendingOrgInvites = pendingOrgInvites; }
    public List<String> getLikedModIds() { return likedModIds; }
    public void setLikedModIds(List<String> likedModIds) { this.likedModIds = likedModIds; }
    @JsonProperty("likedProjectIds")
    public List<String> getLikedProjectIds() { return likedModIds; }
    @JsonProperty("likedProjectIds")
    public void setLikedProjectIds(List<String> likedProjectIds) { this.likedModIds = likedProjectIds; }
    public List<String> getFollowingIds() { return followingIds; }
    public void setFollowingIds(List<String> followingIds) { this.followingIds = followingIds; }
    public List<String> getFollowerIds() { return followerIds; }
    public void setFollowerIds(List<String> followerIds) { this.followerIds = followerIds; }
    public List<User.ConnectedAccount> getConnectedAccounts() { return connectedAccounts; }
    public void setConnectedAccounts(List<User.ConnectedAccount> connectedAccounts) { this.connectedAccounts = connectedAccounts; }
    public List<String> getBadges() { return badges; }
    public void setBadges(List<String> badges) { this.badges = badges; }
    public User.NotificationPreferences getNotificationPreferences() { return notificationPreferences; }
    public void setNotificationPreferences(User.NotificationPreferences notificationPreferences) { this.notificationPreferences = notificationPreferences; }
}
