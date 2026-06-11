package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.seeding")
public record AppSeedingProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("modtale") String sourceDb
) {
}
