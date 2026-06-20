package net.modtale.launcher.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Desktop;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.modtale.launcher.api.ModtaleApiException;
import net.modtale.launcher.settings.LauncherConfig;

public class LauncherUpdateService {

    private static final String GITHUB_API_BASE_URL = "https://api.github.com";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String repository;

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
        this.httpClient = httpClient;
        this.repository = LauncherConfig.normalizeLauncherUpdatesRepository(repository);
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Optional<LauncherUpdateCandidate> latestUpdate(String currentVersion) {
        Optional<GitHubRelease> latestRelease = latestLauncherRelease();
        if (latestRelease.isEmpty()) {
            return Optional.empty();
        }

        GitHubRelease release = latestRelease.get();
        String latestVersion = LauncherVersion.normalizeTagVersion(release.tagName());
        if (!LauncherVersion.isNewer(latestVersion, currentVersion)) {
            return Optional.empty();
        }

        Optional<GitHubAsset> asset = compatibleAsset(release.assets(), System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""));
        return Optional.of(new LauncherUpdateCandidate(
                latestVersion,
                release.tagName(),
                release.name(),
                release.htmlUrl(),
                asset.map(GitHubAsset::name).orElse(null),
                asset.map(GitHubAsset::browserDownloadUrl).orElse(null),
                release.prerelease()
        ));
    }

    public Path downloadInstaller(LauncherUpdateCandidate update) {
        if (update == null || !update.hasInstallerAsset()) {
            throw new ModtaleApiException("No compatible launcher installer is attached to this release.");
        }

        URI downloadUri = URI.create(update.assetDownloadUrl());
        HttpRequest request = requestBuilder(downloadUri)
                .header("Accept", "application/octet-stream")
                .GET()
                .build();
        Path target = installerTarget(update.assetName());
        try {
            Files.createDirectories(target.getParent());
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            ensureSuccess(response.statusCode(), downloadUri.toString());
            try (InputStream body = response.body()) {
                Files.copy(body, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (isLinuxAppImage(target)) {
                target.toFile().setExecutable(true, false);
            }
            return target;
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not download launcher update from " + downloadUri, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModtaleApiException("Launcher update download was interrupted.", ex);
        }
    }

    public void openInstaller(Path installer) {
        if (installer == null) {
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            throw new ModtaleApiException("This desktop environment cannot open " + installer + " automatically.");
        }
        try {
            Desktop.getDesktop().open(installer.toFile());
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not open launcher installer " + installer, ex);
        }
    }

    public void openReleasePage(LauncherUpdateCandidate update) {
        String releaseUrl = update == null ? null : update.releaseUrl();
        if (releaseUrl == null || releaseUrl.isBlank()) {
            releaseUrl = "https://github.com/" + repository + "/releases";
        }
        if (!Desktop.isDesktopSupported()) {
            throw new ModtaleApiException("This desktop environment cannot open the launcher release page automatically.");
        }
        try {
            Desktop.getDesktop().browse(URI.create(releaseUrl));
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not open launcher release page " + releaseUrl, ex);
        }
    }

    static Optional<String> compatibleAssetName(List<String> assetNames, String osName, String arch) {
        if (assetNames == null || assetNames.isEmpty()) {
            return Optional.empty();
        }
        return assetNames.stream()
                .filter(name -> isCompatibleAssetName(name, osName, arch))
                .max(Comparator.comparingInt(name -> assetScore(name, osName, arch)));
    }

    private Optional<GitHubRelease> latestLauncherRelease() {
        URI uri = URI.create(GITHUB_API_BASE_URL + "/repos/" + encodePath(repository) + "/releases?per_page=30");
        HttpRequest request = requestBuilder(uri)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), uri.toString());
            List<GitHubRelease> releases = mapper.readValue(response.body(), new TypeReference<>() {
            });
            return releases.stream()
                    .filter(release -> !release.draft() && !release.prerelease())
                    .filter(this::isLauncherRelease)
                    .max((left, right) -> LauncherVersion.compare(
                            LauncherVersion.normalizeTagVersion(left.tagName()),
                            LauncherVersion.normalizeTagVersion(right.tagName())
                    ));
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not read launcher release metadata from " + uri, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModtaleApiException("Launcher update check was interrupted.", ex);
        }
    }

    private boolean isLauncherRelease(GitHubRelease release) {
        String tagName = release.tagName() == null ? "" : release.tagName().toLowerCase(Locale.ROOT);
        String releaseName = release.name() == null ? "" : release.name().toLowerCase(Locale.ROOT);
        return tagName.startsWith("launcher-v")
                || releaseName.contains("launcher")
                || hasLauncherAsset(release.assets());
    }

    private boolean hasLauncherAsset(List<GitHubAsset> assets) {
        return assets != null && assets.stream()
                .map(GitHubAsset::name)
                .anyMatch(LauncherUpdateService::isLauncherAssetName);
    }

    private Optional<GitHubAsset> compatibleAsset(List<GitHubAsset> assets, String osName, String arch) {
        if (assets == null || assets.isEmpty()) {
            return Optional.empty();
        }
        return assets.stream()
                .filter(asset -> isCompatibleAssetName(asset.name(), osName, arch))
                .max(Comparator.comparingInt(asset -> assetScore(asset.name(), osName, arch)));
    }

    private static boolean isCompatibleAssetName(String assetName, String osName, String arch) {
        String name = assetName == null ? "" : assetName.toLowerCase(Locale.ROOT);
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String normalizedArch = normalizeArch(arch);

        if (os.contains("win")) {
            return name.endsWith(".exe") || name.endsWith(".msi");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return name.endsWith(".dmg") || name.endsWith(".pkg");
        }
        if (os.contains("linux")) {
            if (!name.endsWith(".appimage")) {
                return false;
            }
            if ("aarch64".equals(normalizedArch)) {
                return name.contains("aarch64") || name.contains("arm64");
            }
            if ("x86_64".equals(normalizedArch)) {
                return name.contains("x86_64") || name.contains("amd64") || !name.contains("aarch64");
            }
            return true;
        }
        return false;
    }

    private static boolean isLauncherAssetName(String assetName) {
        String name = assetName == null ? "" : assetName.toLowerCase(Locale.ROOT);
        return name.endsWith(".exe")
                || name.endsWith(".msi")
                || name.endsWith(".dmg")
                || name.endsWith(".pkg")
                || name.endsWith(".appimage");
    }

    private static int assetScore(String assetName, String osName, String arch) {
        String name = assetName == null ? "" : assetName.toLowerCase(Locale.ROOT);
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String normalizedArch = normalizeArch(arch);
        int score = 0;
        if (os.contains("win")) {
            score += name.endsWith(".exe") ? 20 : 10;
            score += name.contains("windows") || name.contains("win") ? 4 : 0;
        } else if (os.contains("mac") || os.contains("darwin")) {
            score += name.endsWith(".dmg") ? 20 : 10;
            score += name.contains("mac") || name.contains("darwin") ? 4 : 0;
        } else if (os.contains("linux")) {
            score += name.endsWith(".appimage") ? 20 : 0;
            if ("aarch64".equals(normalizedArch) && (name.contains("aarch64") || name.contains("arm64"))) {
                score += 8;
            }
            if ("x86_64".equals(normalizedArch) && (name.contains("x86_64") || name.contains("amd64"))) {
                score += 8;
            }
        }
        return score;
    }

    private Path installerTarget(String assetName) {
        Path downloads = Path.of(System.getProperty("user.home", "."), "Downloads");
        Path directory = Files.isDirectory(downloads) && Files.isWritable(downloads)
                ? downloads
                : Path.of(System.getProperty("java.io.tmpdir"));
        return directory.resolve(safeFilename(assetName));
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "ModtaleLauncher/" + LauncherVersion.current());
    }

    private static boolean isLinuxAppImage(Path path) {
        return path != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".appimage");
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
            throw new ModtaleApiException("GitHub returned HTTP " + status + " for " + target, status, null);
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
            @JsonProperty("browser_download_url") String browserDownloadUrl
    ) {
    }
}
