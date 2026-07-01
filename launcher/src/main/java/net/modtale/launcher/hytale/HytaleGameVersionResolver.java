package net.modtale.launcher.hytale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.modtale.launcher.settings.HytalePathDetector;
import net.modtale.launcher.settings.LauncherSettings;

public final class HytaleGameVersionResolver {

    private static final Pattern LOG_GAME_PROCESS = Pattern.compile("\\bgame_build=(\\d+)\\s+game_version=([^\\s\"]+)");
    private static final Pattern LOG_RELEASE = Pattern.compile("\\{build:(\\d+)\\s+version:([^}]+)}");
    private static final Pattern BUILD_DIRECTORY = Pattern.compile("build-(\\d+)");

    private HytaleGameVersionResolver() {
    }

    public static List<HytaleVersion> labelVersions(LauncherSettings settings, List<HytaleVersion> versions) {
        return labelVersions(settings, settings == null ? "release" : settings.getHytaleBranch(), versions);
    }

    public static List<HytaleVersion> labelVersions(LauncherSettings settings, String branch, List<HytaleVersion> versions) {
        if (versions == null || versions.isEmpty()) {
            return List.of();
        }

        Map<Integer, String> labels = resolveBuildVersions(settings, branch);
        if (labels.isEmpty()) {
            return List.copyOf(versions);
        }

        return versions.stream()
                .map(version -> {
                    String label = labels.get(version.build());
                    return label == null || label.isBlank() ? version : version.withGameVersion(label);
                })
                .toList();
    }

    public static Map<Integer, String> resolveBuildVersions(LauncherSettings settings) {
        return resolveBuildVersions(settings, settings == null ? "release" : settings.getHytaleBranch());
    }

    public static Map<Integer, String> resolveBuildVersions(LauncherSettings settings, String branch) {
        Map<Integer, String> labels = new LinkedHashMap<>();
        hytaleRoots(settings).forEach(root -> readOfficialLauncherLog(root.resolve("hytale-launcher.log"), labels));
        installedBuild(settings, branch).ifPresent(build -> installedServerVersion(settings, branch)
                .ifPresent(version -> labels.put(build, version)));
        return labels;
    }

    public static Optional<String> installedServerVersion(LauncherSettings settings) {
        return installedServerVersion(settings, settings == null ? "release" : settings.getHytaleBranch());
    }

    public static Optional<String> installedServerVersion(LauncherSettings settings, String branch) {
        if (settings == null) {
            return Optional.empty();
        }
        return installedGameDirectory(settings, branch)
                .flatMap(gameDirectory -> serverVersionFromJar(gameDirectory.resolve(Path.of("Server", "HytaleServer.jar"))));
    }

    static Optional<String> serverVersionFromJar(Path jarPath) {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            return Optional.empty();
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String implementationVersion = manifest.getMainAttributes().getValue("Implementation-Version");
                if (implementationVersion != null && !implementationVersion.isBlank()) {
                    return Optional.of(implementationVersion.trim());
                }
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    static void readOfficialLauncherLog(Path logPath, Map<Integer, String> labels) {
        if (logPath == null || !Files.isRegularFile(logPath)) {
            return;
        }
        try (Stream<String> lines = Files.lines(logPath)) {
            lines.forEach(line -> {
                Matcher process = LOG_GAME_PROCESS.matcher(line);
                while (process.find()) {
                    labels.put(parseBuild(process.group(1)), process.group(2).trim());
                }
                Matcher release = LOG_RELEASE.matcher(line);
                while (release.find()) {
                    labels.put(parseBuild(release.group(1)), release.group(2).trim());
                }
            });
        } catch (IOException ignored) {
            // Best-effort: the official launcher may be writing this file while we read.
        }
    }

    static Optional<Integer> installedBuild(LauncherSettings settings) {
        return installedBuild(settings, settings == null ? "release" : settings.getHytaleBranch());
    }

    static Optional<Integer> installedBuild(LauncherSettings settings, String branch) {
        if (settings == null) {
            return Optional.empty();
        }

        Path packageDirectory = installedGameDirectory(settings, branch)
                .flatMap(HytaleGameVersionResolver::packageDirectory)
                .orElse(null);
        if (packageDirectory == null) {
            return Optional.empty();
        }
        Path sigDirectory = packageDirectory.resolve("sig");
        if (!Files.isDirectory(sigDirectory)) {
            return Optional.empty();
        }
        try (Stream<Path> children = Files.list(sigDirectory)) {
            return children
                    .filter(Files::isDirectory)
                    .map(path -> BUILD_DIRECTORY.matcher(path.getFileName().toString()))
                    .filter(Matcher::matches)
                    .map(matcher -> parseBuild(matcher.group(1)))
                    .max(Integer::compareTo);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Path> installedGameDirectory(LauncherSettings settings, String branch) {
        if (settings == null) {
            return Optional.empty();
        }
        String normalizedBranch = HytaleApiClient.normalizeBranch(branch);
        for (Path root : hytaleRoots(settings)) {
            for (String branchName : branchDirectoryNames(normalizedBranch)) {
                Path gameRoot = root.resolve(Path.of("install", branchName, "package", "game"));
                Path latest = gameRoot.resolve("latest");
                if (Files.isDirectory(latest)) {
                    return Optional.of(latest);
                }
                if (Files.isDirectory(gameRoot)) {
                    return Optional.of(gameRoot);
                }
            }
        }
        if (normalizedBranch.equals(HytaleApiClient.normalizeBranch(settings.getHytaleBranch()))
                && Files.isDirectory(settings.hytaleGameDirectory())) {
            return Optional.of(settings.hytaleGameDirectory());
        }
        return Optional.empty();
    }

    private static List<String> branchDirectoryNames(String branch) {
        String normalized = HytaleApiClient.normalizeBranch(branch);
        if ("pre-release".equals(normalized)) {
            return List.of("pre-release", "prerelease");
        }
        return List.of(normalized);
    }

    private static Optional<Path> packageDirectory(Path gameDirectory) {
        if (gameDirectory == null) {
            return Optional.empty();
        }

        Path current = gameDirectory.toAbsolutePath().normalize();
        while (current != null) {
            if ("package".equals(current.getFileName() == null ? "" : current.getFileName().toString())) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static List<Path> hytaleRoots(LauncherSettings settings) {
        List<Path> roots = new ArrayList<>();
        if (settings != null) {
            dataRootFromPackageDirectory(settings.hytaleGameDirectory()).ifPresent(roots::add);
            Path userData = settings.hytaleUserDataDirectory();
            if (userData != null && userData.getParent() != null) {
                roots.add(userData.getParent());
            }
        }
        roots.addAll(HytalePathDetector.hytaleDataDirectoryCandidates());
        return roots.stream().map(path -> path.toAbsolutePath().normalize()).distinct().toList();
    }

    private static Optional<Path> dataRootFromPackageDirectory(Path gameDirectory) {
        Optional<Path> packageDirectory = packageDirectory(gameDirectory);
        if (packageDirectory.isEmpty()) {
            return Optional.empty();
        }
        Path branchDirectory = packageDirectory.get().getParent();
        if (branchDirectory == null) {
            return Optional.empty();
        }
        Path installDirectory = branchDirectory.getParent();
        if (installDirectory == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(installDirectory.getParent());
    }

    private static int parseBuild(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
