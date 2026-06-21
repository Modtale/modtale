package net.modtale.launcher.ui.common;

import java.awt.Desktop;
import java.net.URI;
import java.util.function.BiConsumer;
import net.modtale.launcher.logging.LogSanitizer;
import net.modtale.launcher.settings.LauncherConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LauncherExternalLinks {

    private static final Logger LOG = LogManager.getLogger(LauncherExternalLinks.class);

    private LauncherExternalLinks() {
    }

    public static void open(String rawLink, BiConsumer<String, String> toast) {
        URI uri = resolve(rawLink);
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            showError(toast, "Desktop browser integration is not available.");
            return;
        }
        try {
            Desktop.getDesktop().browse(uri);
        } catch (Exception ex) {
            LOG.warn("Could not open {}", LogSanitizer.uri(uri), ex);
            showError(toast, "Could not open " + uri + ".");
        }
    }

    public static URI resolve(String rawLink) {
        String link = rawLink == null || rawLink.isBlank() ? "/" : rawLink.trim();
        if (link.startsWith("http://") || link.startsWith("https://")) {
            return URI.create(link);
        }
        String base = LauncherConfig.siteBaseUrl().replaceAll("/+$", "");
        String path = link.startsWith("/") ? link : "/" + link;
        return URI.create(base + path);
    }

    private static void showError(BiConsumer<String, String> toast, String message) {
        if (toast != null) {
            toast.accept("Could not open link", message);
        }
    }
}
