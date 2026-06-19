package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.seeding")
public record AppSeedingProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("clone") Mode mode,
        @DefaultValue("false") boolean reset,
        @DefaultValue("modtale") String sourceDb
) {
    public enum Mode {
        MOCK,
        TEMPLATE,
        CLONE
    }
}
