package net.modtale.launcher.hytale;

public record HytaleGameSession(String sessionToken, String identityToken) {

    public boolean hasLaunchTokens() {
        return sessionToken != null && !sessionToken.isBlank()
                && identityToken != null && !identityToken.isBlank();
    }
}
