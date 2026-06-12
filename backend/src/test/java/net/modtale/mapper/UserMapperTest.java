package net.modtale.mapper;

import java.util.List;
import net.modtale.model.dto.user.UserDTO;
import net.modtale.model.dto.user.UserSummaryDTO;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserMapperTest {

    @Test
    void toSummaryDTOReturnsNullForMissingUsersAndMapsPublicFields() {
        assertNull(UserMapper.toSummaryDTO(null));

        User user = baseUser();
        UserSummaryDTO dto = UserMapper.toSummaryDTO(user);

        assertEquals("user-1", dto.id());
        assertEquals("ItsNeil17", dto.username());
        assertEquals("Always lock in", dto.bio());
        assertEquals(ApiKey.Tier.ENTERPRISE, dto.tier());
        assertEquals(User.AccountType.ORGANIZATION, dto.accountType());
    }

    @Test
    void toDTOStripsPrivateFieldsAndInvisibleConnectionsForPublicResponses() {
        User user = baseUser();

        UserDTO dto = UserMapper.toDTO(user, false);

        assertEquals("itsneil17@example.com", user.getEmail());
        assertEquals("ItsNeil17", dto.getUsername());
        assertEquals("Always lock in", dto.getBio());
        assertNull(dto.getEmail());
        assertNull(dto.getPendingOrgInvites());
        assertNull(dto.getLikedModIds());
        assertNull(dto.getNotificationPreferences());
        assertEquals(1, dto.getConnectedAccounts().size());
        assertEquals(OAuthProvider.GITHUB, dto.getConnectedAccounts().getFirst().getProvider());
        assertFalse(Boolean.TRUE.equals(dto.getMfaEnabled()));
    }

    @Test
    void toDTOIncludesPrivateFieldsForOwnersAndDefaultsMissingConnectionsToEmptyLists() {
        User user = baseUser();
        user.setConnectedAccounts(null);

        UserDTO dto = UserMapper.toDTO(user, true);

        assertEquals("itsneil17@example.com", dto.getEmail());
        assertEquals("ItsNeil17", dto.getUsername());
        assertEquals("Always lock in", dto.getBio());
        assertTrue(dto.getEmailVerified());
        assertTrue(dto.getMfaEnabled());
        assertNotNull(dto.getPendingOrgInvites());
        assertNotNull(dto.getLikedModIds());
        assertNotNull(dto.getNotificationPreferences());
        assertTrue(dto.getConnectedAccounts().isEmpty());
    }

    private static User baseUser() {
        User user = new User();
        user.setId("user-1");
        user.setUsername("ItsNeil17");
        user.setEmail("itsneil17@example.com");
        user.setEmailVerified(true);
        user.setMfaEnabled(true);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setBannerUrl("https://example.com/banner.png");
        user.setBio("Always lock in");
        user.setCreatedAt("2026-01-01");
        user.setTier(ApiKey.Tier.ENTERPRISE);
        user.setRoles(List.of("USER", "ADMIN"));
        user.setAccountType(User.AccountType.ORGANIZATION);
        user.setOrganizationRoles(List.of(new User.OrganizationRole()));
        user.setOrganizationMembers(List.of(new User.OrganizationMember("user-2", "role-1")));
        user.setPendingOrgInvites(List.of(new User.OrganizationMember("user-3", "role-2")));
        user.setLikedModIds(List.of("project-1"));
        user.setFollowingIds(List.of("user-2"));
        user.setFollowerIds(List.of("user-4"));
        user.setBadges(List.of("founder"));
        user.setConnectedAccounts(List.of(
                new User.ConnectedAccount(OAuthProvider.GITHUB, "gh-1", "ada", "https://github.com/ada", true),
                new User.ConnectedAccount(OAuthProvider.GITLAB, "gl-1", "ada", "https://gitlab.com/ada", false)
        ));
        return user;
    }
}
