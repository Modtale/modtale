package net.modtale.launcher.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.modtale.launcher.hytale.HytaleApiClient;
import net.modtale.launcher.hytale.HytaleAuthSession;
import net.modtale.launcher.hytale.HytaleVersion;
import net.modtale.launcher.model.install.InstalledProject;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LauncherSettings {

    private String lastUsername = "";
    private String hytaleModsPath = HytalePathDetector.defaultModsDirectory().toString();
    private String hytaleGamePath = HytalePathDetector.defaultGameDirectory().toString();
    private String hytaleUserDataPath = HytalePathDetector.defaultUserDataDirectory().toString();
    private String hytaleJavaPath = HytalePathDetector.defaultJavaExecutable().toString();
    private String hytaleBranch = "release";
    private int hytaleBuild;
    private long hytalePlaytimeSeconds;
    private HytaleAuthSession hytaleAuthSession;
    private List<HytaleAuthSession> hytaleAuthSessions = new ArrayList<>();
    private String activeHytaleAccountId = "";
    private String gameVersion = "";
    private boolean includeDependencies = true;
    private boolean includeOptionalDependencies;
    private boolean autoCheckUpdates = true;
    private boolean launcherAutoUpdates;
    private List<HytalePatchlineCacheEntry> hytalePatchlineCaches = new ArrayList<>();
    private List<HytaleVersionCacheEntry> hytaleVersionCaches = new ArrayList<>();
    private List<InstalledProject> installedProjects = new ArrayList<>();

    public String getLastUsername() {
        return lastUsername;
    }

    public void setLastUsername(String lastUsername) {
        this.lastUsername = lastUsername == null ? "" : lastUsername.trim();
    }

    public String getHytaleModsPath() {
        return hytaleModsPath;
    }

    public void setHytaleModsPath(String hytaleModsPath) {
        this.hytaleModsPath = hytaleModsPath == null ? "" : hytaleModsPath.trim();
    }

    public Path hytaleModsDirectory() {
        return Path.of(hytaleModsPath);
    }

    public String getHytaleGamePath() {
        return hytaleGamePath;
    }

    public void setHytaleGamePath(String hytaleGamePath) {
        this.hytaleGamePath = hytaleGamePath == null ? "" : hytaleGamePath.trim();
    }

    public Path hytaleGameDirectory() {
        return Path.of(hytaleGamePath);
    }

    public String getHytaleUserDataPath() {
        return hytaleUserDataPath;
    }

    public void setHytaleUserDataPath(String hytaleUserDataPath) {
        this.hytaleUserDataPath = hytaleUserDataPath == null ? "" : hytaleUserDataPath.trim();
    }

    public Path hytaleUserDataDirectory() {
        return Path.of(hytaleUserDataPath);
    }

    public String getHytaleJavaPath() {
        return hytaleJavaPath;
    }

    public void setHytaleJavaPath(String hytaleJavaPath) {
        this.hytaleJavaPath = hytaleJavaPath == null || hytaleJavaPath.isBlank()
                ? HytalePathDetector.defaultJavaExecutable().toString()
                : hytaleJavaPath.trim();
    }

    public Path hytaleJavaExecutable() {
        return Path.of(hytaleJavaPath);
    }

    public String getHytaleBranch() {
        return hytaleBranch;
    }

    public void setHytaleBranch(String hytaleBranch) {
        this.hytaleBranch = HytaleApiClient.normalizeBranch(hytaleBranch);
    }

    public int getHytaleBuild() {
        return hytaleBuild;
    }

    public void setHytaleBuild(int hytaleBuild) {
        this.hytaleBuild = Math.max(0, hytaleBuild);
    }

    public long getHytalePlaytimeSeconds() {
        return Math.max(0, hytalePlaytimeSeconds);
    }

    public void setHytalePlaytimeSeconds(long hytalePlaytimeSeconds) {
        this.hytalePlaytimeSeconds = Math.max(0, hytalePlaytimeSeconds);
    }

    public void addHytalePlaytimeSeconds(long seconds) {
        if (seconds <= 0) {
            return;
        }
        this.hytalePlaytimeSeconds = Math.max(0, this.hytalePlaytimeSeconds + seconds);
    }

    public List<HytalePatchlineCacheEntry> getHytalePatchlineCaches() {
        return hytalePatchlineCaches;
    }

    public void setHytalePatchlineCaches(List<HytalePatchlineCacheEntry> hytalePatchlineCaches) {
        this.hytalePatchlineCaches = hytalePatchlineCaches == null ? new ArrayList<>() : new ArrayList<>(hytalePatchlineCaches);
    }

    public List<HytaleVersionCacheEntry> getHytaleVersionCaches() {
        return hytaleVersionCaches;
    }

    public void setHytaleVersionCaches(List<HytaleVersionCacheEntry> hytaleVersionCaches) {
        this.hytaleVersionCaches = hytaleVersionCaches == null ? new ArrayList<>() : new ArrayList<>(hytaleVersionCaches);
    }

    public List<String> cachedHytalePatchlines(String accountId, String platform) {
        String accountKey = cacheValue(accountId);
        String platformKey = cacheValue(platform);
        return hytalePatchlineCaches.stream()
                .filter(entry -> accountKey.equals(cacheValue(entry.getAccountId()))
                        && platformKey.equals(cacheValue(entry.getPlatform())))
                .findFirst()
                .map(HytalePatchlineCacheEntry::getPatchlines)
                .orElse(List.of());
    }

    public List<String> pendingHytalePatchlines(String accountId, String platform) {
        String accountKey = cacheValue(accountId);
        String platformKey = cacheValue(platform);
        return hytalePatchlineCaches.stream()
                .filter(entry -> accountKey.equals(cacheValue(entry.getAccountId()))
                        && platformKey.equals(cacheValue(entry.getPlatform())))
                .findFirst()
                .map(HytalePatchlineCacheEntry::getPendingPatchlines)
                .orElse(List.of());
    }

    public void cacheHytalePatchlines(String accountId, String platform, List<String> patchlines) {
        String accountKey = cacheValue(accountId);
        String platformKey = cacheValue(platform);
        HytalePatchlineCacheEntry entry = hytalePatchlineCacheEntry(accountKey, platformKey);
        entry.setAccountId(accountKey);
        entry.setPlatform(platformKey);
        entry.setFetchedAt(System.currentTimeMillis());
        entry.setPatchlines(patchlines);
    }

    public void cachePendingHytalePatchlines(String accountId, String platform, List<String> pendingPatchlines) {
        String accountKey = cacheValue(accountId);
        String platformKey = cacheValue(platform);
        HytalePatchlineCacheEntry entry = hytalePatchlineCacheEntry(accountKey, platformKey);
        entry.setAccountId(accountKey);
        entry.setPlatform(platformKey);
        entry.setPendingPatchlines(pendingPatchlines);
    }

    public List<HytaleVersion> cachedHytaleVersions(String accountId, String platform, String patchline) {
        String accountKey = cacheValue(accountId);
        String platformKey = cacheValue(platform);
        String patchlineKey = HytaleApiClient.normalizeBranch(patchline);
        return hytaleVersionCaches.stream()
                .filter(entry -> accountKey.equals(cacheValue(entry.getAccountId()))
                        && platformKey.equals(cacheValue(entry.getPlatform()))
                        && patchlineKey.equals(HytaleApiClient.normalizeBranch(entry.getPatchline())))
                .findFirst()
                .map(HytaleVersionCacheEntry::getVersions)
                .orElse(List.of());
    }

    public void cacheHytaleVersions(String accountId, String platform, String patchline, List<HytaleVersion> versions) {
        String accountKey = cacheValue(accountId);
        String platformKey = cacheValue(platform);
        String patchlineKey = HytaleApiClient.normalizeBranch(patchline);
        hytaleVersionCaches.removeIf(entry -> accountKey.equals(cacheValue(entry.getAccountId()))
                && platformKey.equals(cacheValue(entry.getPlatform()))
                && patchlineKey.equals(HytaleApiClient.normalizeBranch(entry.getPatchline())));
        HytaleVersionCacheEntry entry = new HytaleVersionCacheEntry();
        entry.setAccountId(accountKey);
        entry.setPlatform(platformKey);
        entry.setPatchline(patchlineKey);
        entry.setFetchedAt(System.currentTimeMillis());
        entry.setVersions(versions);
        hytaleVersionCaches.add(entry);
    }

    public HytaleAuthSession getHytaleAuthSession() {
        normalizeHytaleAuthSessions();
        if (!activeHytaleAccountId.isBlank()) {
            for (HytaleAuthSession session : hytaleAuthSessions) {
                if (activeHytaleAccountId.equals(hytaleAccountId(session))) {
                    return session;
                }
            }
        }
        if (!hytaleAuthSessions.isEmpty()) {
            HytaleAuthSession session = hytaleAuthSessions.getFirst();
            activeHytaleAccountId = hytaleAccountId(session);
            hytaleAuthSession = session;
            return session;
        }
        return hytaleAuthSession;
    }

    public void setHytaleAuthSession(HytaleAuthSession hytaleAuthSession) {
        this.hytaleAuthSession = hytaleAuthSession;
        if (hytaleAuthSession == null) {
            activeHytaleAccountId = "";
            return;
        }
        upsertHytaleAuthSession(hytaleAuthSession);
    }

    public List<HytaleAuthSession> getHytaleAuthSessions() {
        normalizeHytaleAuthSessions();
        return List.copyOf(hytaleAuthSessions);
    }

    public void setHytaleAuthSessions(List<HytaleAuthSession> hytaleAuthSessions) {
        this.hytaleAuthSessions = hytaleAuthSessions == null ? new ArrayList<>() : new ArrayList<>(hytaleAuthSessions);
        normalizeHytaleAuthSessions();
    }

    public String getActiveHytaleAccountId() {
        normalizeHytaleAuthSessions();
        return activeHytaleAccountId;
    }

    public void setActiveHytaleAccountId(String activeHytaleAccountId) {
        this.activeHytaleAccountId = activeHytaleAccountId == null ? "" : activeHytaleAccountId.trim();
        normalizeHytaleAuthSessions();
    }

    public void upsertHytaleAuthSession(HytaleAuthSession session) {
        if (session == null) {
            return;
        }
        String accountId = hytaleAccountId(session);
        hytaleAuthSessions.removeIf(existing -> hytaleAccountId(existing).equals(accountId));
        hytaleAuthSessions.add(session);
        activeHytaleAccountId = accountId;
        hytaleAuthSession = session;
    }

    public void selectHytaleAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        normalizeHytaleAuthSessions();
        for (HytaleAuthSession session : hytaleAuthSessions) {
            if (accountId.equals(hytaleAccountId(session))) {
                activeHytaleAccountId = accountId;
                hytaleAuthSession = session;
                return;
            }
        }
    }

    public void removeActiveHytaleAuthSession() {
        normalizeHytaleAuthSessions();
        String accountId = activeHytaleAccountId;
        if (accountId.isBlank() && hytaleAuthSession != null) {
            accountId = hytaleAccountId(hytaleAuthSession);
        }
        removeHytaleAuthSession(accountId);
    }

    public void removeHytaleAuthSession(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        normalizeHytaleAuthSessions();
        String selectedAccountId = accountId.trim();
        String previousActiveAccountId = activeHytaleAccountId;
        hytaleAuthSessions.removeIf(existing -> hytaleAccountId(existing).equals(selectedAccountId));
        hytaleAuthSession = null;
        activeHytaleAccountId = "";
        if (!hytaleAuthSessions.isEmpty()) {
            HytaleAuthSession next = hytaleAuthSessions.stream()
                    .filter(session -> hytaleAccountId(session).equals(previousActiveAccountId))
                    .findFirst()
                    .orElse(hytaleAuthSessions.getFirst());
            hytaleAuthSession = next;
            activeHytaleAccountId = hytaleAccountId(next);
        }
    }

    public void normalizeHytaleAuthSessions() {
        if (hytaleAuthSessions == null) {
            hytaleAuthSessions = new ArrayList<>();
        }
        if (hytaleAuthSession != null) {
            String legacyId = hytaleAccountId(hytaleAuthSession);
            boolean known = hytaleAuthSessions.stream()
                    .anyMatch(existing -> hytaleAccountId(existing).equals(legacyId));
            if (!known) {
                hytaleAuthSessions.add(hytaleAuthSession);
            }
        }
        hytaleAuthSessions.removeIf(session -> session == null || hytaleAccountId(session).isBlank());
        if (hytaleAuthSessions.isEmpty()) {
            activeHytaleAccountId = "";
            hytaleAuthSession = null;
            return;
        }
        if (activeHytaleAccountId == null || activeHytaleAccountId.isBlank()
                || hytaleAuthSessions.stream().noneMatch(session -> activeHytaleAccountId.equals(hytaleAccountId(session)))) {
            activeHytaleAccountId = hytaleAccountId(hytaleAuthSessions.getFirst());
        }
        hytaleAuthSession = hytaleAuthSessions.stream()
                .filter(session -> activeHytaleAccountId.equals(hytaleAccountId(session)))
                .findFirst()
                .orElse(hytaleAuthSessions.getFirst());
    }

    public static String hytaleAccountId(HytaleAuthSession session) {
        if (session == null) {
            return "";
        }
        if (session.getAccountOwnerId() != null && !session.getAccountOwnerId().isBlank()) {
            return session.getAccountOwnerId().trim();
        }
        if (session.getUuid() != null && !session.getUuid().isBlank()) {
            return session.getUuid().trim();
        }
        return session.getUsername() == null ? "" : session.getUsername().trim();
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public void setGameVersion(String gameVersion) {
        this.gameVersion = gameVersion == null ? "" : gameVersion.trim();
    }

    public boolean isIncludeDependencies() {
        return includeDependencies;
    }

    public void setIncludeDependencies(boolean includeDependencies) {
        this.includeDependencies = includeDependencies;
    }

    public boolean isIncludeOptionalDependencies() {
        return includeOptionalDependencies;
    }

    public void setIncludeOptionalDependencies(boolean includeOptionalDependencies) {
        this.includeOptionalDependencies = includeOptionalDependencies;
    }

    public boolean isAutoCheckUpdates() {
        return autoCheckUpdates;
    }

    public void setAutoCheckUpdates(boolean autoCheckUpdates) {
        this.autoCheckUpdates = autoCheckUpdates;
    }

    public boolean isLauncherAutoUpdates() {
        return launcherAutoUpdates;
    }

    public void setLauncherAutoUpdates(boolean launcherAutoUpdates) {
        this.launcherAutoUpdates = launcherAutoUpdates;
    }

    public List<InstalledProject> getInstalledProjects() {
        return installedProjects;
    }

    public void setInstalledProjects(List<InstalledProject> installedProjects) {
        this.installedProjects = installedProjects == null
                ? new ArrayList<>()
                : new ArrayList<>(installedProjects.stream()
                .filter(project -> project != null && project.projectId() != null && !project.projectId().isBlank())
                .toList());
    }

    public void upsertInstalledProject(InstalledProject project) {
        if (project == null || project.projectId() == null || project.projectId().isBlank()) {
            return;
        }
        installedProjects.removeIf(existing -> existing.projectId().equals(project.projectId()));
        installedProjects.add(project);
    }

    public void removeInstalledProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        installedProjects.removeIf(existing -> existing.projectId().equals(projectId.trim()));
    }

    private static String cacheValue(String value) {
        return value == null ? "" : value.trim();
    }

    private HytalePatchlineCacheEntry hytalePatchlineCacheEntry(String accountId, String platform) {
        String accountKey = cacheValue(accountId);
        String platformKey = cacheValue(platform);
        for (HytalePatchlineCacheEntry entry : hytalePatchlineCaches) {
            if (accountKey.equals(cacheValue(entry.getAccountId()))
                    && platformKey.equals(cacheValue(entry.getPlatform()))) {
                return entry;
            }
        }
        HytalePatchlineCacheEntry entry = new HytalePatchlineCacheEntry();
        hytalePatchlineCaches.add(entry);
        return entry;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class HytalePatchlineCacheEntry {
        private String accountId = "";
        private String platform = "";
        private long fetchedAt;
        private List<String> patchlines = new ArrayList<>();
        private List<String> pendingPatchlines = new ArrayList<>();

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = cacheValue(accountId);
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = cacheValue(platform);
        }

        public long getFetchedAt() {
            return fetchedAt;
        }

        public void setFetchedAt(long fetchedAt) {
            this.fetchedAt = Math.max(0, fetchedAt);
        }

        public List<String> getPatchlines() {
            return patchlines;
        }

        public void setPatchlines(List<String> patchlines) {
            this.patchlines = patchlines == null ? new ArrayList<>() : new ArrayList<>(patchlines);
        }

        public List<String> getPendingPatchlines() {
            return pendingPatchlines;
        }

        public void setPendingPatchlines(List<String> pendingPatchlines) {
            this.pendingPatchlines = pendingPatchlines == null ? new ArrayList<>() : new ArrayList<>(pendingPatchlines);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class HytaleVersionCacheEntry {
        private String accountId = "";
        private String platform = "";
        private String patchline = "release";
        private long fetchedAt;
        private List<HytaleVersion> versions = new ArrayList<>();

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = cacheValue(accountId);
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = cacheValue(platform);
        }

        public String getPatchline() {
            return patchline;
        }

        public void setPatchline(String patchline) {
            this.patchline = HytaleApiClient.normalizeBranch(patchline);
        }

        public long getFetchedAt() {
            return fetchedAt;
        }

        public void setFetchedAt(long fetchedAt) {
            this.fetchedAt = Math.max(0, fetchedAt);
        }

        public List<HytaleVersion> getVersions() {
            return versions;
        }

        public void setVersions(List<HytaleVersion> versions) {
            this.versions = versions == null ? new ArrayList<>() : new ArrayList<>(versions);
        }
    }
}
