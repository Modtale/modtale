package net.modtale.launcher.install;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.UpdateCandidate;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.settings.LauncherSettings;

public class UpdateService {

    private final ModtaleApiClient apiClient;

    public UpdateService(ModtaleApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public List<UpdateCandidate> checkForUpdates(LauncherSettings settings) {
        List<UpdateCandidate> updates = new ArrayList<>();
        for (InstalledProject installed : settings.getInstalledProjects()) {
            checkForUpdate(settings, installed).ifPresent(updates::add);
        }
        return updates;
    }

    public Optional<UpdateCandidate> checkForUpdate(LauncherSettings settings, InstalledProject installed) {
        if (installed == null || !isModtaleProject(installed)) {
            return Optional.empty();
        }
        ProjectDetail project = projectWithVersions(routeKey(installed));
        ProjectVersion newest = VersionSelector.latestCompatible(project, effectiveGameVersion(settings, installed)).orElse(null);
        if (newest == null || sameVersion(installed, newest)) {
            return Optional.empty();
        }
        return Optional.of(new UpdateCandidate(installed, project, newest));
    }

    public ProjectDetail projectWithVersions(String routeKey) {
        ProjectDetail project = apiClient.getProject(routeKey);
        if (project.versions().isEmpty()) {
            project = project.withVersions(apiClient.getProjectVersions(project.routeKey()));
        }
        return project;
    }

    private static String routeKey(InstalledProject installed) {
        return installed.slug() != null && !installed.slug().isBlank() ? installed.slug() : installed.projectId();
    }

    private static boolean isModtaleProject(InstalledProject installed) {
        String source = installed.source() == null ? "" : installed.source().trim();
        return source.isBlank() || InstalledProject.SOURCE_MODTALE.equalsIgnoreCase(source);
    }

    private static String effectiveGameVersion(LauncherSettings settings, InstalledProject installed) {
        if (installed.gameVersion() != null && !installed.gameVersion().isBlank()) {
            return installed.gameVersion();
        }
        return settings.getGameVersion();
    }

    public static boolean sameVersion(InstalledProject installed, ProjectVersion version) {
        if (hasText(installed.installedVersionId()) && hasText(version.id())) {
            return installed.installedVersionId().equals(version.id());
        }
        return hasText(installed.installedVersion()) && installed.installedVersion().equals(version.versionNumber());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
