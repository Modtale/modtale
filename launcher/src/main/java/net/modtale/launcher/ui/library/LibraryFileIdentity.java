package net.modtale.launcher.ui.library;

import java.io.IOException;
import java.nio.file.Path;

final class LibraryFileIdentity {
    private LibraryFileIdentity() {}

    static String key(String file) {
        if (file == null || file.isBlank()) return "";
        try { return key(Path.of(file)); }
        catch (RuntimeException ignored) { return file; }
    }

    static String key(Path file) {
        if (file == null) return "";
        try {
            return file.toRealPath().toString();
        } catch (IOException | SecurityException ignored) {
            // Missing or inaccessible artifacts retain their lexical identity.
            return file.toAbsolutePath().normalize().toString();
        }
    }
}
