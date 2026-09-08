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

export const isStableLauncherRelease = (release: GitHubRelease): boolean =>
    !release.draft && !release.prerelease
    && (release.tag_name?.startsWith('launcher-stable-v') === true
        || release.tag_name?.startsWith('launcher-v') === true);

export async function fetchStableLauncherRelease(signal: AbortSignal): Promise<GitHubRelease | null> {
    for (let page = 1; ; page++) {
        const response = await fetch(`https://api.github.com/repos/Modtale/modtale/releases?per_page=100&page=${page}`, {
            signal,
            headers: { Accept: 'application/vnd.github+json' },
        });
        if (!response.ok) throw new Error('GitHub release lookup failed');
        const releases: GitHubRelease[] = await response.json();
        const stable = releases.find(isStableLauncherRelease);
        if (stable) return stable;
        if (releases.length < 100) return null;
    }
}
