package net.modtale.launcher.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import java.util.HexFormat;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.api.ProjectSearchQuery;
import net.modtale.launcher.model.install.InstallResult;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.project.DownloadUrlResponse;
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
        assertTrue(Files.notExists(mods.resolve("readme.txt")));
        assertTrue(Files.notExists(mods.resolve("Generated Bundle")));
        assertEquals(2, result.installedFiles().size());
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

    @Test
    void modpackInstallInstallsExactCurseForgeFiles() throws IOException {
        AtomicReference<String> downloadQuery = new AtomicReference<>("");
        AtomicInteger curseForgeDownloads = new AtomicInteger();
        byte[] pack = zip(entry("mods/main.jar", "main"));
        startServer();
        server.createContext("/api/v1/projects/pack/versions/1.0.0/download-url", exchange -> {
            downloadQuery.set(exchange.getRequestURI().getRawQuery());
            respondJson(exchange, """
                    {"downloadUrl":"%s/files/pack.zip","expiresIn":60}
                    """.formatted(serverBaseUrl()));
        });
        server.createContext("/files/pack.zip", exchange -> respondBytes(exchange, pack, "application/zip"));
        server.createContext("/curseforge/file/123", exchange -> {
            curseForgeDownloads.incrementAndGet();
            respondBytes(exchange, "curseforge-binary".getBytes(StandardCharsets.UTF_8), "application/java-archive");
        });

        ProjectDependency curseForge = new ProjectDependency(
                "cf-reference", null, null, null, "REQUIRED", "CURSEFORGE", "1450386",
                "https://www.curseforge.com/hytale/mods/example-mod/files/8227810",
                "https://www.curseforge.com/hytale/mods/example-mod/files/8227810",
                "example.jar", null, true, null, "Example Mod", "PLUGIN", "example-mod",
                false, false
        );
        ProjectVersion version = version("pack-version", "1.0.0", curseForge);
        ProjectDetail project = new ProjectDetail(
                "pack", "pack", "Pack", "Pack description", "Creator", "MODPACK",
                "2026-01-01T00:00:00Z", "MIT", "", List.of(), List.of(version)
        );
        LauncherSettings settings = new LauncherSettings();
        Path instance = tempDir.resolve("instance");
        settings.setHytaleUserDataPath(instance.toString());
        settings.setHytaleModsPath(instance.resolve("mods").toString());
        settings.setGameVersion("2026.1");

        InstallResult result = new ModInstaller(
                apiClientWithCurseForgeDownload(new DownloadUrlResponse(
                        serverBaseUrl() + "/curseforge/file/123", 0, "example.jar", 17L, java.util.Map.of(), "CURSEFORGE")),
                new SettingsStore(tempDir.resolve("settings.json"))
        ).installAndRecord(project, version, settings, "2026.1", List.of(curseForge));

        assertTrue(downloadQuery.get().contains("gameVersion=2026.1"));
        assertFalse(downloadQuery.get().contains("target="));
        assertEquals(1, curseForgeDownloads.get());
        assertTrue(result.warnings().isEmpty());
        assertEquals("main", Files.readString(instance.resolve("mods/main.jar")));
        assertEquals("curseforge-binary", Files.readString(instance.resolve("mods/example.jar")));
    }

    @Test
    void curseForgeProjectDownloadsThroughProviderEndpointAndVerifiesArtifact() throws Exception {
        byte[] artifact = "verified-curseforge-mod".getBytes(StandardCharsets.UTF_8);
        String artifactSha1 = sha1(artifact);
        startServer();
        server.createContext("/files/SimpleCompost.jar", exchange ->
                respondBytes(exchange, artifact, "application/java-archive"));
        ProjectVersion version = new ProjectVersion("8227810", "1.2.0", List.of("2026.09"),
                "https://www.curseforge.com/hytale/mods/simple-compost/files/8227810", 12,
                "2026-09-01T00:00:00Z", null, List.of(), "RELEASE");
        ProjectDetail project = new ProjectDetail("curseforge:1450386", "curseforge:1450386", "Simple Compost",
                "Compost things", "Builder", "MOD", "2026-09-01T00:00:00Z", null, null,
                List.of("Gameplay"), List.of(version));
        LauncherSettings settings = new LauncherSettings();
        Path mods = tempDir.resolve("mods");
        settings.setHytaleModsPath(mods.toString());
        settings.setGameVersion("2026.09");

        InstallResult result = new ModInstaller(apiClientWithCurseForgeDownload(new DownloadUrlResponse(
                        serverBaseUrl() + "/files/SimpleCompost.jar", 0, "SimpleCompost.jar",
                        (long) artifact.length, java.util.Map.of("sha1", artifactSha1), "CURSEFORGE")),
                new SettingsStore(tempDir.resolve("settings.json")))
                .installAndRecord(project, version, settings, "2026.09");

        assertEquals("verified-curseforge-mod", Files.readString(mods.resolve("SimpleCompost.jar")));
        assertEquals(InstalledProject.SOURCE_CURSEFORGE, result.installedProject().source());
        assertEquals("8227810", result.installedProject().installedVersionId());
    }

    @Test
    void curseForgeProjectRejectsTamperedArtifactBeforeInstallation() throws Exception {
        byte[] artifact = "tampered".getBytes(StandardCharsets.UTF_8);
        startServer();
        server.createContext("/files/mod.jar", exchange -> respondBytes(exchange, artifact, "application/java-archive"));
        ProjectVersion version = new ProjectVersion("8227810", "1.2.0", List.of("2026.09"), null, 0,
                null, null, List.of(), "RELEASE");
        ProjectDetail project = new ProjectDetail("curseforge:1450386", "curseforge:1450386", "Simple Compost",
                "Compost things", "Builder", "MOD", null, null, null, List.of(), List.of(version));
        LauncherSettings settings = new LauncherSettings();
        Path mods = tempDir.resolve("mods-tampered");
        settings.setHytaleModsPath(mods.toString());

        assertThrows(RuntimeException.class, () -> new ModInstaller(apiClientWithCurseForgeDownload(new DownloadUrlResponse(
                        serverBaseUrl() + "/files/mod.jar", 0, "mod.jar", (long) artifact.length,
                        java.util.Map.of("sha1", "0".repeat(40)), "CURSEFORGE")),
                new SettingsStore(tempDir.resolve("settings-tampered.json")))
                .installAndRecord(project, version, settings, "2026.09"));
        assertTrue(Files.notExists(mods.resolve("mod.jar")));
    }

    @Test
    void liveNyoCfModpackDependencyDownloadsAndInstallsThroughTheLauncher() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("NYOCF_LIVE_TESTS")),
                "Set NYOCF_LIVE_TESTS=true to run the nyoCF launcher install contract.");
        byte[] pack = zip(entry("mods/main.jar", "main"));
        startServer();
        server.createContext("/api/v1/projects/pack/versions/1.0.0/download-url", exchange ->
                respondJson(exchange, """
                        {"downloadUrl":"%s/files/pack.zip","expiresIn":60}
                        """.formatted(serverBaseUrl())));
        server.createContext("/files/pack.zip", exchange -> respondBytes(exchange, pack, "application/zip"));
        server.createContext("/api/v1/projects/external/curseforge/1450386/files/8747324/download-url", exchange ->
                respondJson(exchange, """
                        {"downloadUrl":"https://www.curseforge.com/api/v1/mods/1450386/files/8747324/download","fileName":"SimpleCompost-1.0.0.jar","fileSize":100897,"hashes":{"sha1":"298f05d4294ad6573f1860239b667f76ab510716"},"source":"CURSEFORGE"}
                        """));
        ProjectDependency curseForge = new ProjectDependency(
                "cf-reference", null, null, null, "REQUIRED", "CURSEFORGE", "1450386",
                "https://www.curseforge.com/hytale/mods/simple-compost/files/8747324",
                "https://www.curseforge.com/hytale/mods/simple-compost/files/8747324",
                "SimpleCompost-1.0.0.jar", null, true, null, "Simple Compost", "MOD", "simple-compost",
                false, false);
        ProjectVersion version = version("pack-version", "1.0.0", curseForge);
        ProjectDetail project = new ProjectDetail("pack", "pack", "Pack", "Pack description", "Creator", "MODPACK",
                "2026-01-01T00:00:00Z", "MIT", "", List.of(), List.of(version));
        LauncherSettings settings = new LauncherSettings();
        Path instance = tempDir.resolve("live-instance");
        settings.setHytaleUserDataPath(instance.toString());
        settings.setHytaleModsPath(instance.resolve("mods").toString());

        InstallResult result = new ModInstaller(new ModtaleApiClient(apiBaseUrl()),
                new SettingsStore(tempDir.resolve("live-settings.json")))
                .installAndRecord(project, version, settings, "Early Access", List.of(curseForge));

        Path installed = instance.resolve("mods/SimpleCompost-1.0.0.jar");
        assertTrue(result.warnings().isEmpty());
        assertEquals(100897L, Files.size(installed));
        byte[] signature = Files.readAllBytes(installed);
        assertEquals('P', signature[0]);
        assertEquals('K', signature[1]);
    }

    @Test
    void liveCurseForgeBrowseProjectAndDirectInstallWorkThroughTheLauncher() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("NYOCF_LIVE_TESTS")),
                "Set NYOCF_LIVE_TESTS=true to run the nyoCF launcher install contract.");
        ModtaleApiClient client = new ModtaleApiClient(ModtaleApiClient.DEFAULT_API_BASE_URL);

        var browse = client.searchCurseForgeMods(new ProjectSearchQuery(
                "Simple Compost", null, null, "downloads", 0, 20,
                null, null, null, null, null, null));
        var card = browse.content().stream()
                .filter(candidate -> "curseforge:1450386".equals(candidate.id()))
                .findFirst().orElseThrow();
        ProjectDetail project = client.getProject(card.routeKey());
        ProjectVersion version = project.versions().stream()
                .filter(candidate -> "8747324".equals(candidate.id()))
                .findFirst().orElseThrow();
        DownloadUrlResponse resolvedDownload = client.getCurseForgeDownloadUrl(1450386, 8747324);
        assertEquals("Simple Compost", project.title());
        assertTrue(project.about().contains("Simple Compost"));
        assertFalse(project.galleryImages().isEmpty());
        assertTrue(project.links().get("CurseForge").startsWith("https://www.curseforge.com/hytale/mods/"));
        assertEquals(100897L, resolvedDownload.fileSize());
        assertEquals("298f05d4294ad6573f1860239b667f76ab510716", resolvedDownload.hashes().get("sha1"));
        LauncherSettings settings = new LauncherSettings();
        Path instance = tempDir.resolve("live-direct-instance");
        settings.setHytaleUserDataPath(instance.toString());
        settings.setHytaleModsPath(instance.resolve("mods").toString());

        InstallResult result = new ModInstaller(client,
                new SettingsStore(tempDir.resolve("live-direct-settings.json")))
                .installAndRecord(project, version, settings, "Early Access");

        Path installed = instance.resolve("mods/SimpleCompost-1.0.0.jar");
        assertTrue(result.warnings().isEmpty());
        assertEquals(100897L, Files.size(installed));
        assertEquals("298f05d4294ad6573f1860239b667f76ab510716",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(installed))));
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

    private ModtaleApiClient apiClientWithCurseForgeDownload(DownloadUrlResponse response) {
        return new ModtaleApiClient(apiBaseUrl()) {
            @Override
            public DownloadUrlResponse getCurseForgeDownloadUrl(long projectId, long fileId) {
                return response;
            }
        };
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

    private static String sha1(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
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
