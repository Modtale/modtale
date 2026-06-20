package net.modtale.launcher.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.modtale.launcher.api.ModtaleApiException;

public class SettingsStore {

    private final Path settingsPath;
    private final ObjectMapper mapper;
    private final InstalledProjectRegistry installedProjectRegistry;

    public SettingsStore() {
        this(defaultSettingsPath());
    }

    public SettingsStore(Path settingsPath) {
        this.settingsPath = settingsPath;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.installedProjectRegistry = new InstalledProjectRegistry(settingsPath.getParent(), mapper);
    }

    public LauncherSettings load() {
        if (!Files.exists(settingsPath)) {
            return saveDefaultSettings();
        }
        try {
            if (isBlankSettingsFile()) {
                return saveDefaultSettings();
            }
            LauncherSettings settings = mapper.readValue(settingsPath.toFile(), LauncherSettings.class);
            normalize(settings);
            settings.setInstalledProjects(installedProjectRegistry.merge(settings.getInstalledProjects()));
            return settings;
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not load launcher settings from " + settingsPath, ex);
        }
    }

    private LauncherSettings saveDefaultSettings() {
        LauncherSettings settings = new LauncherSettings();
        save(settings);
        return settings;
    }

    private boolean isBlankSettingsFile() throws IOException {
        return Files.size(settingsPath) == 0 || Files.readString(settingsPath).isBlank();
    }

    public void save(LauncherSettings settings) {
        normalize(settings);
        settings.setInstalledProjects(installedProjectRegistry.merge(settings.getInstalledProjects()));
        try {
            Files.createDirectories(settingsPath.getParent());
            mapper.writeValue(settingsPath.toFile(), settings);
            installedProjectRegistry.save(settings.getInstalledProjects());
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not save launcher settings to " + settingsPath, ex);
        }
    }

    public void removeInstalledProject(String projectId) {
        installedProjectRegistry.remove(projectId);
    }

    public Path settingsPath() {
        return settingsPath;
    }

    public static Path defaultSettingsPath() {
        return defaultLauncherDirectory().resolve("settings.json");
    }

    public static Path defaultSessionPath() {
        return defaultLauncherDirectory().resolve("session-cookies.json");
    }

    private static Path defaultLauncherDirectory() {
        return Path.of(System.getProperty("user.home", "."), ".modtale", "launcher");
    }

    private static void normalize(LauncherSettings settings) {
        if (settings.getHytaleModsPath() == null || settings.getHytaleModsPath().isBlank()) {
            settings.setHytaleModsPath(HytalePathDetector.defaultModsDirectory().toString());
        }
        if (settings.getHytaleGamePath() == null || settings.getHytaleGamePath().isBlank()) {
            settings.setHytaleGamePath(HytalePathDetector.defaultGameDirectory().toString());
        } else if (!HytalePathDetector.containsClientExecutable(settings.hytaleGameDirectory())) {
            HytalePathDetector.detectExistingGameDirectory()
                    .ifPresent(path -> settings.setHytaleGamePath(path.toString()));
        }
        if (settings.getHytaleUserDataPath() == null || settings.getHytaleUserDataPath().isBlank()) {
            settings.setHytaleUserDataPath(HytalePathDetector.defaultUserDataDirectory().toString());
        }
        if (settings.getHytaleJavaPath() == null || settings.getHytaleJavaPath().isBlank()) {
            settings.setHytaleJavaPath(HytalePathDetector.defaultJavaExecutable().toString());
        } else if (HytalePathDetector.isCurrentJavaExecutable(settings.hytaleJavaExecutable())) {
            HytalePathDetector.detectExistingJavaExecutable()
                    .filter(path -> !HytalePathDetector.isCurrentJavaExecutable(path))
                    .ifPresent(path -> settings.setHytaleJavaPath(path.toString()));
        }
        if (settings.getHytaleBranch() == null || settings.getHytaleBranch().isBlank()) {
            settings.setHytaleBranch("release");
        }
        if (settings.getInstalledProjects() == null) {
            settings.setInstalledProjects(java.util.List.of());
        }
        if (settings.getHytalePatchlineCaches() == null) {
            settings.setHytalePatchlineCaches(java.util.List.of());
        }
        if (settings.getHytaleVersionCaches() == null) {
            settings.setHytaleVersionCaches(java.util.List.of());
        }
        settings.normalizeHytaleAuthSessions();
    }
}
