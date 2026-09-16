package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import net.modtale.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HytaleGameUpdaterTest {
    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(strings = {"release", "pre-release", "v0.4"})
    void everyChannelInstallsLatestAndChecksAgainOnEveryPlay(String branch) throws Exception {
        LauncherSettings settings = settings(branch);
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger installs = new AtomicInteger();
        AtomicInteger latestBuild = new AtomicInteger(29);
        List<String> progress = new ArrayList<>();
        HytaleGameUpdater updater = new HytaleGameUpdater(temp.resolve("managed"), (current, requestedBranch) -> {
            requests.incrementAndGet();
            assertEquals(branch, requestedBranch);
            return List.of(version(branch, 19, false), version(branch, latestBuild.get(), true));
        }, (version, token, destination, status) -> {
            assertEquals("test-token", token);
            installs.incrementAndGet();
            assertFalse(Files.exists(destination.resolve(".modtale-build")));
            game(destination, "0.6.6");
        });

        updater.prepare(settings, progress::add);
        Path first = settings.hytaleGameDirectory();
        assertEquals(29, settings.getHytaleBuild());
        assertEquals(temp.resolve("managed").resolve(branch).resolve("29"), first);
        assertEquals("keep", Files.readString(temp.resolve("old/UserData/world")));
        updater.prepare(settings, progress::add);
        assertEquals(2, requests.get());
        assertEquals(1, installs.get());
        latestBuild.set(30);
        updater.prepare(settings, progress::add);
        assertEquals(30, settings.getHytaleBuild());
        assertEquals(2, installs.get());
        assertTrue(Files.exists(first.resolve("Server/HytaleServer.jar")));
        assertTrue(progress.getFirst().contains(branch));
    }

    @Test
    void channelSwitchNeverUsesPreviouslyConfiguredChannel() throws Exception {
        LauncherSettings settings = settings("release");
        HytaleGameUpdater updater = new HytaleGameUpdater(temp.resolve("managed"), (s, branch) -> List.of(version(branch, 29, true)),
                (v, token, destination, status) -> game(destination, "0.6.6"));
        updater.prepare(settings, ignored -> {});
        Path release = settings.hytaleGameDirectory();
        settings.setHytaleBranch("pre-release");
        updater.prepare(settings, ignored -> {});
        assertNotEquals(release, settings.hytaleGameDirectory());
        assertTrue(settings.hytaleGameDirectory().startsWith(temp.resolve("managed/pre-release")));
    }

    @Test
    void failedOrIncompleteUpdatePreservesOriginalGameAndNeverRecordsSuccess() throws Exception {
        LauncherSettings settings = settings("release");
        String original = settings.getHytaleGamePath();
        AtomicReference<Path> staging = new AtomicReference<>();
        HytaleGameUpdater.Installer broken = (v, token, destination, progress) -> {
            staging.set(destination);
            Files.writeString(destination.resolve("partial"), "partial");
            throw new IOException("verification failed");
        };
        for (HytaleGameUpdater.Installer installer : List.of(broken, (v, token, destination, progress) -> {
            staging.set(destination);
            Files.writeString(destination.resolve("partial"), "incomplete");
        })) {
            var updater = new HytaleGameUpdater(temp.resolve("managed"), (s, b) -> List.of(version(b, 29, true)), installer);
            assertThrows(HytaleApiException.class, () -> updater.prepare(settings, ignored -> {}));
            assertEquals(original, settings.getHytaleGamePath());
            assertEquals(19, settings.getHytaleBuild());
            assertFalse(Files.exists(staging.get()));
            assertFalse(Files.exists(temp.resolve("managed/release/29")));
        }
    }

    @Test
    void networkFailureOrMissingLatestDoesNotFallBackToStaleGame() throws Exception {
        LauncherSettings settings = settings("release");
        String original = settings.getHytaleGamePath();
        var updater = new HytaleGameUpdater(temp.resolve("managed"), (s, b) -> {
            throw new HytaleApiException("service unavailable");
        }, (v, t, d, p) -> fail("Should not install"));
        assertThrows(HytaleApiException.class, () -> updater.prepare(settings, ignored -> {}));
        assertEquals(original, settings.getHytaleGamePath());
        var noLatest = new HytaleGameUpdater(temp.resolve("managed"), (s, b) -> List.of(version(b, 19, false)),
                (v, t, d, p) -> fail("Should not install"));
        assertThrows(HytaleApiException.class, () -> noLatest.prepare(settings, ignored -> {}));
    }

    @Test
    void damagedCachedBuildIsReplacedInsteadOfLaunched() throws Exception {
        LauncherSettings settings = settings("release");
        AtomicInteger installs = new AtomicInteger();
        var updater = new HytaleGameUpdater(temp.resolve("managed"), (s, b) -> List.of(version(b, 29, true)),
                (v, t, d, p) -> { installs.incrementAndGet(); game(d, "0.6.6"); });
        updater.prepare(settings, ignored -> {});
        Files.delete(settings.hytaleGameDirectory().resolve("Assets.zip"));
        updater.prepare(settings, ignored -> {});
        assertEquals(2, installs.get());
    }

    @Test
    void launcherNeverStartsOrCreatesLaunchTokensWhenUpdateFails() throws Exception {
        LauncherSettings settings = settings("release");
        var updater = new HytaleGameUpdater(temp.resolve("managed"), (s, b) -> {
            throw new HytaleApiException("No connection");
        }, (v, token, destination, progress) -> fail("No install expected"));
        var auth = new HytaleAuthService(null, null) {
            @Override public HytaleAuthSession ensureFreshSessionForLaunch(LauncherSettings ignored) {
                fail("Launch must stop when the update check fails");
                return null;
            }
        };
        assertThrows(HytaleApiException.class, () -> new HytaleGameLauncher(auth, updater).launch(settings));
        assertEquals(temp.resolve("old"), settings.hytaleGameDirectory());
    }

    @Test
    void signedDownloadsCannotSendCredentialsToOtherHosts() throws Exception {
        assertEquals("game-patches.hytale.com", HytalePatchInstaller.officialArtifact(
                "https://game-patches.hytale.com/patches/linux/amd64/release/0/29.pwr?verify=test").getHost());
        for (String url : List.of("http://game-patches.hytale.com/game.pwr", "https://example.com/game.pwr",
                "https://game-patches.hytale.com.evil.test/game.pwr", "https://user@game-patches.hytale.com/game.pwr")) {
            assertThrows(IOException.class, () -> HytalePatchInstaller.officialArtifact(url));
        }
    }

    private LauncherSettings settings(String branch) throws IOException {
        Path old = temp.resolve("old");
        game(old, "0.5.6");
        Files.createDirectories(old.resolve("UserData"));
        Files.writeString(old.resolve("UserData/world"), "keep");
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleGamePath(old.toString());
        settings.setHytaleBranch(branch);
        settings.setHytaleBuild(19);
        HytaleAuthSession session = new HytaleAuthSession();
        session.setUuid("test-user");
        session.setRefreshToken("test-refresh");
        session.setAccessToken("test-token");
        settings.setHytaleAuthSession(session);
        return settings;
    }

    static void game(Path path, String version) throws IOException {
        Files.createDirectories(HytaleGameLauncher.resolveExecutable(path).getParent());
        Files.writeString(HytaleGameLauncher.resolveExecutable(path), "client");
        Files.writeString(path.resolve("Assets.zip"), "assets");
        Files.createDirectories(path.resolve("Server"));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Implementation-Version", version);
        try (var output = new JarOutputStream(Files.newOutputStream(path.resolve("Server/HytaleServer.jar")), manifest)) { }
    }

    private static HytaleVersion version(String branch, int build, boolean latest) {
        return new HytaleVersion(branch, build, 0, latest, "", "", "");
    }
}
