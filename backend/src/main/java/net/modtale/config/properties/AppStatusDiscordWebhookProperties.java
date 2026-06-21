package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.status-discord-webhook")
public record AppStatusDiscordWebhookProperties(
        @DefaultValue("") String url
) {
}
