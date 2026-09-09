import React, { useEffect, useRef, useState } from 'react';
import { Helmet } from 'react-helmet-async';
import { Download, ExternalLink } from 'lucide-react';
import { GitHubBrandIcon } from '@/components/ui/icons/BrandIcons';
import { ROUTE_SEO } from '@/data/seo-constants';
import { SiteRoutes } from '@/utils/routes';
import { LauncherDemo, type LauncherDemoClip } from '../components/LauncherDemo';
import '../styles/launcher-product.css';
import { fetchLauncherRelease, launcherChannelForHostname, launcherReleasesUrl, type GitHubRelease, type GitHubReleaseAsset } from '../utils/launcherReleases';

type LauncherPlatform = 'windows' | 'mac' | 'linux' | 'unknown';

type LauncherAsset = {
    name: string;
    url: string;
};

type ReleaseState = {
    isLoading: boolean;
    releaseName: string;
    releaseUrl: string;
    assetsByPlatform: Partial<Record<Exclude<LauncherPlatform, 'unknown'>, LauncherAsset>>;
};

const GITHUB_RELEASES_URL = 'https://github.com/Modtale/modtale/releases';

const platformOptions: Array<{
    id: Exclude<LauncherPlatform, 'unknown'>;
    shortLabel: string;
}> = [
    {
        id: 'windows',
        shortLabel: 'Windows',
    },
    {
        id: 'mac',
        shortLabel: 'macOS',
    },
    {
        id: 'linux',
        shortLabel: 'Linux',
    },
];

const detectPlatform = (): LauncherPlatform => {
    if (typeof navigator === 'undefined') return 'unknown';

    const userAgentDataPlatform = (navigator as Navigator & { userAgentData?: { platform?: string } }).userAgentData?.platform || '';
    const legacyPlatform = (navigator as any).platform || '';
    const platformText = `${userAgentDataPlatform} ${legacyPlatform} ${navigator.userAgent || ''}`.toLowerCase();

    if (platformText.includes('win')) return 'windows';
    if (platformText.includes('mac') || platformText.includes('darwin')) return 'mac';
    if (platformText.includes('linux') || platformText.includes('x11')) return 'linux';
    return 'unknown';
};

const isCompatibleAsset = (assetName: string, platform: Exclude<LauncherPlatform, 'unknown'>) => {
    const name = assetName.toLowerCase();

    if (platform === 'windows') return name.endsWith('.exe') || name.endsWith('.msi');
    if (platform === 'mac') return name.endsWith('.dmg') || name.endsWith('.pkg');
    return name.endsWith('.appimage');
};

const assetScore = (assetName: string, platform: Exclude<LauncherPlatform, 'unknown'>) => {
    const name = assetName.toLowerCase();
    if (platform === 'windows') return name.endsWith('.exe') ? 20 : 10;
    if (platform === 'mac') return name.endsWith('.dmg') ? 20 : 10;
    return name.endsWith('.appimage') ? 20 : 0;
};

const selectAssetForPlatform = (assets: GitHubReleaseAsset[] | undefined, platform: Exclude<LauncherPlatform, 'unknown'>): LauncherAsset | undefined => {
    const matchingAsset = (assets || [])
        .filter((asset) => isCompatibleAsset(asset.name, platform))
        .sort((left, right) => assetScore(right.name, platform) - assetScore(left.name, platform))[0];

    if (!matchingAsset) return undefined;
    return {
        name: matchingAsset.name,
        url: matchingAsset.browser_download_url,
    };
};

const releaseDisplayName = (release: GitHubRelease | null) => release?.name || release?.tag_name || 'Latest release';

const primaryActionClass = 'inline-flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-blue-600 px-7 text-base font-bold text-white shadow-[0_8px_32px_rgba(37,99,235,0.25),inset_0_1px_0_rgba(255,255,255,0.2)] ring-1 ring-blue-500 transition-all hover:-translate-y-0.5 hover:bg-blue-500 hover:shadow-[0_16px_48px_rgba(37,99,235,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] sm:w-auto transform-gpu whitespace-nowrap';
const disabledActionClass = 'inline-flex h-14 w-full cursor-not-allowed items-center justify-center gap-3 rounded-2xl bg-slate-200 px-7 text-base font-bold text-slate-500 shadow-sm ring-1 ring-slate-300 sm:w-auto whitespace-nowrap dark:bg-slate-800 dark:text-slate-400 dark:ring-white/10';


