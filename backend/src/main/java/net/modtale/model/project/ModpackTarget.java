package net.modtale.model.project;

public enum ModpackTarget {
    UNIVERSAL,
    CLIENT,
    SERVER;

    public boolean includes(ProjectDependency.Environment environment) {
        ProjectDependency.Environment effective = environment == null
                ? ProjectDependency.Environment.COMMON
                : environment;
        return this == UNIVERSAL
                || this == CLIENT && effective != ProjectDependency.Environment.SERVER
                || this == SERVER && effective != ProjectDependency.Environment.CLIENT;
    }
}
