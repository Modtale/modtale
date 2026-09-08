package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import net.modtale.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HytaleGameVersionResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void labelsInstalledOfficialPackageBuildFromServerJarManifest() throws Exception {
        Path dataRoot = tempDir.resolve("Hytale");
        Path packageDirectory = dataRoot.resolve(Path.of("install", "release", "package"));
        Path gameDirectory = packageDirectory.resolve(Path.of("game", "latest"));
        Files.createDirectories(gameDirectory.resolve("Server"));
        Files.createDirectories(packageDirectory.resolve(Path.of("sig", "build-17")));
        writeServerJar(gameDirectory.resolve(Path.of("Server", "HytaleServer.jar")), "0.5.4");

        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleGamePath(gameDirectory.toString());
        settings.setHytaleUserDataPath(dataRoot.resolve("UserData").toString());

        Map<Integer, String> labels = HytaleGameVersionResolver.resolveBuildVersions(settings);
        List<HytaleVersion> versions = HytaleGameVersionResolver.labelVersions(settings, List.of(
                new HytaleVersion("release", 17, 0, true, "pwr", "head", "sig")
        ));

        assertEquals("0.5.4", labels.get(17));
        assertEquals("0.5.4", versions.getFirst().gameVersion());
        assertEquals("0.5.4 - Build 17 (latest)", versions.getFirst().toString());
    }

    @Test
    void labelsInstalledVersionedPatchlineFromMatchingPackage() throws Exception {
        Path dataRoot = tempDir.resolve("Hytale");
        Path releasePackage = dataRoot.resolve(Path.of("install", "release", "package"));
        Path releaseGame = releasePackage.resolve(Path.of("game", "latest"));
        Path previousPackage = dataRoot.resolve(Path.of("install", "v0.4", "package"));
        Path previousGame = previousPackage.resolve(Path.of("game", "latest"));
        Files.createDirectories(releaseGame.resolve("Server"));
        Files.createDirectories(releasePackage.resolve(Path.of("sig", "build-7")));
        Files.createDirectories(previousGame.resolve("Server"));
        Files.createDirectories(previousPackage.resolve(Path.of("sig", "build-7")));
        writeServerJar(releaseGame.resolve(Path.of("Server", "HytaleServer.jar")), "0.5.6");
        writeServerJar(previousGame.resolve(Path.of("Server", "HytaleServer.jar")), "0.4.9");

        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleBranch("release");
        settings.setHytaleGamePath(releaseGame.toString());
        settings.setHytaleUserDataPath(dataRoot.resolve("UserData").toString());

        List<HytaleVersion> versions = HytaleGameVersionResolver.labelVersions(settings, "v0.4", List.of(
                new HytaleVersion("v0.4", 7, 0, true, "pwr", "head", "sig")
        ));

        assertEquals("0.4.9", versions.getFirst().gameVersion());
        assertEquals("0.4.9", versions.getFirst().displayVersion());
    }

    @Test
    void readsOfficialLauncherLogBuildVersionPairs() throws Exception {
        Path dataRoot = tempDir.resolve("Hytale");
        Files.createDirectories(dataRoot.resolve("UserData"));
        Files.writeString(dataRoot.resolve("hytale-launcher.log"), """
                time=2026-06-13T10:00:00Z level=INFO msg="applying game update" from="&{build:16 version:0.5.3}" to="{build:17 version:0.5.4}" step_from=16 step_to=17
                time=2026-06-13T10:01:00Z level=INFO msg="game process started" game_build=17 game_version=0.5.4
                """);

        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleUserDataPath(dataRoot.resolve("UserData").toString());

        Map<Integer, String> labels = HytaleGameVersionResolver.resolveBuildVersions(settings);

        assertEquals("0.5.3", labels.get(16));
        assertEquals("0.5.4", labels.get(17));
    }

    private void writeServerJar(Path jarPath, String version) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Implementation-Version", version);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.flush();
        }
    }
}
