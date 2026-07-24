package net.modtale.service.auth;

import java.util.Map;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.model.user.OAuthProvider;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class OAuthProviderProfileService {

    public OAuthProviderProfile extract(String providerStr, OAuth2User oauthUser) {
        OAuthProvider provider = OAuthProvider.fromString(providerStr);
        if (provider == null) {
            throw new InvalidAuthenticationRequestException("That OAuth provider is not supported.");
        }

        String providerId = extractProviderId(providerStr, oauthUser);
        String username = extractUsername(providerStr, oauthUser);
        String avatarUrl = extractAvatarUrl(providerStr, oauthUser, providerId);
        String email = oauthUser.getAttribute("email");
        String profileUrl = extractProfileUrl(providerStr, oauthUser, username, providerId);

        return new OAuthProviderProfile(
                provider,
                providerId,
                username,
                avatarUrl,
                email,
                profileUrl,
                provider != OAuthProvider.GOOGLE && provider != OAuthProvider.HYTALE
        );
    }

    private String extractProviderId(String provider, OAuth2User user) {
        if ("twitter".equals(provider)) {
            Map<String, Object> data = user.getAttribute("data");
            if (data != null) {
                return String.valueOf(data.get("id"));
            }
        }
        if ("bluesky".equals(provider)) {
            return user.getAttribute("did");
        }
        if ("google".equals(provider)) {
            return user.getAttribute("sub");
        }
        if ("hytale".equals(provider)) {
            return user.getAttribute("sub");
        }

        Object id = user.getAttribute("id");
        return id != null ? String.valueOf(id) : user.getName();
    }

    private String extractUsername(String provider, OAuth2User user) {
        if ("twitter".equals(provider)) {
            Map<String, Object> data = user.getAttribute("data");
            if (data != null) {
                return (String) data.get("username");
            }
        }
        if ("bluesky".equals(provider)) {
            return user.getAttribute("handle");
        }
        if ("discord".equals(provider)) {
            String name = user.getAttribute("username");
            String discriminator = user.getAttribute("discriminator");
            return (discriminator != null && !discriminator.equals("0")) ? name + "#" + discriminator : name;
        }
        if ("google".equals(provider)) {
            String name = user.getAttribute("name");
            if (name == null) {
                name = user.getAttribute("email");
            }
            if (name == null) {
                name = "User";
            }
            return name.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
        }
        if ("hytale".equals(provider)) {
            Map<String, Object> profile = user.getAttribute("profile");
            Object username = profile != null ? profile.get("username") : null;
            return username != null ? String.valueOf(username) : "hytale_player";
        }

        String login = user.getAttribute("login");
        String username = user.getAttribute("username");
        return login != null ? login : (username != null ? username : user.getName());
    }

    private String extractProfileUrl(String provider, OAuth2User user, String username, String providerId) {
        if ("discord".equals(provider)) {
            return "https://discord.com/users/" + providerId;
        }
        if ("gitlab".equals(provider)) {
            return user.getAttribute("web_url");
        }
        if ("twitter".equals(provider)) {
            return "https://twitter.com/" + username;
        }
        if ("github".equals(provider)) {
            return "https://github.com/" + username;
        }
        if ("bluesky".equals(provider)) {
            return "https://bsky.app/profile/" + username;
        }
        return "";
    }

    private String extractAvatarUrl(String provider, OAuth2User user, String providerId) {
        if ("discord".equals(provider)) {
            String avatar = user.getAttribute("avatar");
            if (avatar != null) {
                return "https://cdn.discordapp.com/avatars/" + providerId + "/" + avatar + ".png";
            }
        }
        if ("bluesky".equals(provider)) {
            return user.getAttribute("avatar");
        }
        if ("google".equals(provider)) {
            return user.getAttribute("picture");
        }
        return user.getAttribute("avatar_url");
    }
}
