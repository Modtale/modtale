package net.modtale.launcher.hytale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.modtale.launcher.settings.HytalePathDetector;
import net.modtale.launcher.settings.LauncherSettings;

public class HytaleGameLauncher {

    private final HytaleAuthService authService;

    public HytaleGameLauncher(HytaleAuthService authService) {
        this.authService = authService;
    }

    public HytaleLaunchResult launch(LauncherSettings settings) {
        HytaleAuthSession session = authService.ensureFreshSessionForLaunch(settings);
        if (!session.hasLaunchTokens()) {
            throw new HytaleApiException("A fresh Hytale-authenticated session is required before launch.");
        }

        LaunchPaths paths = resolvePaths(settings);
        try {
            Files.createDirectories(paths.userDataDirectory());
            ProcessBuilder builder = new ProcessBuilder(command(paths, session));
            builder.directory(paths.workingDirectory().toFile());
            builder.redirectErrorStream(true);
            applyEnvironment(builder.environment(), paths);
            Process process = builder.start();
            return new HytaleLaunchResult(process, paths.executable(), session.getUsername(), session.getUuid());
        } catch (IOException ex) {
            throw new HytaleApiException("Could not launch Hytale: " + ex.getMessage(), ex);
        }
    }

    static List<String> command(LaunchPaths paths, HytaleAuthSession session) {
        List<String> command = new ArrayList<>();
        command.add(paths.executable().toString());
        command.addAll(authenticatedArguments(paths.gameDirectory(), paths.userDataDirectory(), paths.javaExecutable(), session));
        return command;
    }

    static List<String> authenticatedArguments(
            Path gameDirectory,
            Path userDataDirectory,
            Path javaExecutable,
            HytaleAuthSession session
    ) {
        if (session == null || !session.hasLaunchTokens()) {
            throw new HytaleApiException("A fresh Hytale-authenticated session is required before launch.");
        }
        List<String> args = new ArrayList<>();
        args.add("--app-dir");
        args.add(gameDirectory.toString());
        args.add("--user-dir");
        args.add(userDataDirectory.toString());
        args.add("--java-exec");
        args.add(javaExecutable.toString());
        args.add("--name");
        args.add(session.getUsername());
        args.add("--auth-mode");
        args.add("authenticated");
        args.add("--uuid");
        args.add(session.getUuid());
        args.add("--identity-token");
        args.add(session.getIdentityToken());
        args.add("--session-token");
        args.add(session.getSessionToken());
        return args;
    }

    static LaunchPaths resolvePaths(LauncherSettings settings) {
        Path configuredGameDirectory = settings.hytaleGameDirectory();
        if (configuredGameDirectory.toString().isBlank()) {
            throw new HytaleApiException("Choose the Hytale game folder before launching.");
        }
        Path gameDirectory = resolveGameDirectory(
                configuredGameDirectory,
                settings.getHytaleBranch(),
                settings.getHytaleBuild()
        );
        Path executable = resolveExecutable(gameDirectory);
        if (!Files.isRegularFile(executable)) {
            throw new HytaleApiException("Hytale client was not found. Choose the Hytale data folder or a game folder containing "
                    + resolveExecutable(configuredGameDirectory));
        }

        Path userDataDirectory = settings.hytaleUserDataDirectory();
        Path javaExecutable = resolveJavaExecutable(
                settings.hytaleJavaExecutable(),
                gameDirectory,
                userDataDirectory,
                settings.getHytaleBranch()
        );
        if (!Files.isRegularFile(javaExecutable)) {
            throw new HytaleApiException("Java executable was not found at " + javaExecutable);
        }

        Path workingDirectory = HytalePlatform.isMac()
                ? executable.getParent()
                : gameDirectory.resolve("Client");
        return new LaunchPaths(gameDirectory, userDataDirectory, javaExecutable, executable, workingDirectory);
    }

    static Path resolveGameDirectory(Path configuredDirectory, String branch, int build) {
        return gameDirectoryCandidates(configuredDirectory, branch, build).stream()
                .filter(HytaleGameLauncher::containsClientExecutable)
                .findFirst()
                .orElse(configuredDirectory);
    }

    static Path resolveJavaExecutable(Path configuredJava, Path gameDirectory, Path userDataDirectory, String branch) {
        if (Files.isRegularFile(configuredJava) && !HytalePathDetector.isCurrentJavaExecutable(configuredJava)) {
            return configuredJava;
        }

        Optional<Path> bundledJava = bundledJavaCandidates(gameDirectory, userDataDirectory, branch).stream()
                .filter(Files::isRegularFile)
                .findFirst();
        if (bundledJava.isPresent()) {
            return bundledJava.get();
        }

        if (Files.isRegularFile(configuredJava)) {
            return configuredJava;
        }

        return HytalePathDetector.detectExistingJavaExecutable().orElse(configuredJava);
    }

    static Path resolveExecutable(Path gameDirectory) {
        if (HytalePlatform.isMac()) {
            return gameDirectory.resolve(Path.of("Client", "Hytale.app", "Contents", "MacOS", "HytaleClient"));
        }
        if (HytalePlatform.isWindows()) {
            return gameDirectory.resolve(Path.of("Client", "HytaleClient.exe"));
        }
        return gameDirectory.resolve(Path.of("Client", "HytaleClient"));
    }

