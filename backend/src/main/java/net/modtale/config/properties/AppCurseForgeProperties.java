package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.curseforge")
public record AppCurseForgeProperties(
        String apiKey,
        long hytaleGameId
) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && hytaleGameId > 0;
    }
}
