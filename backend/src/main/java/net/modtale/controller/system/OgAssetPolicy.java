package net.modtale.controller.system;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.modtale.config.properties.AppR2Properties;

/** OG rendering only fetches assets from the site's own storage origins. */
final class OgAssetPolicy {
    private final Set<String> origins = new HashSet<>(Set.of("https://modtale.net", "https://cdn.modtale.net"));

    OgAssetPolicy(AppR2Properties properties) {
        String domain = properties.publicDomain();
        if (domain != null && !domain.isBlank()) {
            URI uri = URI.create(domain);
            if (origin(uri) != null) origins.add(origin(uri));
        }
    }

    URI resolve(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (uri.getRawUserInfo() != null || uri.getRawFragment() != null) return null;
            if (uri.getScheme() == null && uri.getRawAuthority() == null) {
                String path = uri.getPath();
                if (path == null || !path.equals(URI.create(path).normalize().getPath())) return null;
                if (path.startsWith("/assets/")) return URI.create("https://modtale.net").resolve(uri);
                if (path.startsWith("/api/v1/files/")) return URI.create("http://localhost:8080").resolve(uri);
                return null;
            }
            String origin = origin(uri);
            return origin != null && origins.contains(origin) ? uri : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String origin(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || uri.getHost() == null || uri.getRawUserInfo() != null
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) return null;
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (scheme.equalsIgnoreCase("https") ? port == 443 : port == 80);
        return scheme.toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                + (defaultPort ? "" : ":" + port);
    }
}
