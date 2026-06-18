package net.modtale.service.communication;

public final class DiscordWebhookIdentity {

    public static final String ADMIN_BOT_USERNAME = "Modtale Admin Bot";

    private DiscordWebhookIdentity() {
    }

    public static String adminBotAvatarUrl(String frontendUrl) {
        return cleanFrontendUrl(frontendUrl) + "/assets/favicon.png";
    }

    private static String cleanFrontendUrl(String frontendUrl) {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "https://modtale.net";
        }

        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }
}
