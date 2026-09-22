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
    void selectsOnlyAutomaticPayloadsForEachPlatformAndArchitecture() {
        List<String> assets = List.of(
                "modtale-launcher-1.2.0-linux-x86_64-update.zip",
                "modtale-launcher-1.2.0-linux-aarch64-update.zip",
                "modtale-launcher-1.2.0-windows-x86_64-update.zip",
                "modtale-launcher-1.2.0-windows-aarch64-update.zip",
                "modtale-launcher-1.2.0-macos-x86_64-update.zip",
                "modtale-launcher-1.2.0-macos-aarch64-update.zip",
                "modtale-launcher-1.2.0-x86_64.AppImage", "Modtale Launcher.exe", "Modtale Launcher.dmg");
        for (String os : List.of("Linux", "Windows 11", "Mac OS X", "Darwin")) {
            String platform = os.equals("Linux") ? "linux" : os.equals("Windows 11") ? "windows" : "macos";
            for (String arch : List.of("amd64", "x86_64", "aarch64", "arm64")) {
                String normalized = arch.equals("amd64") || arch.equals("x86_64") ? "x86_64" : "aarch64";
                assertEquals("modtale-launcher-1.2.0-" + platform + "-" + normalized + "-update.zip",
                        LauncherUpdateService.compatibleAssetName(assets, os, arch).orElseThrow());
            }
        }
        assertTrue(LauncherUpdateService.compatibleAssetName(assets, "Linux", "riscv64").isEmpty());
        assertTrue(LauncherUpdateService.compatibleAssetName(assets, "FreeBSD", "x86_64").isEmpty());
        assertTrue(LauncherUpdateService.compatibleAssetName(List.of("launcher.AppImage", "launcher.exe", "launcher.dmg"),
                "Linux", "amd64").isEmpty());
    }
}
