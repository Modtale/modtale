package net.modtale.launcher.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemBrowserTest {

    private static final URI URL = URI.create("https://example.com/auth?state=a%20b");

    @Test
    void usesDesktopAwareLinuxLaunchersWithoutShellInterpolation() {
        assertEquals(List.of(
                List.of("xdg-open", URL.toASCIIString()),
                List.of("gio", "open", URL.toASCIIString())
        ), SystemBrowser.commandsFor("Linux", URL));
    }

    @Test
    void usesNativeMacLauncher() {
        assertEquals(List.of(List.of("open", URL.toASCIIString())),
                SystemBrowser.commandsFor("Mac OS X", URL));
    }

    @Test
    void usesNativeWindowsLauncher() {
        assertEquals(List.of(List.of("rundll32", "url.dll,FileProtocolHandler", URL.toASCIIString())),
                SystemBrowser.commandsFor("Windows 11", URL));
    }

    @Test
    void startsFallbackProcessWithValidRedirects() {
        String java = ProcessHandle.current().info().command().orElseThrow();
        assertDoesNotThrow(() -> SystemBrowser.startCommand(List.of(java, "-version")));
    }
}
