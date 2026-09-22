package net.modtale.launcher.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import net.modtale.launcher.api.ModtaleApiException;
import net.modtale.launcher.logging.LogSanitizer;
import net.modtale.launcher.settings.LauncherConfig;
import net.modtale.launcher.logging.LauncherLog;
import net.modtale.launcher.logging.LauncherLogger;

public class LauncherUpdateService {

    private static final LauncherLogger LOG = LauncherLog.getLogger(LauncherUpdateService.class);
    private static final String GITHUB_API_BASE_URL = "https://api.github.com";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String repository;
    private final String apiBaseUrl;

    public LauncherUpdateService() {
        this(LauncherConfig.launcherUpdatesRepository());
    }

    public LauncherUpdateService(String repository) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), repository);
    }

    LauncherUpdateService(HttpClient httpClient, String repository) {
        this(httpClient, repository, GITHUB_API_BASE_URL);
    }

    LauncherUpdateService(HttpClient httpClient, String repository, String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
        this.httpClient = httpClient;
        this.repository = LauncherConfig.normalizeLauncherUpdatesRepository(repository);
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Optional<LauncherUpdateCandidate> latestUpdate(String currentVersion) {
        return latestUpdate(currentVersion, "stable");
    }

    public Optional<LauncherUpdateCandidate> latestUpdate(String currentVersion, String channel) {
        Optional<GitHubRelease> latestRelease = latestLauncherRelease(channel);
        if (latestRelease.isEmpty()) {
            return Optional.empty();
        }

        GitHubRelease release = latestRelease.get();
        String latestVersion = LauncherVersion.normalizeTagVersion(release.tagName());
        boolean switchingChannel = currentVersion.contains("-develop.") != "develop".equals(channel);
        if (!switchingChannel && !LauncherVersion.isNewer(latestVersion, currentVersion)) {
            return Optional.empty();
        }

        Optional<GitHubAsset> asset = compatibleAsset(release.assets(), System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""));
        if (asset.isEmpty()) return Optional.empty();
        return Optional.of(new LauncherUpdateCandidate(
                latestVersion,
                release.tagName(),
                release.name(),
                release.htmlUrl(),
                asset.map(GitHubAsset::name).orElse(null),
                asset.map(GitHubAsset::browserDownloadUrl).orElse(null),
                release.prerelease(),
                asset.map(GitHubAsset::size).orElse(0L),
                asset.map(GitHubAsset::digest).orElse(null)
        ));
    }

    public Path downloadInstaller(LauncherUpdateCandidate update) {
        return downloadInstaller(update, ignored -> { });
    }

    public Path downloadInstaller(LauncherUpdateCandidate update, DoubleConsumer progress) {
        if (update == null || !update.hasInstallerAsset()) {
            throw new ModtaleApiException("No compatible automatic launcher update is attached to this release.");
        }

        URI downloadUri = URI.create(update.assetDownloadUrl());
        HttpRequest request = requestBuilder(downloadUri)
                .header("Accept", "application/octet-stream")
                .GET()
                .build();
        Path target = installerTarget(update.assetName());
        try {
            Files.createDirectories(target.getParent());
            LOG.info("GET " + LogSanitizer.uri(downloadUri));
            long started = System.currentTimeMillis();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            LOG.info("GET " + LogSanitizer.uri(downloadUri) + " -> HTTP "
                    + response.statusCode() + " in " + Math.max(0, System.currentTimeMillis() - started) + "ms");
            try (InputStream body = response.body()) {
                ensureSuccess(response.statusCode(), downloadUri.toString());
                if (update.assetSize() > 0) progress.accept(0);
                Path temporary = Files.createTempFile(target.getParent(), ".modtale-update-", ".tmp");
                try {
                    try (var output = Files.newOutputStream(temporary)) {
                        byte[] buffer = new byte[65536];
                        long downloaded = 0;
                        int lastPercent = 0;
                        for (int read; (read = body.read(buffer)) != -1;) {
                            downloaded += read;
                            if (downloaded > 256L * 1024 * 1024) throw new IOException("Launcher update exceeds the download size limit.");
                            output.write(buffer, 0, read);
                            if (update.assetSize() > 0) {
                                int percent = (int) Math.min(100, downloaded * 100 / update.assetSize());
                                if (percent > lastPercent) {
                                    lastPercent = percent;
                                    progress.accept(percent / 100.0);
                                }
                            }
                        }
                    }
                    if (update.assetSize() > 0 && Files.size(temporary) != update.assetSize()) {
                        throw new IOException("The launcher update download is incomplete.");
                    }
                    verifyDigest(temporary, update.assetDigest());
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            return target;
        } catch (IOException ex) {
            LOG.warn("Could not download launcher update from " + LogSanitizer.uri(downloadUri), ex);
            throw new ModtaleApiException("Could not download launcher update from " + LogSanitizer.uri(downloadUri), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("Launcher update download was interrupted.", ex);
            throw new ModtaleApiException("Launcher update download was interrupted.", ex);
        }
    }

    public void installUpdate(Path installer, LauncherUpdateCandidate update) {
        try {
            new LauncherPayloadInstaller().install(installer, update.version());
        } catch (IOException ex) {
            LOG.warn("Could not install launcher update " + installer, ex);
            throw new ModtaleApiException("Could not install launcher update: " + ex.getMessage(), ex);
        }
    }

    public String installationMessage() {
        return "The launcher will download the update and restart automatically. Your settings and library will be preserved.";
    }

    public Optional<String> consumeUpdateFailure() {
        String root = System.getenv("MODTALE_UPDATE_ROOT");
        if (root == null) return Optional.empty();
        Path failure = Path.of(root).resolve("update-failure");
        try {
            if (!Files.exists(failure)) return Optional.empty();
            String message = Files.readString(failure);
            Files.delete(failure);
            return Optional.of(message);
        } catch (IOException ex) {
            LOG.warn("Could not read update result", ex);
            return Optional.empty();
        }
    }

    static void verifyDigest(Path file, String expected) throws IOException {
        if (expected == null || !expected.startsWith("sha256:")) {
            throw new IOException("The release is missing its update checksum.");
        }
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
            }
            String actual = "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(expected)) throw new IOException("The launcher update checksum did not match. Try again.");
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 is unavailable", ex);
        }
    }

    public boolean canInstallUpdates() {
        String root = System.getenv("MODTALE_UPDATE_ROOT");
        String executable = System.getenv("MODTALE_LAUNCHER_EXECUTABLE");
        return root != null && !root.isBlank() && executable != null && !executable.isBlank();
    }

    static Optional<String> compatibleAssetName(List<String> assetNames, String osName, String arch) {
        if (assetNames == null || assetNames.isEmpty()) {
            return Optional.empty();
        }
        return assetNames.stream()
                .filter(name -> isCompatibleAssetName(name, osName, arch))
                .findFirst();
    }

    private Optional<GitHubRelease> latestLauncherRelease(String channel) {
        for (int page = 1; ; page++) {
            List<GitHubRelease> releases = releasePage(page);
            Optional<GitHubRelease> release = releases.stream()
                    .filter(candidate -> matchesChannel(candidate.tagName(), candidate.draft(), candidate.prerelease(), channel))
                    .findFirst();
            if (release.isPresent() || releases.size() < 100) {
                return release;
            }
        }
    }

    static boolean matchesChannel(String tag, boolean draft, boolean prerelease, String channel) {
        if (draft || tag == null) {
            return false;
        }
        return "develop".equals(channel)
                ? prerelease && tag.startsWith("launcher-develop-v")
                : !prerelease && (tag.startsWith("launcher-stable-v") || tag.startsWith("launcher-v"));
    }

    private List<GitHubRelease> releasePage(int page) {
        URI uri = URI.create(apiBaseUrl + "/repos/" + encodePath(repository) + "/releases?per_page=100&page=" + page);
        HttpRequest request = requestBuilder(uri)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        try {
            LOG.info("GET " + LogSanitizer.uri(uri));
            long started = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            LOG.info("GET " + LogSanitizer.uri(uri) + " -> HTTP "
                    + response.statusCode() + " in " + Math.max(0, System.currentTimeMillis() - started) + "ms");
            ensureSuccess(response.statusCode(), uri.toString());
            return mapper.readValue(response.body(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            LOG.warn("Could not read launcher release metadata from " + LogSanitizer.uri(uri), ex);
            throw new ModtaleApiException("Could not read launcher release metadata from " + LogSanitizer.uri(uri), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("Launcher update check was interrupted.", ex);
            throw new ModtaleApiException("Launcher update check was interrupted.", ex);
        }
    }

    private Optional<GitHubAsset> compatibleAsset(List<GitHubAsset> assets, String osName, String arch) {
        if (assets == null || assets.isEmpty()) {
            return Optional.empty();
        }
        return assets.stream()
                .filter(asset -> isCompatibleAssetName(asset.name(), osName, arch))
                .filter(asset -> asset.browserDownloadUrl() != null && !asset.browserDownloadUrl().isBlank())
                .filter(asset -> asset.size() > 0 && asset.digest() != null
                        && asset.digest().matches("sha256:[0-9a-fA-F]{64}"))
                .findFirst();
    }

    private static boolean isCompatibleAssetName(String assetName, String osName, String arch) {
        String name = assetName == null ? "" : assetName.toLowerCase(Locale.ROOT);
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String normalizedArch = normalizeArch(arch);

        String platform = os.contains("mac") || os.contains("darwin") ? "macos"
                : os.contains("win") ? "windows" : os.contains("linux") ? "linux" : "unsupported";
        return name.endsWith("-" + platform + "-" + normalizedArch + "-update.zip");
    }

    private Path installerTarget(String assetName) {
        try {
            return Files.createTempDirectory("modtale-update-").resolve(safeFilename(assetName));
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not prepare the launcher update download.", ex);
        }
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "ModtaleLauncher/" + LauncherVersion.current());
    }

    private static String normalizeArch(String arch) {
        String value = arch == null ? "" : arch.toLowerCase(Locale.ROOT);
        if (value.equals("amd64") || value.equals("x86_64")) {
            return "x86_64";
        }
        if (value.contains("aarch64") || value.contains("arm64")) {
            return "aarch64";
        }
        return value;
    }

    private static String safeFilename(String value) {
        String sanitized = value == null ? "modtale-launcher-update" : value.replaceAll("[^A-Za-z0-9._ -]+", "-");
        return sanitized.isBlank() ? "modtale-launcher-update" : sanitized.trim();
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    private static void ensureSuccess(int status, String target) {
        if (status < 200 || status >= 300) {
            String safeTarget = LogSanitizer.url(target);
            LOG.warn("GitHub returned HTTP " + status + " for " + safeTarget);
            throw new ModtaleApiException("GitHub returned HTTP " + status + " for " + safeTarget, status, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubRelease(
            String name,
            @JsonProperty("tag_name") String tagName,
            @JsonProperty("html_url") String htmlUrl,
            boolean draft,
            boolean prerelease,
            List<GitHubAsset> assets
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubAsset(
            String name,
            @JsonProperty("browser_download_url") String browserDownloadUrl,
            long size,
            String digest
    ) {
    }
}
