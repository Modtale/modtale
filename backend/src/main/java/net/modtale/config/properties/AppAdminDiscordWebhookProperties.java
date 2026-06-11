package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.admin-discord-webhook")
public record AppAdminDiscordWebhookProperties(
        @DefaultValue("") String url
) {
}
