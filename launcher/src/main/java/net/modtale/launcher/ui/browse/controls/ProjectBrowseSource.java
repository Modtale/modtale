package net.modtale.launcher.ui.browse.controls;

public enum ProjectBrowseSource {
    MODTALE("Modtale"),
    CURSEFORGE("CurseForge");

    private final String label;

    ProjectBrowseSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
