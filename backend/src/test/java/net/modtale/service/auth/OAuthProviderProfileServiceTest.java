package net.modtale.service.auth;

import java.util.Map;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.model.user.OAuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthProviderProfileServiceTest {

    private final OAuthProviderProfileService service = new OAuthProviderProfileService();

    @Test
    void extractBuildsDiscordProfileWithDiscriminatorAndCdnAvatar() {
        OAuthProviderProfile profile = service.extract("discord", oauthUser(Map.of(
                "id", "discord-1",
                "username", "Willow",
                "discriminator", "1234",
                "avatar", "avatar-hash",
                "email", "willow@example.com"
        ), "discord-1"));

        assertEquals(OAuthProvider.DISCORD, profile.provider());
        assertEquals("discord-1", profile.providerId());
        assertEquals("Willow#1234", profile.username());
        assertEquals("https://cdn.discordapp.com/avatars/discord-1/avatar-hash.png", profile.avatarUrl());
        assertEquals("https://discord.com/users/discord-1", profile.profileUrl());
        assertTrue(profile.visible());
    }

    @Test
    void extractBuildsGoogleProfileWithSanitizedNameAndHiddenVisibility() {
        OAuthProviderProfile profile = service.extract("google", oauthUser(Map.of(
                "sub", "google-1",
                "name", "Willow Branch!",
                "picture", "https://example.com/avatar.png",
                "email", "willow@example.com"
        ), "google-1"));

        assertEquals(OAuthProvider.GOOGLE, profile.provider());
        assertEquals("google-1", profile.providerId());
        assertEquals("WillowBranch", profile.username());
        assertEquals("https://example.com/avatar.png", profile.avatarUrl());
        assertFalse(profile.visible());
    }

    @Test
    void extractUsesTwitterDataEnvelope() {
        OAuthProviderProfile profile = service.extract("twitter", oauthUser(Map.of(
                "data", Map.of("id", "tw-1", "username", "modtale_dev")
        ), "fallback"));

        assertEquals(OAuthProvider.TWITTER, profile.provider());
        assertEquals("tw-1", profile.providerId());
        assertEquals("modtale_dev", profile.username());
        assertEquals("https://twitter.com/modtale_dev", profile.profileUrl());
    }

    @Test
    void extractUsesBlueskyDidHandleAvatarAndProfileUrl() {
        OAuthProviderProfile profile = service.extract("bluesky", oauthUser(Map.of(
                "did", "did:plc:abc",
                "handle", "willow.bsky.social",
                "avatar", "https://cdn.example/avatar.jpg"
        ), "fallback"));

        assertEquals(OAuthProvider.BLUESKY, profile.provider());
        assertEquals("did:plc:abc", profile.providerId());
        assertEquals("willow.bsky.social", profile.username());
        assertEquals("https://cdn.example/avatar.jpg", profile.avatarUrl());
        assertEquals("https://bsky.app/profile/willow.bsky.social", profile.profileUrl());
    }

    @Test
    void extractFallsBackToNameForGenericProviderIdentity() {
        OAuthProviderProfile profile = service.extract("github", oauthUser(Map.of(
                "avatar_url", "https://example.com/avatar.png"
        ), "octo-user"));

        assertEquals(OAuthProvider.GITHUB, profile.provider());
        assertEquals("octo-user", profile.providerId());
        assertEquals("octo-user", profile.username());
        assertEquals("https://github.com/octo-user", profile.profileUrl());
    }

    @Test
    void extractRejectsUnsupportedProviders() {
        assertThrows(
                InvalidAuthenticationRequestException.class,
                () -> service.extract("spacebook", oauthUser(Map.of("id", "1"), "1"))
        );
    }

    private static OAuth2User oauthUser(Map<String, Object> attributes, String name) {
        return new DefaultOAuth2User(
                java.util.List.of(() -> "ROLE_USER"),
                attributes,
                attributes.containsKey("login") ? "login" : attributes.keySet().iterator().next()
        ) {
            @Override
            public String getName() {
                return name;
            }
        };
    }
}
