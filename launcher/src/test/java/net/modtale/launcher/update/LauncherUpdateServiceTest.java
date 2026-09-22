package net.modtale.launcher.update;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import java.util.List;
import org.junit.jupiter.api.Test;

class LauncherUpdateServiceTest {

    @Test
    void isolatesReleaseChannels() {
        assertTrue(LauncherUpdateService.matchesChannel("launcher-stable-v1.0.0", false, false, "stable"));
        assertTrue(LauncherUpdateService.matchesChannel("launcher-v1.0.0", false, false, "stable"));
        assertTrue(LauncherUpdateService.matchesChannel("launcher-develop-v1.0.0-develop.1.1", false, true, "develop"));
        assertFalse(LauncherUpdateService.matchesChannel("launcher-develop-v1.0.0", false, false, "stable"));
        assertFalse(LauncherUpdateService.matchesChannel("launcher-stable-v1.0.0", false, false, "develop"));
        assertFalse(LauncherUpdateService.matchesChannel("launcher-stable-v1.0.0", true, false, "stable"));
    }

    @Test
    void findsChannelsAcrossPagesAndAllowsSwitchingBackToOlderStable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String develop = "{\"tag_name\":\"launcher-develop-v2.0.0-develop.10.1\",\"prerelease\":true}";
        String stable = "{\"tag_name\":\"launcher-stable-v1.0.0\",\"prerelease\":false}";
        server.createContext("/repos/Modtale/modtale/releases", exchange -> {
            String body = exchange.getRequestURI().getQuery().endsWith("page=1")
                    ? "[" + String.join(",", java.util.Collections.nCopies(100, develop)) + "]"
                    : "[" + stable + "]";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            LauncherUpdateService service = new LauncherUpdateService(HttpClient.newHttpClient(), "Modtale/modtale",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            assertEquals("1.0.0", service.latestUpdate("2.0.0-develop.10.1", "stable").orElseThrow().version());
            assertEquals("2.0.0-develop.10.1", service.latestUpdate("3.0.0", "develop").orElseThrow().version());
            assertTrue(service.latestUpdate("1.0.0", "stable").isEmpty());
            assertTrue(service.latestUpdate("2.0.0-develop.10.1", "develop").isEmpty());
            assertTrue(service.latestUpdate("2.0.0", "stable").isEmpty());
            assertTrue(service.latestUpdate("2.0.0-develop.9.1", "develop").isPresent());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void selectsWindowsInstallerAsset() {
        assertEquals("Modtale Launcher-1.2.0.exe", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x86_64.AppImage",
                "Modtale Launcher-1.2.0.dmg",
                "Modtale Launcher-1.2.0.exe"
        ), "Windows 11", "amd64").orElseThrow());
    }

    @Test
    void selectsMacInstallerAsset() {
        assertEquals("Modtale Launcher-1.2.0.dmg", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x86_64.AppImage",
                "Modtale Launcher-1.2.0.dmg",
                "Modtale Launcher-1.2.0.exe"
        ), "Mac OS X", "aarch64").orElseThrow());
    }

    @Test
    void selectsLinuxAssetForCurrentArchitecture() {
        assertEquals("modtale-launcher-1.2.0-aarch64.AppImage", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x86_64.AppImage",
                "modtale-launcher-1.2.0-aarch64.AppImage"
        ), "Linux", "aarch64").orElseThrow());
    }

    @Test
    void doesNotOfferArmLinuxInstallerToX64Users() {
        assertEquals("modtale-launcher-1.2.0-x86_64.AppImage", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x86_64.AppImage",
                "modtale-launcher-1.2.0-arm64.AppImage"
        ), "Linux", "amd64").orElseThrow());
    }

    @Test
    void selectsArchitectureSpecificWindowsInstaller() {
        assertEquals("modtale-launcher-1.2.0-arm64.exe", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x64.exe",
                "modtale-launcher-1.2.0-arm64.exe",
                "modtale-launcher-1.2.0.exe"
        ), "Windows 11", "aarch64").orElseThrow());
    }

    @Test
    void selectsArchitectureSpecificMacInstaller() {
        assertEquals("modtale-launcher-1.2.0-x64.dmg", LauncherUpdateService.compatibleAssetName(List.of(
                "modtale-launcher-1.2.0-x64.dmg",
                "modtale-launcher-1.2.0-arm64.dmg",
                "modtale-launcher-1.2.0.dmg"
        ), "Mac OS X", "x86_64").orElseThrow());
    }
}
