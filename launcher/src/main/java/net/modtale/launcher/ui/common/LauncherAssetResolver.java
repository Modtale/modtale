package net.modtale.launcher.ui.common;

import java.util.Objects;
import java.util.function.Supplier;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.settings.LauncherConfig;

public final class LauncherAssetResolver {

    private final ModtaleApiClient apiClient;
    private final Supplier<String> fallbackAssetUrl;

    public LauncherAssetResolver(ModtaleApiClient apiClient, Supplier<String> fallbackAssetUrl) {
        this.apiClient = apiClient;
        this.fallbackAssetUrl = fallbackAssetUrl;
    }

    public String resolve(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Objects.requireNonNull(fallbackAssetUrl.get());
        }
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl;
        }
        if (rawUrl.startsWith("/api")) {
            return apiRoot() + rawUrl;
        }
        if (rawUrl.startsWith("/")) {
            return LauncherConfig.siteBaseUrl().replaceAll("/+$", "") + rawUrl;
        }
        return rawUrl;
    }

    public String resolveBackendAsset(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Objects.requireNonNull(fallbackAssetUrl.get());
        }
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl;
        }
        if (rawUrl.startsWith("/")) {
            return apiRoot() + rawUrl;
        }
        return rawUrl;
    }

    private String apiRoot() {
        var uri = apiClient.apiBaseUri();
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port < 0 ? "" : ":" + port);
    }
}
