package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.modjam-discord-feed")
public record AppModjamDiscordFeedProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String url
) {
    @Override
    public String toString() {
        return "AppModjamDiscordFeedProperties[enabled=" + enabled + ", urlConfigured="
                + (url != null && !url.isBlank()) + "]";
    }
}
