package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.hytale.maven")
public record AppGameVersionProperties(
        @DefaultValue("https://maven.hytale.com/release/com/hypixel/hytale/Server/maven-metadata.xml") String releaseUrl,
        @DefaultValue("https://maven.hytale.com/pre-release/com/hypixel/hytale/Server/maven-metadata.xml") String preReleaseUrl,
        @DefaultValue("3600000") long pollMs
) {
}
