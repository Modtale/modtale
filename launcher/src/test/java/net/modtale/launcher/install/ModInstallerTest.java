package net.modtale.launcher.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.install.InstallResult;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.settings.SettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModInstallerTest {

    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void selectedDependencyBundlesUseModpackFlattening() throws IOException {
        AtomicInteger dependencyRequests = new AtomicInteger();
        AtomicReference<String> bundleQuery = new AtomicReference<>("");
        byte[] bundle = zip(
                entry("Generated Bundle/mods/main.jar", "main"),
                entry("Generated Bundle/mods/dependency.jar", "dependency"),
                entry("Generated Bundle/readme.txt", "readme")
        );
        startServer();
        server.createContext("/api/v1/projects/project/versions/1.0.0/dependencies", exchange -> {
            dependencyRequests.incrementAndGet();
            respond(exchange, 500, "Unexpected dependency lookup");
        });
        server.createContext("/api/v1/projects/project/versions/1.0.0/download-bundle-url", exchange -> {
            bundleQuery.set(exchange.getRequestURI().getRawQuery());
            respondJson(exchange, """
                    {"downloadUrl":"%s/files/bundle.zip","expiresIn":60}
                    """.formatted(serverBaseUrl()));
        });
        server.createContext("/files/bundle.zip", exchange -> respondBytes(exchange, bundle, "application/zip"));

        ProjectDependency dependency = dependency();
        ProjectVersion version = new ProjectVersion(
                "v1",
                "1.0.0",
                List.of("2026.1"),
                "/files/main.jar",
                0,
                "2026-01-01T00:00:00Z",
                "",
                List.of(dependency),
                "RELEASE"
        );
        ProjectDetail project = new ProjectDetail(
                "project",
                "project",
                "Project",
                "Project description",
                "Creator",
                "PLUGIN",
                "2026-01-01T00:00:00Z",
                "MIT",
                "",
                List.of(),
                List.of(version)
        );
        LauncherSettings settings = new LauncherSettings();
        Path mods = tempDir.resolve("mods");
        settings.setHytaleModsPath(mods.toString());
        settings.setGameVersion("2026.1");

        InstallResult result = new ModInstaller(
                new ModtaleApiClient(apiBaseUrl()),
                new SettingsStore(tempDir.resolve("settings.json"))
        ).installAndRecord(project, version, settings, "2026.1", List.of(dependency));

        assertEquals(0, dependencyRequests.get(), "Exact modal selections should not refetch dependencies.");
        assertTrue(bundleQuery.get().contains("deps=dependency-project"));
        assertEquals("main", Files.readString(mods.resolve("main.jar")));
        assertEquals("dependency", Files.readString(mods.resolve("dependency.jar")));
        assertEquals("readme", Files.readString(mods.resolve("readme.txt")));
        assertTrue(Files.notExists(mods.resolve("Generated Bundle")));
        assertTrue(result.installedFiles().stream().anyMatch(path -> path.getFileName().toString().equals("readme.txt")));
        assertEquals(InstalledProject.INSTALL_BUNDLE, result.installedProject().installType());
        assertEquals(List.of("dependency-project"), result.installedProject().dependencyProjectIds());
        assertEquals(1, result.installedProject().bundledProjects().size());
        assertEquals("dependency-project", result.installedProject().bundledProjects().getFirst().projectId());
        assertEquals("Dependency Project", result.installedProject().bundledProjects().getFirst().displayName());
    }

    @Test
    void switchVersionPreservesBundleSelectionAndInstallRecord() throws IOException {
        byte[] firstBundle = zip(
                entry("mods/main.jar", "main-one"),
                entry("mods/dependency.jar", "dependency-one")
        );
        byte[] secondBundle = zip(
                entry("mods/main.jar", "main-two"),
                entry("mods/dependency.jar", "dependency-two")
        );
        AtomicReference<String> secondBundleQuery = new AtomicReference<>("");
        startServer();
        server.createContext("/api/v1/projects/project/versions/1.0.0/download-bundle-url", exchange -> respondJson(exchange, """
                {"downloadUrl":"%s/files/first.zip","expiresIn":60}
                """.formatted(serverBaseUrl())));
        server.createContext("/api/v1/projects/project/versions/1.1.0/download-bundle-url", exchange -> {
            secondBundleQuery.set(exchange.getRequestURI().getRawQuery());
            respondJson(exchange, """
                    {"downloadUrl":"%s/files/second.zip","expiresIn":60}
                    """.formatted(serverBaseUrl()));
        });
        server.createContext("/files/first.zip", exchange -> respondBytes(exchange, firstBundle, "application/zip"));
        server.createContext("/files/second.zip", exchange -> respondBytes(exchange, secondBundle, "application/zip"));

        ProjectDependency dependency = dependency();
        ProjectVersion first = version("v1", "1.0.0", dependency);
        ProjectVersion second = version("v2", "1.1.0", dependency);
        ProjectDetail project = project(first, second);
        LauncherSettings settings = new LauncherSettings();
        Path mods = tempDir.resolve("mods");
        settings.setHytaleModsPath(mods.toString());
        settings.setGameVersion("2026.1");

        ModInstaller installer = new ModInstaller(
                new ModtaleApiClient(apiBaseUrl()),
                new SettingsStore(tempDir.resolve("settings.json"))
        );
        InstallResult installed = installer.installAndRecord(project, first, settings, "2026.1", List.of(dependency));

        InstallResult switched = installer.switchVersionAndRecord(installed.installedProject(), project, second, settings);

        assertTrue(secondBundleQuery.get().contains("deps=dependency-project"));
        assertEquals("main-two", Files.readString(mods.resolve("main.jar")));
        assertEquals("dependency-two", Files.readString(mods.resolve("dependency.jar")));
        assertEquals("1.1.0", switched.installedProject().installedVersion());
        assertEquals(installed.installedProject().installedAt(), switched.installedProject().installedAt());
        assertEquals(List.of("dependency-project"), switched.installedProject().dependencyProjectIds());
        assertEquals(1, settings.getInstalledProjects().size());
    }

    private ProjectDependency dependency() {
        return new ProjectDependency(
                "dependency-reference",
                "dependency-project",
                "Dependency Project",
                "2.0.0",
                "REQUIRED",
                "MODTALE",
                null,
                null,
                null,
                null,
                null,
                false
        );
    }

    private ProjectVersion version(String id, String number, ProjectDependency dependency) {
        return new ProjectVersion(
                id,
                number,
                List.of("2026.1"),
                "/files/main.jar",
                0,
                "2026-01-01T00:00:00Z",
                "",
                List.of(dependency),
                "RELEASE"
        );
    }

    private ProjectDetail project(ProjectVersion... versions) {
        return new ProjectDetail(
                "project",
                "project",
                "Project",
                "Project description",
                "Creator",
                "PLUGIN",
                "2026-01-01T00:00:00Z",
                "MIT",
                "",
                List.of(),
                List.of(versions)
        );
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
    }

    private String serverBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String apiBaseUrl() {
        return serverBaseUrl() + "/api/v1";
    }

    private static Entry entry(String name, String content) {
        return new Entry(name, content);
    }

    private static byte[] zip(Entry... entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void respondJson(HttpExchange exchange, String body) throws IOException {
        respondBytes(exchange, body.getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void respondBytes(HttpExchange exchange, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"bundle.zip\"");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private record Entry(String name, String content) {
    }
}
