package net.modtale.launcher.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Opens local files using the desktop's registered application. */
public final class SystemFileOpener {
    private SystemFileOpener() {}

    public static void open(Path file) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new IOException("File does not exist: " + absolute);
        }
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            IOException failure = null;
            for (List<String> command : linuxCommands(absolute)) {
                try {
                    SystemBrowser.startCommand(command);
                    return;
                } catch (IOException ex) {
                    if (failure == null) failure = ex;
                    else failure.addSuppressed(ex);
                }
            }
            throw new IOException("No supported desktop file opener is available.", failure);
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IOException("This desktop environment cannot open " + absolute + " automatically.");
        }
        Desktop.getDesktop().open(absolute.toFile());
    }

    static List<List<String>> linuxCommands(Path file) {
        String uri = file.toAbsolutePath().normalize().toUri().toASCIIString();
        return List.of(List.of("xdg-open", uri), List.of("gio", "open", uri));
    }
}
