package net.modtale.launcher.hytale;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;
import net.modtale.launcher.cache.LauncherCachePaths;

/** Applies official Wharf patches with a pinned, checksum-verified upstream Butler binary. */
final class HytalePatchInstaller {
    private static final String BUTLER_VERSION = "15.31.0";
    private static final Map<String, String> BUTLER_HASHES = Map.of(
            "linux-amd64", "1e536377187894ef5fe7f35edfb29df256df63e50e7f6b97ebd54bc5aba4c055",
            "linux-arm64", "fbe7dd5b0ea81355916275276db109666f495bf26e75c4bd6b4d510952a0c3cf",
            "darwin-amd64", "9d32a015cb3b85fd7a317d11c5ba1729bcb8eb62c0c7b1fcea04d16d8bbbeee3",
            "darwin-arm64", "8dceeab42b311a0ffd8c13d9e064afd53045382a9331b38f587701231fe1e7f2",
            "windows-amd64", "b4666e7ad97adb544c38d9dccc8b168ee4583469a90af058ac668a557ed030ec");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    void install(HytaleVersion version, String token, Path destination, Consumer<String> progress)
            throws IOException, InterruptedException {
        Path scratch = Files.createTempDirectory(destination.getParent(), ".patch-");
        try {
            Path patcher = patcher(progress, scratch.resolve("tools"));
            Path patch = scratch.resolve("game.pwr");
            Path signature = scratch.resolve("game.pws");
            download(officialArtifact(version.pwrUrl()), token, patch, progress, "Downloading Hytale " + version.branch() + " build " + version.build());
            download(officialArtifact(version.sigUrl()), token, signature, progress, "Downloading game verification data");
            Path empty = Files.createDirectory(scratch.resolve("empty"));
            Path checkpoints = Files.createDirectory(scratch.resolve("checkpoints"));
            progress.accept("Installing and verifying Hytale " + version.branch() + " build " + version.build() + "...");
            apply(patcher, patch, signature, empty, destination, checkpoints, scratch.resolve("patcher.log"));
        } finally {
            HytaleGameUpdater.deleteTree(scratch);
        }
    }

    static void apply(Path patcher, Path patch, Path signature, Path empty, Path destination, Path checkpoints, Path log)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(List.of(patcher.toString(), "apply", patch.toString(), empty.toString(),
                "--dir", destination.toString(), "--staging-dir", checkpoints.toString(), "--signature", signature.toString()))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if (!process.waitFor(30, TimeUnit.MINUTES)) throw new IOException("Hytale installation timed out.");
            if (process.exitValue() != 0) {
                if (Files.size(log) <= 16 * 1024) {
                    net.modtale.launcher.logging.LauncherLog.getLogger(HytalePatchInstaller.class)
                            .warn("Hytale patcher failed: " + Files.readString(log));
                }
                throw new IOException("Hytale patch verification failed (exit " + process.exitValue() + ").");
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.onExit().join();
            }
        }
    }

    static URI officialArtifact(String url) throws IOException {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !"game-patches.hytale.com".equals(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException ex) {
            throw new IOException("Hytale returned an invalid game download address.");
        }
    }

    private Path patcher(Consumer<String> progress, Path tools) throws IOException, InterruptedException {
        String platform = HytalePlatform.os() + "-" + HytalePlatform.arch();
        String expected = BUTLER_HASHES.get(platform);
        if (expected == null) throw new IOException("Game updates are not supported on this platform yet.");
        Path root = LauncherCachePaths.cacheDirectory("butler-" + BUTLER_VERSION + "-" + platform);
        String executable = HytalePlatform.isWindows() ? "butler.exe" : "butler";
        Files.createDirectories(root);
        Path archive = root.resolve("butler.zip");
        if (!Files.isRegularFile(archive) || !sha256(archive).equals(expected)) {
            Path partial = Files.createTempFile(root, "download-", ".zip");
            try {
                // GitHub's release download redirects to its asset CDN. No Hytale credentials are sent.
                download(URI.create("https://github.com/itchio/butler/releases/download/v" + BUTLER_VERSION
                        + "/butler-" + platform + ".zip"), null, partial, progress, "Downloading game update tools");
                if (!sha256(partial).equals(expected)) throw new IOException("Game update tool checksum mismatch.");
                Files.move(partial, archive, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(partial); }
        }
        // Each install gets its own executable so concurrent channels never replace a running tool.
        Files.createDirectories(tools);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                Path output = tools.resolve(entry.getName()).normalize();
                if (!output.startsWith(tools)) throw new IOException("Invalid update tool archive path.");
                if (entry.isDirectory()) Files.createDirectories(output);
                else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Path binary = tools.resolve(platform).resolve(executable);
        if (!Files.isRegularFile(binary)) throw new IOException("Game update tool is missing.");
        if (!HytalePlatform.isWindows() && !binary.toFile().setExecutable(true, true)) throw new IOException("Cannot run game update tool.");
        return binary;
    }

    private void download(URI uri, String token, Path file, Consumer<String> progress, String label)
            throws IOException, InterruptedException {
        progress.accept(label + "...");
        // Never forward bearer credentials across redirects, nor put signed URLs in logs/errors.
        HttpClient client = token == null ? HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30)).build() : http;
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofHours(1)).header("User-Agent", "ModtaleLauncher");
        if (token != null) request.header("Authorization", "Bearer " + token);
        HttpResponse<java.io.InputStream> response;
        try {
            response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException ex) {
            throw new IOException(label + " failed. Check your connection and try again.");
        }
        try (var input = response.body()) {
            if (response.statusCode() != 200) throw new IOException(label + " failed (HTTP " + response.statusCode() + ").");
            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            long received = 0;
            int lastPercent = 0;
            try (var output = Files.newOutputStream(file)) {
                byte[] buffer = new byte[128 * 1024];
                for (int count; (count = input.read(buffer)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    output.write(buffer, 0, count);
                    received += count;
                    int percent = total > 0 ? (int) (received * 100 / total) : -1;
                    if (percent >= lastPercent + 10) { lastPercent = percent; progress.accept(label + " (" + percent + "%)..."); }
                }
            }
            if (total >= 0 && received != total) throw new IOException("Incomplete game download.");
        }
    }

    private static String sha256(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[128 * 1024];
            for (int read; (read = input.read(bytes)) != -1;) digest.update(bytes, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
