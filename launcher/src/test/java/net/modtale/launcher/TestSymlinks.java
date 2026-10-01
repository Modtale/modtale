package net.modtale.launcher;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;

/** Keeps link-specific tests explicit on Windows accounts without symlink privilege. */
public final class TestSymlinks {
    private TestSymlinks() {}

    public static Path createSymbolicLink(Path link, Path target) throws IOException {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (FileSystemException exception) {
            String reason = exception.getReason() == null ? "" : exception.getReason().toLowerCase(Locale.ROOT);
            if (reason.contains("privilege") || reason.contains("not permitted") || reason.contains("not supported")) {
                Assumptions.abort("Symbolic links are unavailable to this Windows test account: " + reason);
            }
            throw exception;
        }
    }
}
