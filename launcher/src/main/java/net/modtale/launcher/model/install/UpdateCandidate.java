package net.modtale.launcher.model.install;

import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;

public record UpdateCandidate(InstalledProject installedProject, ProjectDetail project, ProjectVersion newestVersion) {
    public String title() {
        return installedProject.title();
    }

    public String currentVersion() {
        return installedProject.installedVersion();
    }

    public String newestVersionNumber() {
        return newestVersion == null ? "" : newestVersion.versionNumber();
    }
}