const LauncherDownloadButton = ({
    asset,
    label,
    isLoading,
}: {
    asset?: LauncherAsset;
    label: string;
    isLoading: boolean;
}) => {
    if (!asset) {
        return (
            <button type="button" className={disabledActionClass} disabled>
                <Download className="h-5 w-5" aria-hidden="true" />
                {isLoading ? 'Finding download...' : 'Download unavailable'}
            </button>
        );
    }

    return (
        <a
            href={asset.url}
            download={asset.name}
            className={primaryActionClass}
            aria-label={`Download ${asset.name}`}
        >
            <Download className="h-5 w-5" aria-hidden="true" />
            {label}
        </a>
    );
};

const demos: Array<{ clip: LauncherDemoClip; title: string; accent: string; description: string; alt: string }> = [
    {
        clip: 'browse-projects',
        title: 'Find your next',
        accent: 'favorite.',
        description: 'Discover mods on Modtale and CurseForge, then install them right where you play.',
        alt: 'Downloading LevelingCore, choosing a world, and finding it enabled in the library',
    },
    {
        clip: 'world-library',
        title: 'Every world.',
        accent: 'Your rules.',
        description: 'One library, with a different mix of mods for every adventure.',
        alt: 'Switching between Hytale worlds and choosing their enabled mods',
    },
    {
        clip: 'curseforge-mods',
        title: 'More of the mods',
        accent: 'you love.',
        description: 'Enable CurseForge, find a mod, and bring it into your library alongside your Modtale favorites.',
        alt: 'Enabling CurseForge, switching providers, and downloading BetterMap into the library',
    },
    {
        clip: 'mod-configs',
        title: 'Make it play',
        accent: 'your way.',
        description: 'Fine-tune your mods with a built-in config editor, without digging through files.',
        alt: 'Opening a mod’s configuration in the launcher and editing its settings',
    },
    {
        clip: 'account-sync',
        title: 'Your setup.',
        accent: 'Ready to follow.',
        description: 'Sync your library and preferences so your next session feels like home.',
        alt: 'Syncing a saved setup into an empty launcher library',
    },
    {
        clip: 'wardrobe',
        title: 'A little more',
        accent: 'you.',
        description: 'Pick a look, make it your own, and save it for your next session.',
        alt: 'Customizing a popular skin and saving the finished outfit in the wardrobe',
    },
];

