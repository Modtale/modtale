package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.discord-webhook")
public record AppDiscordWebhookProperties(
        @DefaultValue("") String url
) {
}
