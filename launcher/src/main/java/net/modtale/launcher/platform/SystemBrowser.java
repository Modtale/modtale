package net.modtale.launcher.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Opens web links through AWT when available and native OS launchers otherwise. */
public final class SystemBrowser {

    private SystemBrowser() {
    }

    public static void open(URI uri) throws IOException {
        if (uri == null || uri.getScheme() == null
                || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) {
            throw new IOException("Only HTTP and HTTPS links can be opened.");
        }

        IOException failure = null;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (IOException | RuntimeException ex) {
            failure = asIOException("AWT desktop browser integration failed.", ex);
        }

        for (List<String> command : commandsFor(System.getProperty("os.name", ""), uri)) {
            try {
                startCommand(command);
                return;
            } catch (IOException | RuntimeException ex) {
                IOException commandFailure = asIOException("Could not run " + command.getFirst() + ".", ex);
                if (failure == null) failure = commandFailure;
                else failure.addSuppressed(commandFailure);
            }
        }

        throw new IOException("No supported system browser integration is available.", failure);
    }

    static void startCommand(List<String> command) throws IOException {
        new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    static List<List<String>> commandsFor(String osName, URI uri) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String url = uri.toASCIIString();
        List<List<String>> commands = new ArrayList<>();
        if (os.contains("win")) {
            commands.add(List.of("rundll32", "url.dll,FileProtocolHandler", url));
        } else if (os.contains("mac") || os.contains("darwin")) {
            commands.add(List.of("open", url));
        } else {
            commands.add(List.of("xdg-open", url));
            commands.add(List.of("gio", "open", url));
        }
        return List.copyOf(commands);
    }

    private static IOException asIOException(String message, Exception cause) {
        return cause instanceof IOException ioException
                ? ioException
                : new IOException(message, cause);
    }
}
