package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.frontend")
public record AppFrontendProperties(
        @DefaultValue("http://localhost:5173") String url
) {
}
