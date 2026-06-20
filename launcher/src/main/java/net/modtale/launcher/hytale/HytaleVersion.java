package net.modtale.launcher.hytale;

public record HytaleVersion(
        String branch,
        int build,
        int fromBuild,
        boolean latest,
        String gameVersion,
        String pwrUrl,
        String pwrHeadUrl,
        String sigUrl
) {

    public HytaleVersion(
            String branch,
            int build,
            int fromBuild,
            boolean latest,
            String pwrUrl,
            String pwrHeadUrl,
            String sigUrl
    ) {
        this(branch, build, fromBuild, latest, "", pwrUrl, pwrHeadUrl, sigUrl);
    }

    public HytaleVersion withGameVersion(String gameVersion) {
        return new HytaleVersion(branch, build, fromBuild, latest, gameVersion, pwrUrl, pwrHeadUrl, sigUrl);
    }

    public String displayVersion() {
        if (gameVersion != null && !gameVersion.isBlank()) {
            return gameVersion;
        }
        return "Build " + build;
    }

    @Override
    public String toString() {
        String suffix = latest ? " (latest)" : "";
        if (gameVersion != null && !gameVersion.isBlank()) {
            return gameVersion + " - Build " + build + suffix;
        }
        return displayVersion() + suffix;
    }
}
