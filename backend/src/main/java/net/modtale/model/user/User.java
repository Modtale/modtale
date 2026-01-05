package net.modtale.model.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

@Document(collection = "users")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String email;

    private boolean emailVerified = false;

    @JsonIgnore
    private String verificationToken;

    @JsonIgnore
    private LocalDateTime verificationTokenExpiry;

    @JsonIgnore
    private String password;

    private String avatarUrl;
    private String bannerUrl;
    private String bio;
    private String createdAt;

    private ApiKey.Tier tier;
    private List<String> roles;

    private AccountType accountType = AccountType.USER;
    private List<OrganizationMember> organizationMembers = new ArrayList<>();

    private List<OrganizationMember> pendingOrgInvites = new ArrayList<>();

    private List<String> likedModIds = new ArrayList<>();
    private List<String> likedModpackIds = new ArrayList<>();

    private List<String> followingIds = new ArrayList<>();
    private List<String> followerIds = new ArrayList<>();

    private List<ConnectedAccount> connectedAccounts = new ArrayList<>();

    private List<String> badges = new ArrayList<>();

    private NotificationPreferences notificationPreferences = new NotificationPreferences();

    private String githubAccessToken;
    private String gitlabAccessToken;

    public User() {
        this.tier = ApiKey.Tier.USER;
        this.createdAt = LocalDate.now().toString();
        this.roles = new ArrayList<>();
        this.roles.add("USER");
    }

    public enum NotificationLevel {
        OFF, ON, EMAIL
    }

    public enum AccountType {
        USER, ORGANIZATION
    }

    public static class OrganizationMember implements Serializable {
        private static final long serialVersionUID = 1L;
        private String userId;
        private String role;

        public OrganizationMember() {}
        public OrganizationMember(String userId, String role) {
            this.userId = userId;
            this.role = role;
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public static class NotificationPreferences implements Serializable {
        private static final long serialVersionUID = 1L;

        private NotificationLevel projectUpdates = NotificationLevel.ON;
        private NotificationLevel creatorUploads = NotificationLevel.ON;
        private NotificationLevel newReviews = NotificationLevel.ON;
        private NotificationLevel newFollowers = NotificationLevel.ON;
        private NotificationLevel dependencyUpdates = NotificationLevel.ON;

        public NotificationLevel getProjectUpdates() { return projectUpdates; }
        public void setProjectUpdates(NotificationLevel projectUpdates) { this.projectUpdates = projectUpdates; }
        public NotificationLevel getCreatorUploads() { return creatorUploads; }
        public void setCreatorUploads(NotificationLevel creatorUploads) { this.creatorUploads = creatorUploads; }
        public NotificationLevel getNewReviews() { return newReviews; }
        public void setNewReviews(NotificationLevel newReviews) { this.newReviews = newReviews; }
        public NotificationLevel getNewFollowers() { return newFollowers; }
        public void setNewFollowers(NotificationLevel newFollowers) { this.newFollowers = newFollowers; }
        public NotificationLevel getDependencyUpdates() { return dependencyUpdates; }
        public void setDependencyUpdates(NotificationLevel dependencyUpdates) { this.dependencyUpdates = dependencyUpdates; }
    }

    public static class ConnectedAccount implements Serializable {
        private static final long serialVersionUID = 1L;

        private String provider;
        private String providerId;
        private String username;
        private String profileUrl;
        private boolean visible;

        public ConnectedAccount() {}
        public ConnectedAccount(String provider, String providerId, String username, String profileUrl, boolean visible) {
            this.provider = provider;
            this.providerId = providerId;
            this.username = username;
            this.profileUrl = profileUrl;
            this.visible = visible;
        }

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getProfileUrl() { return profileUrl; }
        public void setProfileUrl(String profileUrl) { this.profileUrl = profileUrl; }
        public boolean isVisible() { return visible; }
        public void setVisible(boolean visible) { this.visible = visible; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public LocalDateTime getVerificationTokenExpiry() { return verificationTokenExpiry; }
    public void setVerificationTokenExpiry(LocalDateTime verificationTokenExpiry) { this.verificationTokenExpiry = verificationTokenExpiry; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public ApiKey.Tier getTier() { return tier != null ? tier : ApiKey.Tier.USER; }
    public void setTier(ApiKey.Tier tier) { this.tier = tier; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    public List<OrganizationMember> getOrganizationMembers() { return organizationMembers; }
    public void setOrganizationMembers(List<OrganizationMember> organizationMembers) { this.organizationMembers = organizationMembers; }

    public List<OrganizationMember> getPendingOrgInvites() { return pendingOrgInvites; }
    public void setPendingOrgInvites(List<OrganizationMember> pendingOrgInvites) { this.pendingOrgInvites = pendingOrgInvites; }

    public List<String> getLikedModIds() { return likedModIds; }
    public void setLikedModIds(List<String> likedModIds) { this.likedModIds = likedModIds; }
    public List<String> getLikedModpackIds() { return likedModpackIds; }
    public void setLikedModpackIds(List<String> likedModpackIds) { this.likedModpackIds = likedModpackIds; }

    public List<String> getFollowingIds() { return followingIds; }
    public void setFollowingIds(List<String> followingIds) { this.followingIds = followingIds; }

    public List<String> getFollowerIds() { return followerIds; }
    public void setFollowerIds(List<String> followerIds) { this.followerIds = followerIds; }

    public List<ConnectedAccount> getConnectedAccounts() { return connectedAccounts; }
    public void setConnectedAccounts(List<ConnectedAccount> connectedAccounts) { this.connectedAccounts = connectedAccounts; }

    public List<String> getBadges() {
        if (createdAt != null && LocalDate.parse(createdAt).isBefore(LocalDate.of(2026, 1, 13))) {
            if (!badges.contains("OG")) {
                List<String> dynamicBadges = new ArrayList<>(badges);
                dynamicBadges.add("OG");
                return dynamicBadges;
            }
        }
        return badges;
    }
    public void setBadges(List<String> badges) { this.badges = badges; }

    public NotificationPreferences getNotificationPreferences() { return notificationPreferences; }
    public void setNotificationPreferences(NotificationPreferences notificationPreferences) { this.notificationPreferences = notificationPreferences; }

    public String getGithubAccessToken() { return githubAccessToken; }
    public void setGithubAccessToken(String githubAccessToken) { this.githubAccessToken = githubAccessToken; }

    public String getGitlabAccessToken() { return gitlabAccessToken; }
    public void setGitlabAccessToken(String gitlabAccessToken) { this.gitlabAccessToken = gitlabAccessToken; }
}