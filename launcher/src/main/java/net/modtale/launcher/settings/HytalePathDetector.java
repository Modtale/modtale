package net.modtale.launcher.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class HytalePathDetector {

    private HytalePathDetector() {
    }

    public static Path defaultModsDirectory() {
        return detectExistingModsDirectory().orElseGet(() -> defaultUserDataDirectory().resolve("Mods"));
    }

    public static Path defaultSavesDirectory() {
        return detectExistingSavesDirectory().orElseGet(() -> defaultUserDataDirectory().resolve("Saves"));
    }

    public static Path defaultUserDataDirectory() {
        return detectExistingUserDataDirectory().orElseGet(() -> userDataCandidates().getFirst());
    }

    public static Path defaultGameDirectory() {
        return detectExistingGameDirectory().orElseGet(() -> gameCandidates().getFirst());
    }

    public static Path defaultJavaExecutable() {
        return detectExistingJavaExecutable().orElseGet(HytalePathDetector::currentJavaExecutable);
    }

    public static Optional<Path> detectExistingJavaExecutable() {
        return javaExecutableCandidates().stream().filter(Files::isRegularFile).findFirst();
    }

    public static Optional<Path> detectExistingGameDirectory() {
        return gameCandidates().stream().filter(HytalePathDetector::containsClientExecutable).findFirst();
    }

    public static List<Path> candidates() {
        return userDataCandidates().stream()
                .flatMap(path -> java.util.stream.Stream.of(path.resolve("Mods"), path.resolve("mods"), path.resolve(Path.of("userdata", "mods"))))
                .distinct()
                .toList();
    }

    public static List<Path> userDataCandidates() {
        return hytaleDataDirectoryCandidates().stream()
                .flatMap(path -> Stream.of(path.resolve("UserData"), path.resolve("userdata"), path))
                .distinct()
                .toList();
    }

    public static List<Path> gameCandidates() {
        List<Path> paths = new ArrayList<>();
        hytaleDataDirectoryCandidates().forEach(root -> {
            paths.add(root.resolve(Path.of("install", "release", "package", "game", "latest")));
            paths.add(root.resolve(Path.of("install", "pre-release", "package", "game", "latest")));
            paths.add(root.resolve(Path.of("install", "prerelease", "package", "game", "latest")));
            paths.add(root.resolve(Path.of("install", "release", "package", "game")));
            paths.add(root.resolve(Path.of("install", "pre-release", "package", "game")));
            paths.add(root.resolve(Path.of("install", "prerelease", "package", "game")));
            paths.add(root);
        });

        String home = System.getProperty("user.home", ".");
        paths.add(Path.of(home, "Hytale", "Game"));
        paths.add(Path.of(home, "Hytale"));
        paths.add(Path.of(home, ".modtale", "launcher", "hytale"));
        return paths.stream().distinct().toList();
    }

    public static List<Path> javaExecutableCandidates() {
        String executable = javaExecutableName();
        List<Path> paths = new ArrayList<>();
        hytaleDataDirectoryCandidates().forEach(root -> {
            paths.add(root.resolve(Path.of("install", "release", "package", "jre", "latest", "bin", executable)));
            paths.add(root.resolve(Path.of("install", "pre-release", "package", "jre", "latest", "bin", executable)));
            paths.add(root.resolve(Path.of("install", "prerelease", "package", "jre", "latest", "bin", executable)));
            paths.add(root.resolve(Path.of("jre", "latest", "bin", executable)));
        });
        paths.add(currentJavaExecutable());
        return paths.stream().distinct().toList();
    }

    public static boolean isCurrentJavaExecutable(Path path) {
        if (path == null) {
            return false;
        }
        return path.toAbsolutePath().normalize().equals(currentJavaExecutable().toAbsolutePath().normalize());
    }

    public static List<Path> hytaleDataDirectoryCandidates() {
        String home = System.getProperty("user.home", ".");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> paths = new ArrayList<>();

        if (os.contains("win")) {
            addEnvPath(paths, "APPDATA", "Hytale");
            paths.add(Path.of(home, "AppData", "Roaming", "Hytale"));
            addEnvPath(paths, "LOCALAPPDATA", "Hytale");
            addEnvPath(paths, "ProgramFiles", "Hypixel Studios", "Hytale Launcher");
            paths.add(Path.of("C:", "Program Files", "Hypixel Studios", "Hytale Launcher"));
        } else if (os.contains("mac")) {
            paths.add(Path.of(home, "Library", "Application Support", "Hytale"));
            paths.add(Path.of("/", "Applications", "Hytale Launcher.app", "Contents", "MacOS"));
        } else {
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            if (xdgDataHome != null && !xdgDataHome.isBlank()) {
                paths.add(Path.of(xdgDataHome, "Hytale"));
            }
            paths.add(Path.of(home, ".var", "app", "com.hypixel.HytaleLauncher", "data", "Hytale"));
            paths.add(Path.of(home, ".local", "share", "Hytale"));
            paths.add(Path.of(home, ".config", "Hytale"));
            paths.add(Path.of(home, ".hytale"));
        }

        paths.add(Path.of(home, "Hytale"));
        return paths.stream().distinct().toList();
    }

    private static Path currentJavaExecutable() {
        return Path.of(System.getProperty("java.home", "."), "bin", javaExecutableName());
    }

    private static String javaExecutableName() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        return executable;
    }

    private static void addEnvPath(List<Path> paths, String variable, String first, String... more) {
        String value = System.getenv(variable);
        if (value != null && !value.isBlank()) {
            paths.add(Path.of(value, first).resolve(Path.of("", more)));
        }
    }

    public static Optional<Path> detectExistingModsDirectory() {
        return candidates().stream().filter(Files::isDirectory).findFirst();
    }

    public static Optional<Path> detectExistingSavesDirectory() {
        return userDataCandidates().stream()
                .flatMap(path -> java.util.stream.Stream.of(path.resolve("Saves"), path.resolve("saves")))
                .filter(Files::isDirectory)
                .findFirst();
    }

    public static Optional<Path> detectExistingUserDataDirectory() {
        return userDataCandidates().stream().filter(Files::isDirectory).findFirst();
    }

    public static boolean containsClientExecutable(Path path) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return Files.isRegularFile(path.resolve(Path.of("Client", "Hytale.app", "Contents", "MacOS", "HytaleClient")));
        }
        if (os.contains("win")) {
            return Files.isRegularFile(path.resolve(Path.of("Client", "HytaleClient.exe")));
        }
        return Files.isRegularFile(path.resolve(Path.of("Client", "HytaleClient")));
    }
}
