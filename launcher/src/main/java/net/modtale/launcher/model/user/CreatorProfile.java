package net.modtale.launcher.model.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreatorProfile(
        @JsonAlias("_id") String id,
        String username,
        String avatarUrl,
        String bannerUrl,
        String bio,
        String createdAt,
        String tier,
        List<String> roles,
        String accountType,
        List<String> badges,
        List<String> followerIds,
        List<String> followingIds,
        List<ConnectedAccount> connectedAccounts,
        List<OrganizationMember> organizationMembers,
        List<OrganizationRole> organizationRoles
) {
    public CreatorProfile {
        roles = roles == null ? List.of() : List.copyOf(roles);
        badges = badges == null ? List.of() : List.copyOf(badges);
        followerIds = followerIds == null ? List.of() : List.copyOf(followerIds);
        followingIds = followingIds == null ? List.of() : List.copyOf(followingIds);
        connectedAccounts = connectedAccounts == null ? List.of() : List.copyOf(connectedAccounts);
        organizationMembers = organizationMembers == null ? List.of() : List.copyOf(organizationMembers);
        organizationRoles = organizationRoles == null ? List.of() : List.copyOf(organizationRoles);
    }

    public boolean organization() {
        return "ORGANIZATION".equalsIgnoreCase(accountType);
    }

    @Override
    public String toString() {
        return username == null || username.isBlank() ? id : username;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConnectedAccount(
            String provider,
            String providerId,
            String username,
            String profileUrl,
            Boolean visible
    ) {
        public boolean isVisible() {
            return Boolean.TRUE.equals(visible);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrganizationMember(
            String userId,
            String roleId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrganizationRole(
            String id,
            String name,
            String color,
            List<String> permissions,
            Boolean owner
    ) {
        public OrganizationRole {
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }
}