    private static void applyEnvironment(Map<String, String> environment, LaunchPaths paths) {
        Path clientDirectory = paths.gameDirectory().resolve("Client");
        if (HytalePlatform.isWindows()) {
            return;
        }

        String separator = System.getProperty("path.separator", ":");
        String existingLd = environment.getOrDefault("LD_LIBRARY_PATH", "");
        environment.put("LD_LIBRARY_PATH", clientDirectory + (existingLd.isBlank() ? "" : separator + existingLd));

        if (HytalePlatform.isMac()) {
            String existingDyld = environment.getOrDefault("DYLD_LIBRARY_PATH", "");
            environment.put("DYLD_LIBRARY_PATH", clientDirectory + (existingDyld.isBlank() ? "" : separator + existingDyld));
        }
    }

    private static boolean containsClientExecutable(Path path) {
        return Files.isRegularFile(resolveExecutable(path));
    }

    private static List<Path> gameDirectoryCandidates(Path configuredDirectory, String branch, int build) {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        paths.add(configuredDirectory);
        addVersionFolders(paths, configuredDirectory, build);
        addOfficialGameFolders(paths, configuredDirectory, branch, build);
        dataRootFromOfficialGameDirectory(configuredDirectory).ifPresent(root -> addOfficialGameFolders(paths, root, branch, build));
        return List.copyOf(paths);
    }

    private static void addOfficialGameFolders(LinkedHashSet<Path> paths, Path hytaleDataRoot, String branch, int build) {
        for (String branchName : branchNames(branch)) {
            Path gameRoot = hytaleDataRoot.resolve(Path.of("install", branchName, "package", "game"));
            addVersionFolders(paths, gameRoot, build);
            paths.add(gameRoot);
        }
    }

    private static void addVersionFolders(LinkedHashSet<Path> paths, Path root, int build) {
        if (build > 0) {
            paths.add(root.resolve(Integer.toString(build)));
        }
        paths.add(root.resolve("latest"));
    }

    private static List<Path> bundledJavaCandidates(Path gameDirectory, Path userDataDirectory, String branch) {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        dataRootFromOfficialGameDirectory(gameDirectory).ifPresent(root -> addOfficialJavaFolders(paths, root, branch));
        dataRootFromUserDataDirectory(userDataDirectory).ifPresent(root -> addOfficialJavaFolders(paths, root, branch));
        return List.copyOf(paths);
    }

    private static void addOfficialJavaFolders(LinkedHashSet<Path> paths, Path hytaleDataRoot, String branch) {
        String executable = HytalePlatform.isWindows() ? "java.exe" : "java";
        for (String branchName : branchNames(branch)) {
            paths.add(hytaleDataRoot.resolve(Path.of("install", branchName, "package", "jre", "latest", "bin", executable)));
        }
        paths.add(hytaleDataRoot.resolve(Path.of("jre", "latest", "bin", executable)));
    }

    private static List<String> branchNames(String branch) {
        String normalized = HytaleApiClient.normalizeBranch(branch);
        if ("pre-release".equals(normalized)) {
            return List.of("pre-release", "prerelease", "release");
        }
        if (!"release".equals(normalized)) {
            return List.of(normalized);
        }
        return List.of("release");
    }

    private static Optional<Path> dataRootFromUserDataDirectory(Path userDataDirectory) {
        if (userDataDirectory == null || userDataDirectory.getFileName() == null) {
            return Optional.empty();
        }
        String name = userDataDirectory.getFileName().toString();
        if ("UserData".equalsIgnoreCase(name) || "userdata".equalsIgnoreCase(name)) {
            return Optional.ofNullable(userDataDirectory.getParent());
        }
        return Optional.empty();
    }

    private static Optional<Path> dataRootFromOfficialGameDirectory(Path gameDirectory) {
        if (gameDirectory == null) {
            return Optional.empty();
        }
        Path candidate = gameDirectory.toAbsolutePath().normalize();
        if (candidate.getFileName() != null && !"game".equals(candidate.getFileName().toString())) {
            candidate = candidate.getParent();
        }
        if (candidate == null || candidate.getFileName() == null || !"game".equals(candidate.getFileName().toString())) {
            return Optional.empty();
        }
        Path packageDirectory = candidate.getParent();
        if (packageDirectory == null || packageDirectory.getFileName() == null
                || !"package".equals(packageDirectory.getFileName().toString())) {
            return Optional.empty();
        }
        Path branchDirectory = packageDirectory.getParent();
        if (branchDirectory == null) {
            return Optional.empty();
        }
        Path installDirectory = branchDirectory.getParent();
        if (installDirectory == null || installDirectory.getFileName() == null
                || !"install".equals(installDirectory.getFileName().toString())) {
            return Optional.empty();
        }
        return Optional.ofNullable(installDirectory.getParent());
    }

    public record LaunchPaths(
            Path gameDirectory,
            Path userDataDirectory,
            Path javaExecutable,
            Path executable,
            Path workingDirectory
    ) {
    }
}
