package net.modtale.model.user;

public enum OAuthProvider {
    GITHUB,
    GITLAB,
    GOOGLE,
    DISCORD,
    TWITTER,
    BLUESKY;

    public static OAuthProvider fromString(String provider) {
        if (provider == null) return null;
        try {
            return OAuthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}