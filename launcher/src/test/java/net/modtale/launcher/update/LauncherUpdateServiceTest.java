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
        String develop = release("launcher-develop-v2.0.0-develop.10.1", true, automaticAssets());
        String stable = release("launcher-stable-v1.0.0", false, automaticAssets());
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
    void legacyOrIncompleteReleasesAreNotOfferedAsUpdates() throws Exception {
        var body = new java.util.concurrent.atomic.AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/Modtale/modtale/releases", exchange -> {
            byte[] bytes = ("[" + body.get() + "]").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var service = new LauncherUpdateService(HttpClient.newHttpClient(), "Modtale/modtale",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            for (String assets : List.of("[]", "[{\"name\":\"launcher.AppImage\"}]",
                    automaticAssets().replace("sha256:", "unavailable:"),
                    automaticAssets().replace("https://example.invalid/update", ""))) {
                body.set(release("launcher-v0.2.145", false, assets));
                assertTrue(service.latestUpdate("0.1.0-SNAPSHOT", "stable").isEmpty());
            }
            body.set(release("launcher-stable-v0.2.160", false, automaticAssets()));
            var update = service.latestUpdate("0.2.145", "stable").orElseThrow();
            assertTrue(update.hasInstallerAsset());
            assertEquals("0.2.160", update.version());
            assertTrue(service.latestUpdate("0.2.145", "develop").isEmpty());
        } finally {
            server.stop(0);
        }
    }

    private static String release(String tag, boolean prerelease, String assets) {
        return "{\"tag_name\":\"" + tag + "\",\"prerelease\":" + prerelease + ",\"assets\":" + assets + "}";
    }

    private static String automaticAssets() {
        var assets = new java.util.ArrayList<String>();
        for (String os : List.of("linux", "windows", "macos")) {
            for (String arch : List.of("x86_64", "aarch64")) {
                assets.add("{\"name\":\"launcher-" + os + "-" + arch + "-update.zip\","
                        + "\"browser_download_url\":\"https://example.invalid/update\",\"size\":123,"
                        + "\"digest\":\"sha256:" + "a".repeat(64) + "\"}");
            }
        }
        return "[" + String.join(",", assets) + "]";
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
