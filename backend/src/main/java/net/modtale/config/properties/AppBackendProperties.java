package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.backend")
public record AppBackendProperties(
        @DefaultValue("http://localhost:8080") String url
) {
}
