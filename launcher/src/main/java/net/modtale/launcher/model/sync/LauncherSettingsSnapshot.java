package net.modtale.launcher.model.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import net.modtale.launcher.settings.LauncherSettings;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LauncherSettingsSnapshot {

    private int schemaVersion = 1;
    private String settingsHash = "";
    private String updatedAt = "";
    private Preferences preferences = new Preferences();
    private List<InstalledProjectSnapshot> installedProjects = new ArrayList<>();

    public static LauncherSettingsSnapshot fromSettings(LauncherSettings settings) {
        LauncherSettingsSnapshot snapshot = new LauncherSettingsSnapshot();
        snapshot.setPreferences(Preferences.fromSettings(settings));
        snapshot.setInstalledProjects(settings == null ? List.of() : settings.getInstalledProjects().stream()
                .filter(LauncherSettingsSnapshot::isSyncedProject)
                .map(InstalledProjectSnapshot::fromInstalledProject)
                .toList());
        snapshot.refreshHash();
        return snapshot;
    }

    public void applyPreferencesTo(LauncherSettings settings) {
        if (settings == null) {
            return;
        }
        Preferences source = preferences == null ? new Preferences() : preferences;
        settings.setHytaleModsPath(source.hytaleModsPath);
        settings.setHytaleGamePath(source.hytaleGamePath);
        settings.setHytaleUserDataPath(source.hytaleUserDataPath);
        settings.setHytaleJavaPath(source.hytaleJavaPath);
        settings.setHytaleBranch(source.hytaleBranch);
        settings.setHytaleBuild(source.hytaleBuild);
        settings.setGameVersion(source.gameVersion);
        settings.setIncludeDependencies(source.includeDependencies);
        settings.setIncludeOptionalDependencies(source.includeOptionalDependencies);
        settings.setAutoCheckUpdates(source.autoCheckUpdates);
        settings.setLauncherAutoUpdates(source.launcherAutoUpdates);
    }

    public boolean hasSyncedContent() {
        return !installedProjects().isEmpty()
                || (settingsHash != null && !settingsHash.isBlank())
                || (updatedAt != null && !updatedAt.isBlank());
    }

    public String effectiveHash() {
        String hash = settingsHash == null ? "" : settingsHash.trim();
        if (!hash.isBlank()) {
            return hash;
        }
        return computeHash();
    }

    public String computeHash() {
        return hashPayload(canonicalPayload());
    }

    public String installedProjectsHash() {
        return hashPayload(canonicalInstalledProjectsPayload());
    }

    public LauncherSettingsSnapshot preferencesOnly() {
        LauncherSettingsSnapshot copy = new LauncherSettingsSnapshot();
        copy.setSchemaVersion(schemaVersion);
        copy.setSettingsHash(computeHash());
        copy.setUpdatedAt(updatedAt);
        copy.setPreferences(preferences);
        copy.setInstalledProjects(List.of());
        return copy;
    }

    private static String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    public void refreshHash() {
        settingsHash = computeHash();
    }

    private String canonicalPayload() {
        Preferences prefs = preferences == null ? new Preferences() : preferences;
        StringJoiner payload = new StringJoiner("\n");
        payload.add("schema=" + schemaVersion);
        payload.add("modsPath=" + value(prefs.hytaleModsPath));
        payload.add("gamePath=" + value(prefs.hytaleGamePath));
        payload.add("userDataPath=" + value(prefs.hytaleUserDataPath));
        payload.add("javaPath=" + value(prefs.hytaleJavaPath));
        payload.add("branch=" + value(prefs.hytaleBranch));
        payload.add("build=" + Math.max(0, prefs.hytaleBuild));
        payload.add("gameVersion=" + value(prefs.gameVersion));
        payload.add("includeDependencies=" + prefs.includeDependencies);
        payload.add("includeOptionalDependencies=" + prefs.includeOptionalDependencies);
        payload.add("autoCheckUpdates=" + prefs.autoCheckUpdates);
        payload.add("launcherAutoUpdates=" + prefs.launcherAutoUpdates);
        installedProjects().stream()
                .filter(project -> !value(project.projectId).isBlank())
                .sorted(Comparator
                        .comparing((InstalledProjectSnapshot project) -> value(project.projectId))
                        .thenComparing(project -> value(project.installedVersionId))
                        .thenComparing(project -> value(project.installedVersion)))
                .forEach(project -> payload.add(project.canonicalPayload()));
        return payload.toString();
    }

    private String canonicalInstalledProjectsPayload() {
        StringJoiner payload = new StringJoiner("\n");
        installedProjects().stream()
                .filter(project -> !value(project.projectId).isBlank())
                .sorted(Comparator
                        .comparing((InstalledProjectSnapshot project) -> value(project.projectId))
                        .thenComparing(project -> value(project.installedVersionId))
                        .thenComparing(project -> value(project.installedVersion)))
                .forEach(project -> payload.add(project.canonicalPayload()));
        return payload.toString();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = Math.max(1, schemaVersion);
    }

    public String getSettingsHash() {
        return settingsHash;
    }

    public void setSettingsHash(String settingsHash) {
        this.settingsHash = settingsHash == null ? "" : settingsHash.trim();
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt == null ? "" : updatedAt.trim();
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences == null ? new Preferences() : preferences;
    }

    public List<InstalledProjectSnapshot> getInstalledProjects() {
        return installedProjects;
    }

    public void setInstalledProjects(List<InstalledProjectSnapshot> installedProjects) {
        this.installedProjects = installedProjects == null ? new ArrayList<>() : new ArrayList<>(installedProjects);
    }

    public List<InstalledProjectSnapshot> installedProjects() {
        return installedProjects == null ? List.of() : List.copyOf(installedProjects);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isSyncedProject(InstalledProject project) {
        if (project == null) {
            return false;
        }
        String source = value(project.source());
        return source.isBlank() || InstalledProject.SOURCE_MODTALE.equalsIgnoreCase(source);
    }

    private static String listValue(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Preferences {
        private String hytaleModsPath = "";
        private String hytaleGamePath = "";
        private String hytaleUserDataPath = "";
        private String hytaleJavaPath = "";
        private String hytaleBranch = "release";
        private int hytaleBuild;
        private String gameVersion = "";
        private boolean includeDependencies = true;
        private boolean includeOptionalDependencies;
        private boolean autoCheckUpdates = true;
        private boolean launcherAutoUpdates;

        public static Preferences fromSettings(LauncherSettings settings) {
            Preferences preferences = new Preferences();
            if (settings == null) {
                return preferences;
            }
            preferences.setHytaleModsPath(settings.getHytaleModsPath());
            preferences.setHytaleGamePath(settings.getHytaleGamePath());
            preferences.setHytaleUserDataPath(settings.getHytaleUserDataPath());
            preferences.setHytaleJavaPath(settings.getHytaleJavaPath());
            preferences.setHytaleBranch(settings.getHytaleBranch());
            preferences.setHytaleBuild(settings.getHytaleBuild());
            preferences.setGameVersion(settings.getGameVersion());
            preferences.setIncludeDependencies(settings.isIncludeDependencies());
            preferences.setIncludeOptionalDependencies(settings.isIncludeOptionalDependencies());
            preferences.setAutoCheckUpdates(settings.isAutoCheckUpdates());
            preferences.setLauncherAutoUpdates(settings.isLauncherAutoUpdates());
            return preferences;
        }

        public String getHytaleModsPath() { return hytaleModsPath; }
        public void setHytaleModsPath(String hytaleModsPath) { this.hytaleModsPath = value(hytaleModsPath); }
        public String getHytaleGamePath() { return hytaleGamePath; }
        public void setHytaleGamePath(String hytaleGamePath) { this.hytaleGamePath = value(hytaleGamePath); }
        public String getHytaleUserDataPath() { return hytaleUserDataPath; }
        public void setHytaleUserDataPath(String hytaleUserDataPath) { this.hytaleUserDataPath = value(hytaleUserDataPath); }
        public String getHytaleJavaPath() { return hytaleJavaPath; }
        public void setHytaleJavaPath(String hytaleJavaPath) { this.hytaleJavaPath = value(hytaleJavaPath); }
        public String getHytaleBranch() { return hytaleBranch; }
        public void setHytaleBranch(String hytaleBranch) { this.hytaleBranch = value(hytaleBranch); }
        public int getHytaleBuild() { return hytaleBuild; }
        public void setHytaleBuild(int hytaleBuild) { this.hytaleBuild = Math.max(0, hytaleBuild); }
        public String getGameVersion() { return gameVersion; }
        public void setGameVersion(String gameVersion) { this.gameVersion = value(gameVersion); }
        public boolean isIncludeDependencies() { return includeDependencies; }
        public void setIncludeDependencies(boolean includeDependencies) { this.includeDependencies = includeDependencies; }
        public boolean isIncludeOptionalDependencies() { return includeOptionalDependencies; }
        public void setIncludeOptionalDependencies(boolean includeOptionalDependencies) { this.includeOptionalDependencies = includeOptionalDependencies; }
        public boolean isAutoCheckUpdates() { return autoCheckUpdates; }
        public void setAutoCheckUpdates(boolean autoCheckUpdates) { this.autoCheckUpdates = autoCheckUpdates; }
        public boolean isLauncherAutoUpdates() { return launcherAutoUpdates; }
        public void setLauncherAutoUpdates(boolean launcherAutoUpdates) { this.launcherAutoUpdates = launcherAutoUpdates; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstalledProjectSnapshot {
        private String projectId = "";
        private String slug = "";
        private String title = "";
        private String classification = "";
        private String installedVersion = "";
        private String installedVersionId = "";
        private String gameVersion = "";
        private String source = InstalledProject.SOURCE_MODTALE;
        private String installType = InstalledProject.INSTALL_DIRECT;
        private boolean modpackUnlocked;
        private List<String> dependencyProjectIds = new ArrayList<>();
        private List<String> externalDependencies = new ArrayList<>();
        private List<InstalledProjectReference> bundledProjects = new ArrayList<>();

        public static InstalledProjectSnapshot fromInstalledProject(InstalledProject project) {
            InstalledProjectSnapshot snapshot = new InstalledProjectSnapshot();
            if (project == null) {
                return snapshot;
            }
            snapshot.setProjectId(project.projectId());
            snapshot.setSlug(project.slug());
            snapshot.setTitle(project.title());
            snapshot.setClassification(project.classification());
            snapshot.setInstalledVersion(project.installedVersion());
            snapshot.setInstalledVersionId(project.installedVersionId());
            snapshot.setGameVersion(project.gameVersion());
            snapshot.setSource(project.source());
            snapshot.setInstallType(project.installType());
            snapshot.setModpackUnlocked(project.modpackUnlocked());
            snapshot.setDependencyProjectIds(project.dependencyProjectIds());
            snapshot.setExternalDependencies(project.externalDependencies());
            snapshot.setBundledProjects(project.bundledProjects());
            return snapshot;
        }

        private String canonicalPayload() {
            return String.join("|",
                    "project=" + value(projectId),
                    "slug=" + value(slug),
                    "title=" + value(title),
                    "classification=" + value(classification),
                    "version=" + value(installedVersion),
                    "versionId=" + value(installedVersionId),
                    "gameVersion=" + value(gameVersion),
                    "source=" + value(source),
                    "installType=" + value(installType),
                    "unlocked=" + modpackUnlocked,
                    "deps=" + listValue(dependencyProjectIds),
                    "external=" + listValue(externalDependencies),
                    "bundled=" + bundledValue(bundledProjects));
        }

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = value(projectId); }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = value(slug); }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = value(title); }
        public String getClassification() { return classification; }
        public void setClassification(String classification) { this.classification = value(classification); }
        public String getInstalledVersion() { return installedVersion; }
        public void setInstalledVersion(String installedVersion) { this.installedVersion = value(installedVersion); }
        public String getInstalledVersionId() { return installedVersionId; }
        public void setInstalledVersionId(String installedVersionId) { this.installedVersionId = value(installedVersionId); }
        public String getGameVersion() { return gameVersion; }
        public void setGameVersion(String gameVersion) { this.gameVersion = value(gameVersion); }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = value(source).isBlank() ? InstalledProject.SOURCE_MODTALE : value(source); }
        public String getInstallType() { return installType; }
        public void setInstallType(String installType) { this.installType = value(installType).isBlank() ? InstalledProject.INSTALL_DIRECT : value(installType); }
        public boolean isModpackUnlocked() { return modpackUnlocked; }
        public void setModpackUnlocked(boolean modpackUnlocked) { this.modpackUnlocked = modpackUnlocked; }
        public List<String> getDependencyProjectIds() { return dependencyProjectIds; }
        public void setDependencyProjectIds(List<String> dependencyProjectIds) {
            this.dependencyProjectIds = dependencyProjectIds == null ? new ArrayList<>() : new ArrayList<>(dependencyProjectIds);
        }
        public List<String> getExternalDependencies() { return externalDependencies; }
        public void setExternalDependencies(List<String> externalDependencies) {
            this.externalDependencies = externalDependencies == null ? new ArrayList<>() : new ArrayList<>(externalDependencies);
        }
        public List<InstalledProjectReference> getBundledProjects() { return bundledProjects; }
        public void setBundledProjects(List<InstalledProjectReference> bundledProjects) {
            this.bundledProjects = bundledProjects == null ? new ArrayList<>() : new ArrayList<>(bundledProjects);
        }
    }

    private static String bundledValue(List<InstalledProjectReference> projects) {
        if (projects == null || projects.isEmpty()) {
            return "";
        }
        return projects.stream()
                .map(project -> String.join(":",
                        value(project.projectId()),
                        value(project.slug()),
                        value(project.versionNumber()),
                        value(project.source()),
                        value(project.externalId())))
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}
