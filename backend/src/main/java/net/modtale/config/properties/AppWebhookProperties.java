package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.webhook")
public record AppWebhookProperties(
        @DefaultValue("") String url,
        @DefaultValue("") String key
) {
}
