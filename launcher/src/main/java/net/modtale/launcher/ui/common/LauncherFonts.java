package net.modtale.launcher.ui.common;

import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.text.Font;

public final class LauncherFonts {

    private static final AtomicBoolean LOADED = new AtomicBoolean();
    private static final String[] INTER_FONTS = {
            "/net/modtale/launcher/ui/nativefx/fonts/Inter-Regular.ttf",
            "/net/modtale/launcher/ui/nativefx/fonts/Inter-Bold.ttf",
            "/net/modtale/launcher/ui/nativefx/fonts/Inter-Black.ttf"
    };

    private LauncherFonts() {
    }

    public static void load() {
        if (!LOADED.compareAndSet(false, true)) {
            return;
        }
        for (String fontPath : INTER_FONTS) {
            URL font = LauncherFonts.class.getResource(fontPath);
            if (font != null) {
                Font.loadFont(font.toExternalForm(), 16);
            }
        }
    }
}
