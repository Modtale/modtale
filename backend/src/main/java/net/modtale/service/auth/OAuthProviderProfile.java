package net.modtale.service.auth;

import net.modtale.model.user.OAuthProvider;

public record OAuthProviderProfile(
        OAuthProvider provider,
        String providerId,
        String username,
        String avatarUrl,
        String email,
        String profileUrl,
        boolean visible
) {
}
