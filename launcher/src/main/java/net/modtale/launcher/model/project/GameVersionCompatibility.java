package net.modtale.launcher.model.project;

/** Matches exact game versions and numeric catalog families such as CurseForge's 0.5. */
public final class GameVersionCompatibility {
    private GameVersionCompatibility() { }

    public static boolean matches(String declared, String installed) {
        if (declared == null || installed == null) return false;
        if (declared.equals(installed)) return true;
        return declared.matches("[0-9]+\\.[0-9]+")
                && installed.startsWith(declared + ".")
                && isNumericVersion(installed);
    }

    public static boolean isNumericVersion(String version) {
        return version != null && version.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?)?");
    }
}
