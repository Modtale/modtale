package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.warden")
public record AppWardenProperties(
        @DefaultValue("http://localhost:8081") String url,
        @DefaultValue("") String apiKey,
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("75") long requestTimeoutSeconds
) {
}