export const LauncherPage: React.FC = () => {
    const seo = ROUTE_SEO[SiteRoutes.launcher()];
    const heroScrollRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const shell = heroScrollRef.current;
        if (!shell) return;
        let frame = 0;
        const update = () => {
            frame = 0;
            const hero = shell.querySelector<HTMLElement>('.launcher-product-hero');
            if (!hero) return;
            const cover = hero.querySelector<HTMLElement>('.launcher-product-cover');
            if (!cover) return;
            const distance = Math.max(0, cover.offsetTop + cover.offsetHeight - hero.clientHeight + 24);
            shell.style.setProperty('--hero-travel', `${distance}px`);
            const offset = Math.max(0, Math.min(distance, 96 - shell.getBoundingClientRect().top));
            shell.style.setProperty('--hero-scroll', `${offset}px`);
            shell.style.setProperty('--hero-copy-opacity', `${Math.max(0, 1 - offset / 160)}`);
        };
        const schedule = () => { if (!frame) frame = requestAnimationFrame(update); };
        update();
        window.addEventListener('scroll', schedule, { passive: true });
        window.addEventListener('resize', schedule);
        return () => {
            cancelAnimationFrame(frame);
            window.removeEventListener('scroll', schedule);
            window.removeEventListener('resize', schedule);
        };
    }, []);

    const [detectedPlatform, setDetectedPlatform] = useState<LauncherPlatform>('unknown');
    const [releaseState, setReleaseState] = useState<ReleaseState>({
        isLoading: true,
        releaseName: 'Latest release',
        releaseUrl: GITHUB_RELEASES_URL,
        assetsByPlatform: {},
    });

    useEffect(() => {
        setDetectedPlatform(detectPlatform());

        const controller = new AbortController();
        let isCancelled = false;

        const channel = launcherChannelForHostname(window.location.hostname);
        const fallbackReleaseUrl = launcherReleasesUrl(channel);
        setReleaseState((current) => ({ ...current, releaseUrl: fallbackReleaseUrl }));

        fetchLauncherRelease(controller.signal, channel)
            .then((launcherRelease) => {
                if (isCancelled) return;

                const assetsByPlatform = platformOptions.reduce<ReleaseState['assetsByPlatform']>((acc, platform) => {
                    const asset = selectAssetForPlatform(launcherRelease?.assets, platform.id);
                    if (asset) acc[platform.id] = asset;
                    return acc;
                }, {});

                setReleaseState({
                    isLoading: false,
                    releaseName: releaseDisplayName(launcherRelease),
                    releaseUrl: launcherRelease?.html_url || fallbackReleaseUrl,
                    assetsByPlatform,
                });
            })
            .catch(() => {
                if (!isCancelled) {
                    setReleaseState((current) => ({ ...current, isLoading: false }));
                }
            });

        return () => { isCancelled = true; controller.abort(); };
    }, []);
    const primaryPlatform = detectedPlatform === 'unknown' ? 'windows' : detectedPlatform;
    const primaryPlatformOption = platformOptions.find(platform => platform.id === primaryPlatform) || platformOptions[0];
    const primaryAsset = releaseState.assetsByPlatform[primaryPlatformOption.id];
    const primaryDownloadLabel = `Download for ${primaryPlatformOption.shortLabel}`;
    const releaseLabel = releaseState.isLoading ? 'Checking latest release...' : releaseState.releaseName;
    return (
        <main className="launcher-product">
            <Helmet>
                <title>{seo.title}</title>
                <meta name="description" content={seo.description} />
                <meta name="keywords" content={seo.keywords} />
            </Helmet>
            <div className="launcher-hero-scroll" ref={heroScrollRef}>
            <header className="launcher-product-hero">
                <div className="launcher-hero-copy">
                    <div className="launcher-product-brand">
                        <img className="dark:hidden" src="/assets/logo.svg" width="853" height="128" alt="Modtale" />
                        <img className="hidden dark:block" src="/assets/logo_light.svg" width="853" height="128" alt="Modtale" />
                    </div>
                    <h1>Launcher</h1>
                    <div className="launcher-download" id="download">
                        <LauncherDownloadButton asset={primaryAsset} label={primaryDownloadLabel} isLoading={releaseState.isLoading} />
                        <div className="launcher-platforms" role="group" aria-label="Choose operating system">
                            {platformOptions.map(platform => (
                                <button key={platform.id} type="button" aria-pressed={primaryPlatform === platform.id} onClick={() => setDetectedPlatform(platform.id)}>
                                    {platform.shortLabel}
                                </button>
                            ))}
                        </div>
                        <a className="launcher-release-link" href={releaseState.releaseUrl || GITHUB_RELEASES_URL} target="_blank" rel="noreferrer">
                            {releaseLabel} <ExternalLink size={12} aria-hidden="true" />
                        </a>
                    </div>
                </div>
                <figure className="launcher-product-cover">
                    <img src="/assets/news/launcher-play.jpg" alt="The Modtale Launcher Play tab with community projects, friends, and Hytale news" width="2560" height="1600" fetchPriority="high" />
                </figure>
            </header>
            </div>
            <div className="launcher-product-features">
                {demos.map((demo, index) => (
                    <section className={`launcher-feature${index % 2 ? ' launcher-feature-reverse' : ''}${index === 0 || index === demos.length - 1 ? ' launcher-feature-showcase' : ''}`} data-feature={demo.clip} key={demo.clip} aria-labelledby={`launcher-${demo.clip}-title`}>
                        <div className="launcher-feature-inner">
                            <div className="launcher-feature-copy">
                                <h2 id={`launcher-${demo.clip}-title`}>{demo.title}<br /><span>{demo.accent}</span></h2>
                                <p>{demo.description}</p>
                            </div>
                            <LauncherDemo clip={demo.clip} alt={demo.alt} />
                        </div>
                    </section>
                ))}
            </div>
            <section className="launcher-open-source" aria-labelledby="launcher-open-source-title">
                <div className="launcher-open-source-content">
                    <div className="launcher-open-source-brand">
                        <img className="dark:hidden" src="/assets/logo.svg" width="253" height="45" alt="Modtale" loading="lazy" />
                        <img className="hidden dark:block" src="/assets/logo_light.svg" width="253" height="45" alt="Modtale" loading="lazy" />
                    </div>
                    <h2 id="launcher-open-source-title">Built by the community,<br /><span>for the community.</span></h2>
                    <p>The Modtale Launcher is open source. Explore the code, contribute an idea, and help shape the way we play.</p>
                    <a className="launcher-source-button" href="https://github.com/Modtale/modtale/tree/develop/launcher" target="_blank" rel="noreferrer">
                        <GitHubBrandIcon width={22} height={22} aria-hidden="true" /> View Source Code
                    </a>
                </div>
            </section>
        </main>
    );
};
