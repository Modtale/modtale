package net.modtale.launcher.update;

public record LauncherUpdateCandidate(
        String version,
        String tagName,
        String releaseName,
        String releaseUrl,
        String assetName,
        String assetDownloadUrl,
        boolean prerelease
) {

    public boolean hasInstallerAsset() {
        return assetName != null && !assetName.isBlank()
                && assetDownloadUrl != null && !assetDownloadUrl.isBlank();
    }

    public String displayVersion() {
        return version == null || version.isBlank() ? tagName : version;
    }
}
