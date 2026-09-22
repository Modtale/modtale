package net.modtale.launcher.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

final class LauncherPayloadInstaller {
    private static final long MAX_EXTRACTED_BYTES = 512L * 1024 * 1024;

    void install(Path archive, String version) throws IOException {
        String root = System.getenv("MODTALE_UPDATE_ROOT");
        String executable = System.getenv("MODTALE_LAUNCHER_EXECUTABLE");
        if (root == null || executable == null) {
            throw new IOException("This launcher was started without its native bootstrap. Reopen the installed launcher to update.");
        }
        Path updateRoot = Path.of(root);
        Files.createDirectories(updateRoot);
        Path staged = Files.createTempDirectory(updateRoot, "version-");
        Process helper = null;
        boolean prepared = false;
        try {
            extract(archive, staged, version);
            List<String> probe = javaCommand(staged, "net.modtale.launcher.RuntimeProbe");
            runProbe(probe);
            Path ready = staged.resolve("restart-ready");
            List<String> restart = javaCommand(staged, LauncherPayloadInstaller.class.getName());
            restart.addAll(List.of(updateRoot.toString(), executable, Long.toString(ProcessHandle.current().pid()),
                    staged.getFileName().toString()));
            helper = new ProcessBuilder(restart)
                    .redirectErrorStream(true).redirectOutput(updateRoot.resolve("update.log").toFile()).start();
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (!Files.exists(ready)) {
                if (!helper.isAlive() || System.nanoTime() > deadline) {
                    helper.destroyForcibly();
                    throw new IOException("Could not prepare the update restart. See " + updateRoot.resolve("update.log"));
                }
                Thread.sleep(50);
            }
            prepared = true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Update preparation was interrupted.", ex);
        } finally {
            if (!prepared && helper != null) helper.destroyForcibly();
            try {
                Files.deleteIfExists(archive);
                if (!prepared) {
                    try (var files = Files.walk(staged)) {
                        for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                    }
                }
            } catch (IOException cleanup) {
                // Cleanup must not turn a prepared restart into a failed update.
            }
        }
    }

    static void extract(Path archive, Path destination, String version) throws IOException {
        long total = 0;
        int count = 0;
        boolean launcher = false;
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                if (++count > 256 || entry.isDirectory() || name.contains("/") || name.contains("\\")
                        || !(name.endsWith(".jar") || name.equals("bootstrap.json"))) {
                    throw new IOException("Invalid launcher update entry: " + name);
                }
                Path file = destination.resolve(name);
                try (var output = Files.newOutputStream(file, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer = new byte[65536];
                    for (int read; (read = zip.read(buffer)) != -1;) {
                        total += read;
                        if (total > MAX_EXTRACTED_BYTES) throw new IOException("Launcher update exceeds the size limit.");
                        output.write(buffer, 0, read);
                    }
                }
                if (name.startsWith("modtale-launcher-") && name.endsWith(".jar")) launcher = true;
            }
        }
        if (!launcher || !Files.isRegularFile(destination.resolve("bootstrap.json"))) {
            throw new IOException("The launcher update is incomplete.");
        }
        var config = new ObjectMapper().readTree(destination.resolve("bootstrap.json").toFile());
        boolean matches = false;
        for (var arg : config.path("jvm_args")) {
            if (arg.asText().equals("-Dmodtale.launcherVersion=" + version)) matches = true;
        }
        if (!matches) throw new IOException("The downloaded launcher version does not match the requested update.");
    }

    private static List<String> javaCommand(Path app, String mainClass) {
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        return new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp", app.resolve("*").toString(), mainClass));
    }

    private static void runProbe(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("The updated launcher did not pass its startup check.");
        }
        if (process.exitValue() != 0) throw new IOException("The updated launcher failed its startup check.");
    }

    static void activate(Path root, String directory) throws IOException {
        Path temporary = root.resolve("active-" + UUID.randomUUID());
        try {
            Files.writeString(temporary, directory, StandardCharsets.UTF_8);
            Files.move(temporary, root.resolve("active"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        String executable = args[1];
        long parent = Long.parseLong(args[2]);
        String directory = args[3];
        String previous = Files.exists(root.resolve("active")) ? Files.readString(root.resolve("active")) : null;
        Files.writeString(root.resolve(directory).resolve("restart-ready"), "ready");
        var process = ProcessHandle.of(parent);
        if (process.isPresent()) process.get().onExit().get(90, TimeUnit.SECONDS);
        try {
            activate(root, directory);
            startLauncher(executable);
        } catch (Exception failure) {
            if (previous == null) Files.deleteIfExists(root.resolve("active")); else activate(root, previous);
            Files.writeString(root.resolve("update-failure"), "The updated launcher could not start. Your previous version has been restored. Try updating again.");
            failure.printStackTrace();
            startLauncher(executable);
        }
    }

    private static void startLauncher(String executable) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(executable);
        for (String key : List.of("APPDIR", "APPIMAGE", "ARGV0")) builder.environment().remove(key);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        Process child = builder.start();
        if (child.waitFor(3, TimeUnit.SECONDS) && child.exitValue() != 0) {
            throw new IOException("Updated launcher exited with code " + child.exitValue());
        }
    }
}
