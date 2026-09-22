package net.modtale.launcher.ui.library;

import net.modtale.launcher.model.project.ProjectVersion;

record LibraryVersionChoice(ProjectVersion version, String label) {
    @Override
    public String toString() {
        return label;
    }
}
