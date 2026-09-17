package net.modtale.launcher.hytale;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.settings.SettingsStore;

/** Installs immutable, verified builds without modifying the official install or UserData. */
public final class HytaleGameUpdater {
    @FunctionalInterface
    interface Versions { List<HytaleVersion> get(LauncherSettings settings, String branch); }
    @FunctionalInterface
    interface Installer { void install(HytaleVersion version, String token, Path destination, Consumer<String> progress) throws IOException, InterruptedException; }

    private final Path root;
    private final Versions versions;
    private final Installer installer;

    public HytaleGameUpdater(HytaleAuthService auth) {
        this(SettingsStore.defaultSettingsPath().getParent().resolve("game")
                        .resolve(HytalePlatform.os() + "-" + HytalePlatform.arch()),
                auth::getAvailableVersions, new HytalePatchInstaller()::install);
    }

    HytaleGameUpdater(Path root, Versions versions, Installer installer) {
        this.root = root;
        this.versions = versions;
        this.installer = installer;
    }

    public void prepare(LauncherSettings settings, Consumer<String> progress) {
        String branch = HytaleApiClient.normalizeBranch(settings.getHytaleBranch());
        progress.accept("Checking Hytale " + branch + " for updates...");
        // Build numbers saved by the UI are display state, not a pin: every channel follows its latest build.
        List<HytaleVersion> available;
        String account = LauncherSettings.hytaleAccountId(settings.getHytaleAuthSession());
        String platform = HytalePlatform.os() + "/" + HytalePlatform.arch();
        try {
            available = versions.get(settings, branch);
            settings.cacheHytaleVersions(account, platform, branch, available);
        } catch (HytaleApiException ex) {
            if (ex.statusCode() == 429 && useInstalledLatest(settings, branch, account, platform, progress)) return;
            throw ex;
        }
        HytaleVersion latest = available.stream()
                .filter(version -> branch.equals(version.branch()) && version.latest() && version.build() > 0)
                .max(Comparator.comparingInt(HytaleVersion::build))
                .orElseThrow(() -> new HytaleApiException("No current build is available for Hytale " + branch + "."));
        if (latest.fromBuild() != 0) {
            throw new HytaleApiException("Hytale did not provide a full installation for " + branch + ".");
        }
        Path channel = root.resolve(branch);
        Path target = channel.resolve(Integer.toString(latest.build()));
        try {
            Files.createDirectories(channel);
            try (FileChannel lockFile = FileChannel.open(channel.resolve("install.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    var lock = lockFile.tryLock()) {
                if (lock == null) throw new HytaleApiException("Another launcher is updating this Hytale channel. Try again when it finishes.");
                if (!complete(target, latest)) {
                    Path staging = Files.createTempDirectory(channel, ".install-");
                    try {
                        HytaleAuthSession session = settings.getHytaleAuthSession();
                        if (session == null || session.getAccessToken().isBlank()) {
                            throw new HytaleApiException("Sign in to Hytale before downloading a game update.");
                        }
                        installer.install(latest, session.getAccessToken(), staging, progress);
                        requireGame(staging);
                        Files.writeString(staging.resolve(".modtale-build"), identity(latest));
                        // Only launcher-owned, incomplete builds can be removed here.
                        deleteTree(target);
                        Files.move(staging, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        deleteTree(staging);
                    }
                }
            }
            settings.setHytaleGamePath(target.toString());
            settings.setHytaleBuild(latest.build());
            progress.accept("Hytale " + branch + " build " + latest.build() + " is ready.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new HytaleApiException("Hytale update interrupted. The previous installation is unchanged.");
        } catch (IOException | java.nio.channels.OverlappingFileLockException ex) {
            throw new HytaleApiException("Could not update Hytale. The game was not launched; please try again.", ex);
        }
    }

    private boolean useInstalledLatest(LauncherSettings settings, String branch, String account,
            String platform, Consumer<String> progress) {
        HytaleVersion known = settings.cachedHytaleVersions(account, platform, branch).stream()
                .filter(version -> branch.equals(version.branch()) && version.latest() && version.build() > 0)
                .max(Comparator.comparingInt(HytaleVersion::build)).orElse(null);
        if (known == null) return false;
        Path target = root.resolve(branch).resolve(Integer.toString(known.build()));
        try {
            if (!complete(target, known)) return false;
        } catch (IOException ex) {
            return false;
        }
        settings.setHytaleGamePath(target.toString());
        settings.setHytaleBuild(known.build());
        progress.accept("Hytale's update check is rate limited. Launching installed " + branch
                + " build " + known.build() + ", the last known latest build. Updates will be checked on the next launch.");
        return true;
    }

    private static boolean complete(Path game, HytaleVersion version) throws IOException {
        Path marker = game.resolve(".modtale-build");
        if (!Files.isRegularFile(marker) || !Files.readString(marker).equals(identity(version))) return false;
        try { requireGame(game); return true; }
        catch (IOException ex) { return false; }
    }

    private static String identity(HytaleVersion version) { return version.branch() + ":" + version.build(); }

    static void requireGame(Path game) throws IOException {
        if (!Files.isRegularFile(HytaleGameLauncher.resolveExecutable(game))
                || !Files.isRegularFile(game.resolve("Assets.zip"))
                || HytaleGameVersionResolver.serverVersionFromJar(game.resolve("Server/HytaleServer.jar")).isEmpty()) {
            throw new IOException("The downloaded Hytale build is incomplete.");
        }
    }

    static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try (var entries = Files.walk(path)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) Files.delete(entry);
        }
    }
}
