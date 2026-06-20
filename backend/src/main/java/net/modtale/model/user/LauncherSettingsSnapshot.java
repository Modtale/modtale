package net.modtale.model.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LauncherSettingsSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private int schemaVersion = 1;
    private String settingsHash = "";
    private String updatedAt = "";
    private Preferences preferences = new Preferences();
    private List<InstalledProject> installedProjects = new ArrayList<>();

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

    public List<InstalledProject> getInstalledProjects() {
        return installedProjects;
    }

    public void setInstalledProjects(List<InstalledProject> installedProjects) {
        this.installedProjects = installedProjects == null ? new ArrayList<>() : new ArrayList<>(installedProjects);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Preferences implements Serializable {
        private static final long serialVersionUID = 1L;

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

        public String getHytaleModsPath() { return hytaleModsPath; }
        public void setHytaleModsPath(String hytaleModsPath) { this.hytaleModsPath = hytaleModsPath; }
        public String getHytaleGamePath() { return hytaleGamePath; }
        public void setHytaleGamePath(String hytaleGamePath) { this.hytaleGamePath = hytaleGamePath; }
        public String getHytaleUserDataPath() { return hytaleUserDataPath; }
        public void setHytaleUserDataPath(String hytaleUserDataPath) { this.hytaleUserDataPath = hytaleUserDataPath; }
        public String getHytaleJavaPath() { return hytaleJavaPath; }
        public void setHytaleJavaPath(String hytaleJavaPath) { this.hytaleJavaPath = hytaleJavaPath; }
        public String getHytaleBranch() { return hytaleBranch; }
        public void setHytaleBranch(String hytaleBranch) { this.hytaleBranch = hytaleBranch; }
        public int getHytaleBuild() { return hytaleBuild; }
        public void setHytaleBuild(int hytaleBuild) { this.hytaleBuild = Math.max(0, hytaleBuild); }
        public String getGameVersion() { return gameVersion; }
        public void setGameVersion(String gameVersion) { this.gameVersion = gameVersion; }
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
    public static class InstalledProject implements Serializable {
        private static final long serialVersionUID = 1L;

        private String projectId = "";
        private String slug = "";
        private String title = "";
        private String classification = "";
        private String installedVersion = "";
        private String installedVersionId = "";
        private String gameVersion = "";
        private String source = "MODTALE";
        private String installType = "DIRECT";
        private boolean modpackUnlocked;
        private List<String> dependencyProjectIds = new ArrayList<>();
        private List<String> externalDependencies = new ArrayList<>();
        private List<InstalledProjectReference> bundledProjects = new ArrayList<>();

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getClassification() { return classification; }
        public void setClassification(String classification) { this.classification = classification; }
        public String getInstalledVersion() { return installedVersion; }
        public void setInstalledVersion(String installedVersion) { this.installedVersion = installedVersion; }
        public String getInstalledVersionId() { return installedVersionId; }
        public void setInstalledVersionId(String installedVersionId) { this.installedVersionId = installedVersionId; }
        public String getGameVersion() { return gameVersion; }
        public void setGameVersion(String gameVersion) { this.gameVersion = gameVersion; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getInstallType() { return installType; }
        public void setInstallType(String installType) { this.installType = installType; }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstalledProjectReference implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id = "";
        private String projectId = "";
        private String slug = "";
        private String title = "";
        private String classification = "";
        private String versionNumber = "";
        private String dependencyType = "";
        private String source = "";
        private String externalId = "";
        private String externalUrl = "";
        private String externalFileUrl = "";
        private String externalFileName = "";
        private String cachedFileUrl = "";
        private String icon = "";
        private Boolean optional;
        private Boolean embedded;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getClassification() { return classification; }
        public void setClassification(String classification) { this.classification = classification; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getDependencyType() { return dependencyType; }
        public void setDependencyType(String dependencyType) { this.dependencyType = dependencyType; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getExternalId() { return externalId; }
        public void setExternalId(String externalId) { this.externalId = externalId; }
        public String getExternalUrl() { return externalUrl; }
        public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }
        public String getExternalFileUrl() { return externalFileUrl; }
        public void setExternalFileUrl(String externalFileUrl) { this.externalFileUrl = externalFileUrl; }
        public String getExternalFileName() { return externalFileName; }
        public void setExternalFileName(String externalFileName) { this.externalFileName = externalFileName; }
        public String getCachedFileUrl() { return cachedFileUrl; }
        public void setCachedFileUrl(String cachedFileUrl) { this.cachedFileUrl = cachedFileUrl; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public Boolean getOptional() { return optional; }
        public void setOptional(Boolean optional) { this.optional = optional; }
        public Boolean getEmbedded() { return embedded; }
        public void setEmbedded(Boolean embedded) { this.embedded = embedded; }
    }
}
