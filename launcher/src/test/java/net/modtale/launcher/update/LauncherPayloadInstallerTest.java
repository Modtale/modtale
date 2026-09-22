package net.modtale.launcher.update;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherPayloadInstallerTest {
    @TempDir Path directory;

    private Map<String, String> payload() {
        var files = new LinkedHashMap<String, String>();
        files.put("modtale-launcher-1.2.0.jar", "launcher");
        files.put("dependency.jar", "dependency");
        files.put("bootstrap.json", "{\"jvm_args\":[\"-Dmodtale.launcherVersion=1.2.0\"]}");
        return files;
    }

    private Path archive(Map<String, String> entries) throws IOException {
        Path archive = Files.createTempFile(directory, "update-", ".zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs({org.junit.jupiter.api.condition.OS.LINUX, org.junit.jupiter.api.condition.OS.MAC})
    void restartHelperWaitsForShutdownAndRollsBackFailedLaunch() throws Exception {
        for (boolean fail : new boolean[]{false, true}) {
            Path root = Files.createTempDirectory(directory, "restart with spaces ");
            Path staged = Files.createDirectory(root.resolve("version-new"));
            LauncherPayloadInstaller.activate(root, "version-old");
            Path executable = root.resolve("launcher");
            String script = "#!/bin/sh\n"
                    + "active=$(cat \"$MODTALE_TEST_ROOT/active\")\n"
                    + (fail ? "if [ \"$active\" = version-new ]; then exit 1; fi\n" : "")
                    + "printf '%s' \"$active\" > \"$MODTALE_TEST_ROOT/launched\"\n";
            Files.writeString(executable, script);
            assertTrue(executable.toFile().setExecutable(true));
            Process parent = new ProcessBuilder("/bin/sh", "-c", "sleep 30").start();
            String classes = Path.of(LauncherPayloadInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", classes, LauncherPayloadInstaller.class.getName(), root.toString(), executable.toString(),
                    Long.toString(parent.pid()), staged.getFileName().toString());
            builder.environment().put("MODTALE_TEST_ROOT", root.toString());
            builder.redirectErrorStream(true).redirectOutput(root.resolve("helper.log").toFile());
            Process helper = builder.start();
            try {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(staged.resolve("restart-ready")) && helper.isAlive() && System.nanoTime() < deadline) Thread.sleep(20);
                assertTrue(Files.exists(staged.resolve("restart-ready")), () -> {
                    try { return Files.readString(root.resolve("helper.log")); } catch (IOException e) { return e.toString(); }
                });
                assertEquals("version-old", Files.readString(root.resolve("active")));
                assertFalse(Files.exists(root.resolve("launched")));
                parent.destroyForcibly().waitFor();
                assertTrue(helper.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(0, helper.exitValue());
                assertEquals(fail ? "version-old" : "version-new", Files.readString(root.resolve("launched")));
                assertEquals(fail, Files.exists(root.resolve("update-failure")));
            } finally {
                parent.destroyForcibly();
                helper.destroyForcibly();
            }
        }
    }

    @Test
    void validatesReleaseChecksumBeforeInstallation() throws Exception {
        Path file = Files.writeString(directory.resolve("payload.zip"), "payload");
        String digest = "sha256:" + java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        LauncherUpdateService.verifyDigest(file, digest);
        Files.writeString(file, "corrupt");
        assertThrows(IOException.class, () -> LauncherUpdateService.verifyDigest(file, digest));
        assertThrows(IOException.class, () -> LauncherUpdateService.verifyDigest(file, null));
    }

    @Test
    void stagesCompletePayloadWithoutActivatingIt() throws Exception {
        Path staged = Files.createDirectory(directory.resolve("version-123"));
        LauncherPayloadInstaller.extract(archive(payload()), staged, "1.2.0");
        assertEquals("launcher", Files.readString(staged.resolve("modtale-launcher-1.2.0.jar")));
        assertFalse(Files.exists(directory.resolve("active")));
        LauncherPayloadInstaller.activate(directory, "version-123");
        assertEquals("version-123", Files.readString(directory.resolve("active")));
        LauncherPayloadInstaller.activate(directory, "version-456");
        assertEquals("version-456", Files.readString(directory.resolve("active")));
        assertTrue(Files.exists(staged.resolve("modtale-launcher-1.2.0.jar")));
    }

    @Test
    void rejectsTraversalUnexpectedFilesAndWrongVersionWithoutChangingActiveVersion() throws Exception {
        LauncherPayloadInstaller.activate(directory, "version-previous");
        for (String invalid : new String[]{"../escaped.jar", "nested/evil.jar", "..\\escaped.jar", "script.exe"}) {
            var entries = payload();
            entries.put(invalid, "bad");
            Path staged = Files.createTempDirectory(directory, "version-");
            assertThrows(IOException.class, () -> LauncherPayloadInstaller.extract(archive(entries), staged, "1.2.0"));
        }
        assertThrows(IOException.class, () -> LauncherPayloadInstaller.extract(archive(payload()),
                Files.createTempDirectory(directory, "version-"), "9.9.9"));
        assertEquals("version-previous", Files.readString(directory.resolve("active")));
        assertFalse(Files.exists(directory.resolve("escaped.jar")));
    }

    @Test
    void rejectsIncompleteAndNonZipDownloads() throws Exception {
        assertThrows(IOException.class, () -> LauncherPayloadInstaller.extract(archive(Map.of("dependency.jar", "incomplete")),
                Files.createTempDirectory(directory, "version-"), "1.2.0"));
        Path bad = Files.writeString(directory.resolve("bad.zip"), "<html>download failed</html>");
        assertThrows(IOException.class, () -> LauncherPayloadInstaller.extract(bad,
                Files.createTempDirectory(directory, "version-"), "1.2.0"));
    }
}
