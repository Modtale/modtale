package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.hytalemodding")
public record AppWikiProperties(
        @DefaultValue("") String wikiKey,
        @DefaultValue("https://wiki.hytalemodding.dev/api") String wikiUrl
) {
}
