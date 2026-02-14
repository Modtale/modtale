package net.modtale.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    private String id;
    private String username;
    private String email;
    private Boolean emailVerified;
    private Boolean mfaEnabled;
    private String avatarUrl;
    private String bannerUrl;
    private String bio;
    private String createdAt;
    private ApiKey.Tier tier;
    private List<String> roles;
    private User.AccountType accountType;
    private List<User.OrganizationMember> organizationMembers;
    private List<User.OrganizationMember> pendingOrgInvites;
    private List<String> likedModIds;
    private List<String> followingIds;
    private List<String> followerIds;
    private List<User.ConnectedAccount> connectedAccounts;
    private List<String> badges;
    private User.NotificationPreferences notificationPreferences;

    public static UserDTO fromEntity(User user, boolean includePrivate) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();

        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setBannerUrl(user.getBannerUrl());
        dto.setBio(user.getBio());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setTier(user.getTier());
        dto.setRoles(user.getRoles());
        dto.setAccountType(user.getAccountType());
        dto.setBadges(user.getBadges());

        dto.setFollowingIds(user.getFollowingIds());
        dto.setFollowerIds(user.getFollowerIds());
        dto.setOrganizationMembers(user.getOrganizationMembers());

        if (user.getConnectedAccounts() != null) {
            if (includePrivate) {
                dto.setConnectedAccounts(user.getConnectedAccounts());
            } else {
                dto.setConnectedAccounts(user.getConnectedAccounts().stream()
                        .filter(User.ConnectedAccount::isVisible)
                        .collect(Collectors.toList()));
            }
        } else {
            dto.setConnectedAccounts(new ArrayList<>());
        }

        if (includePrivate) {
            dto.setEmail(user.getEmail());
            dto.setEmailVerified(user.isEmailVerified());
            dto.setMfaEnabled(user.isMfaEnabled());
            dto.setPendingOrgInvites(user.getPendingOrgInvites());
            dto.setLikedModIds(user.getLikedModIds());
            dto.setNotificationPreferences(user.getNotificationPreferences());
        } else {
            dto.setEmail(null);
            dto.setPendingOrgInvites(null);
            dto.setNotificationPreferences(null);
            dto.setLikedModIds(null);
        }

        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }
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
    public User.AccountType getAccountType() { return accountType; }
    public void setAccountType(User.AccountType accountType) { this.accountType = accountType; }
    public List<User.OrganizationMember> getOrganizationMembers() { return organizationMembers; }
    public void setOrganizationMembers(List<User.OrganizationMember> organizationMembers) { this.organizationMembers = organizationMembers; }
    public List<User.OrganizationMember> getPendingOrgInvites() { return pendingOrgInvites; }
    public void setPendingOrgInvites(List<User.OrganizationMember> pendingOrgInvites) { this.pendingOrgInvites = pendingOrgInvites; }
    public List<String> getLikedModIds() { return likedModIds; }
    public void setLikedModIds(List<String> likedModIds) { this.likedModIds = likedModIds; }
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