package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.limits")
public record AppLimitProperties(
        @DefaultValue("10") int maxApiKeysPerUser,
        @DefaultValue("5") int maxOrgsPerUser,
        @DefaultValue("10") int reportsPerDay,
        @DefaultValue("5") int rescansPerDay,
        @DefaultValue("5") int maxVersionsPerDay,
        @DefaultValue("50") int maxProjectsPerUser,
        @DefaultValue("20") int maxGalleryImagesPerProject,
        @DefaultValue("10") int modpackGenPerHour
) {
}
