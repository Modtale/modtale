package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.public-cache-purge")
public record PublicCachePurgeProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("") String zoneId,
    @DefaultValue("") String token) {
  // Configuration includes a credential; never include it in diagnostics.
  @Override
  public String toString() {
    return "PublicCachePurgeProperties[enabled=" + enabled + ", credentials=redacted]";
  }
}
