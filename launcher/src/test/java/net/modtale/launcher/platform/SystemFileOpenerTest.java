package net.modtale.launcher.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemFileOpenerTest {
    @TempDir Path directory;

    @Test
    void linuxInstallerPathsRemainSingleUriArguments() {
        Path installer = directory.resolve("-installer $(command) #1.deb");
        String uri = installer.toUri().toASCIIString();
        assertEquals(List.of(List.of("xdg-open", uri), List.of("gio", "open", uri)),
                SystemFileOpener.linuxCommands(installer));
        assertTrue(uri.startsWith("file:/"));
        assertTrue(uri.contains("%20"));
        assertTrue(uri.contains("%23"));
    }

    @Test
    void rejectsMissingInstallersBeforeStartingDesktopIntegration() {
        assertThrows(IOException.class, () -> SystemFileOpener.open(directory.resolve("missing.deb")));
        assertThrows(IOException.class, () -> SystemFileOpener.open(directory));
    }
}
