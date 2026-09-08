export type GitHubReleaseAsset = {
    name: string;
    browser_download_url: string;
};

export type GitHubRelease = {
    tag_name?: string;
    name?: string;
    html_url?: string;
    draft?: boolean;
    prerelease?: boolean;
    assets?: GitHubReleaseAsset[];
};

export type LauncherReleaseChannel = 'stable' | 'develop';

export const launcherChannelForHostname = (hostname: string): LauncherReleaseChannel =>
    hostname.toLowerCase() === 'dev.modtale.net' ? 'develop' : 'stable';

export const launcherReleasesUrl = (channel: LauncherReleaseChannel): string => channel === 'develop'
    ? 'https://github.com/Modtale/modtale/releases?q=launcher-develop-v'
    : 'https://github.com/Modtale/modtale/releases/latest';

export const isStableLauncherRelease = (release: GitHubRelease): boolean =>
    !release.draft && !release.prerelease
    && (release.tag_name?.startsWith('launcher-stable-v') === true
        || release.tag_name?.startsWith('launcher-v') === true);

export const isLauncherReleaseForChannel = (release: GitHubRelease, channel: LauncherReleaseChannel): boolean =>
    channel === 'develop'
        ? !release.draft && release.prerelease === true && release.tag_name?.startsWith('launcher-develop-v') === true
        : isStableLauncherRelease(release);

export async function fetchLauncherRelease(
    signal: AbortSignal,
    channel: LauncherReleaseChannel = 'stable',
): Promise<GitHubRelease | null> {
    for (let page = 1; ; page++) {
        const response = await fetch(`https://api.github.com/repos/Modtale/modtale/releases?per_page=100&page=${page}`, {
            signal,
            headers: { Accept: 'application/vnd.github+json' },
        });
        if (!response.ok) throw new Error('GitHub release lookup failed');
        const releases: GitHubRelease[] = await response.json();
        const release = releases.find((candidate) => isLauncherReleaseForChannel(candidate, channel));
        if (release) return release;
        if (releases.length < 100) return null;
    }
}
