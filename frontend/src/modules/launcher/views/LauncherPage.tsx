import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import {
    Check,
    Download,
    ExternalLink,
    FileText,
    Gamepad2,
    PackageCheck,
    Search,
} from 'lucide-react';
import { ROUTE_SEO } from '@/data/seo-constants';
import { SiteRoutes } from '@/utils/routes';
import { GLASS_CARD } from '@/modules/home/styles';

type LauncherPlatform = 'windows' | 'mac' | 'linux' | 'unknown';

type GitHubReleaseAsset = {
    name: string;
    browser_download_url: string;
};

type GitHubRelease = {
    tag_name?: string;
    name?: string;
    html_url?: string;
    draft?: boolean;
    prerelease?: boolean;
    assets?: GitHubReleaseAsset[];
};

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

const GITHUB_RELEASES_API_URL = 'https://api.github.com/repos/Modtale/modtale/releases?per_page=30';
const GITHUB_RELEASES_URL = 'https://github.com/Modtale/modtale/releases';
const GITHUB_LATEST_RELEASE_URL = 'https://github.com/Modtale/modtale/releases/latest';

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

const featureSections = [
    {
        eyebrow: 'Browse',
        title: 'Browse Modtale without leaving desktop',
        subtitle: 'Project pages, creator context, and releases in one native surface.',
        body: 'Open rich project pages, scan metadata, save favorites, and move from discovery to install without bouncing between a browser and your Hytale folders.',
        image: '/assets/launcher/project.png',
        imageAlt: 'Modtale Launcher showing a project page with download, changelog, and comment actions',
        icon: Search,
        accent: 'text-blue-600 dark:text-blue-400',
        titleGradient: 'from-blue-500 to-indigo-500 dark:from-blue-400 dark:to-indigo-400',
        glowFrom: 'rgba(37, 99, 235, 0.1)',
        glowTo: 'rgba(99, 102, 241, 0.08)',
        previewGlow: 'from-blue-500/5 via-transparent to-indigo-500/5 dark:from-blue-500/10 dark:via-transparent dark:to-indigo-500/10',
        points: ['Project details', 'Favorite and share actions', 'Release context'],
    },
    {
        eyebrow: 'Install',
        title: 'Install the right build with fewer choices to juggle',
        subtitle: 'Version-aware downloads, changelogs, and dependency-aware setup.',
        body: 'Pick a project and let the launcher keep the install path, supported game versions, and dependency prompts close to the download flow.',
        image: '/assets/launcher/patchly.png',
        imageAlt: 'Modtale Launcher showing a project download page for Patchly',
        icon: PackageCheck,
        accent: 'text-emerald-600 dark:text-emerald-400',
        titleGradient: 'from-blue-500 to-emerald-500 dark:from-blue-400 dark:to-emerald-400',
        glowFrom: 'rgba(59, 130, 246, 0.1)',
        glowTo: 'rgba(16, 185, 129, 0.08)',
        previewGlow: 'from-blue-500/5 via-transparent to-emerald-500/5 dark:from-blue-500/10 dark:via-transparent dark:to-emerald-500/10',
        points: ['Compatible builds', 'Dependency prompts', 'Local install paths'],
    },
    {
        eyebrow: 'Learn',
        title: 'Read the project context before you launch',
        subtitle: 'Docs, commands, permissions, and notes live beside the install flow.',
        body: 'Project pages can carry wiki-style instructions, command references, code snippets, and setup notes so server owners and players know what they are installing.',
        image: '/assets/launcher/voile-mid.png',
        imageAlt: 'Modtale Launcher showing project documentation and command reference content',
        icon: FileText,
        accent: 'text-amber-600 dark:text-amber-300',
        titleGradient: 'from-amber-500 to-orange-500 dark:from-amber-300 dark:to-orange-300',
        glowFrom: 'rgba(245, 158, 11, 0.1)',
        glowTo: 'rgba(249, 115, 22, 0.08)',
        previewGlow: 'from-amber-500/5 via-transparent to-orange-500/5 dark:from-amber-500/10 dark:via-transparent dark:to-orange-500/10',
        points: ['Project docs', 'Command notes', 'Config snippets'],
    },
    {
        eyebrow: 'Play',
        title: 'Keep library, account, and launch flows close',
        subtitle: 'Browse, Library, Play, and account controls stay in the same app chrome.',
        body: 'Use the native launcher as the handoff between Modtale projects and your local Hytale setup, with room for signed-in installs, library updates, and launch-session work.',
        image: '/assets/launcher/voile.png',
        imageAlt: 'Modtale Launcher showing project details inside the native launcher shell',
        icon: Gamepad2,
        accent: 'text-violet-600 dark:text-violet-300',
        titleGradient: 'from-violet-500 to-blue-500 dark:from-violet-300 dark:to-blue-300',
        glowFrom: 'rgba(124, 58, 237, 0.1)',
        glowTo: 'rgba(37, 99, 235, 0.08)',
        previewGlow: 'from-violet-500/5 via-transparent to-blue-500/5 dark:from-violet-500/10 dark:via-transparent dark:to-blue-500/10',
        points: ['Library updates', 'Hytale sign-in', 'Launcher settings'],
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

const isLauncherAssetName = (assetName?: string) => {
    const name = (assetName || '').toLowerCase();
    return name.endsWith('.exe')
        || name.endsWith('.msi')
        || name.endsWith('.dmg')
        || name.endsWith('.pkg')
        || name.endsWith('.appimage');
};

const isLauncherRelease = (release: GitHubRelease) => {
    const tagName = (release.tag_name || '').toLowerCase();
    const releaseName = (release.name || '').toLowerCase();

    return tagName.startsWith('launcher-v')
        || releaseName.includes('launcher')
        || Boolean(release.assets?.some((asset) => isLauncherAssetName(asset.name)));
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
const secondaryActionClass = 'inline-flex h-14 w-full items-center justify-center gap-3 rounded-2xl border border-slate-200 bg-white px-7 text-base font-bold text-slate-900 shadow-sm transition-all hover:-translate-y-0.5 hover:bg-slate-50 hover:shadow-md sm:w-auto dark:border-white/10 dark:bg-slate-800/80 dark:text-white dark:hover:bg-slate-700 transform-gpu whitespace-nowrap';

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

const LauncherFeatureBodyText = ({ children }: { children: React.ReactNode }) => (
    <p className="mx-auto max-w-xl text-lg font-medium leading-relaxed text-slate-500 [text-wrap:balance] sm:text-xl dark:text-slate-400">
        {children}
    </p>
);

const LauncherScreenshotPreview = ({
    src,
    alt,
    glowClass,
    priority = false,
}: {
    src: string;
    alt: string;
    glowClass: string;
    priority?: boolean;
}) => (
    <figure className="relative w-full overflow-visible">
        <div className={`absolute inset-0 rounded-3xl bg-gradient-to-tr ${glowClass} blur-2xl pointer-events-none`} />
        <div className={`${GLASS_CARD} relative p-2 sm:p-3`}>
            <img
                src={src}
                alt={alt}
                className="block aspect-video w-full rounded-2xl bg-slate-100 object-cover shadow-sm dark:bg-slate-900"
                loading={priority ? 'eager' : 'lazy'}
                fetchPriority={priority ? 'high' : 'auto'}
                decoding="async"
            />
        </div>
    </figure>
);

const LauncherFeaturePreview = ({
    feature,
    reverse = false,
    priority = false,
}: {
    feature: typeof featureSections[number];
    reverse?: boolean;
    priority?: boolean;
}) => {
    const Icon = feature.icon;

    return (
        <div className={`flex flex-col items-center gap-12 lg:gap-16 2xl:gap-24 ${reverse ? 'lg:flex-row-reverse' : 'lg:flex-row'}`}>
            <div className="flex flex-1 flex-col items-center space-y-5 text-center">
                <div className={`inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white/80 px-4 py-2 text-xs font-black uppercase tracking-wider shadow-sm backdrop-blur dark:border-white/10 dark:bg-white/[0.06] ${feature.accent}`}>
                    <Icon className="h-4 w-4" aria-hidden="true" />
                    {feature.eyebrow}
                </div>
                <h2 className="text-4xl font-black leading-tight tracking-normal text-slate-900 sm:text-5xl 2xl:text-6xl dark:text-white">
                    {feature.title}
                </h2>
                <p className={`text-lg font-semibold text-transparent bg-clip-text bg-gradient-to-r sm:text-xl ${feature.titleGradient}`}>
                    {feature.subtitle}
                </p>
                <LauncherFeatureBodyText>
                    {feature.body}
                </LauncherFeatureBodyText>
                <div className="grid w-full max-w-xl grid-cols-1 gap-3 sm:grid-cols-3">
                    {feature.points.map((point) => (
                        <div key={point} className="flex min-h-12 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white/70 px-4 py-3 text-sm font-black text-slate-700 shadow-sm backdrop-blur-md dark:border-white/10 dark:bg-white/[0.04] dark:text-slate-200">
                            <Check className="h-4 w-4 shrink-0 text-emerald-500" aria-hidden="true" />
                            <span>{point}</span>
                        </div>
                    ))}
                </div>
            </div>
            <div className="relative w-full max-w-3xl flex-1 overflow-visible">
                <LauncherScreenshotPreview
                    src={feature.image}
                    alt={feature.imageAlt}
                    glowClass={feature.previewGlow}
                    priority={priority}
                />
            </div>
        </div>
    );
};

const LauncherShowcaseSection = ({
                                     children,
                                     glowFrom,
                                     glowTo,
                                     align = 'left',
                                 }: {
    children: React.ReactNode;
    glowFrom: string;
    glowTo: string;
    align?: 'left' | 'right';
}) => {
    const primaryGlowPosition = align === 'left'
        ? { left: '-8rem', right: 'auto' }
        : { right: '-8rem', left: 'auto' };
    const secondaryGlowPosition = align === 'left'
        ? { right: '-6rem', left: 'auto' }
        : { left: '-6rem', right: 'auto' };

    return (
        <section className="relative isolate overflow-hidden border-t border-slate-200/60 py-20 sm:py-28 dark:border-white/[0.04]">
            <div className="absolute inset-0 bg-gradient-to-b from-slate-50 via-slate-100/70 to-slate-50 dark:from-[#0b1220] dark:via-[#08111d] dark:to-[#070e19]" />
            <div
                className="absolute top-[-4rem] h-56 w-56 rounded-full blur-3xl opacity-35 pointer-events-none sm:h-72 sm:w-72"
                style={{
                    ...primaryGlowPosition,
                    background: `radial-gradient(circle, ${glowFrom} 0%, transparent 72%)`,
                }}
            />
            <div
                className="absolute bottom-[-5rem] h-64 w-64 rounded-full blur-3xl opacity-25 pointer-events-none sm:h-80 sm:w-80"
                style={{
                    ...secondaryGlowPosition,
                    background: `radial-gradient(circle, ${glowTo} 0%, transparent 74%)`,
                }}
            />
            <div
                className="absolute inset-0 opacity-100 pointer-events-none"
                style={{
                    backgroundImage: `
                        radial-gradient(circle at 1px 1px, rgba(148, 163, 184, 0.09) 1px, transparent 0),
                        linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 18%, transparent 78%, rgba(255, 255, 255, 0.015)),
                        linear-gradient(120deg, rgba(59, 130, 246, 0) 0%, rgba(59, 130, 246, 0.045) 48%, rgba(59, 130, 246, 0) 100%)
                    `,
                    backgroundSize: '20px 20px, 100% 100%, 100% 100%',
                }}
            />
            <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-slate-200/40 to-transparent pointer-events-none dark:from-black/25" />
            <div className="absolute inset-0 pointer-events-none bg-gradient-to-r from-slate-900/5 via-transparent to-slate-900/5 dark:from-slate-950/12 dark:via-transparent dark:to-slate-950/12" />

            <div className="relative z-20 mx-auto max-w-[112rem] px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28">
                {children}
            </div>
        </section>
    );
};

export const LauncherPage: React.FC = () => {
    const seo = ROUTE_SEO[SiteRoutes.launcher()];
    const [detectedPlatform, setDetectedPlatform] = useState<LauncherPlatform>('unknown');
    const [releaseState, setReleaseState] = useState<ReleaseState>({
        isLoading: true,
        releaseName: 'Latest release',
        releaseUrl: GITHUB_LATEST_RELEASE_URL,
        assetsByPlatform: {},
    });

    useEffect(() => {
        setDetectedPlatform(detectPlatform());

        const controller = new AbortController();
        let isCancelled = false;

        fetch(GITHUB_RELEASES_API_URL, {
            signal: controller.signal,
            headers: { Accept: 'application/vnd.github+json' },
        })
            .then((response) => {
                if (!response.ok) throw new Error('GitHub release lookup failed');
                return response.json() as Promise<GitHubRelease[]>;
            })
            .then((releases) => {
                if (isCancelled) return;

                const launcherRelease = releases.find((release) => !release.draft && !release.prerelease && isLauncherRelease(release)) || null;
                const assetsByPlatform = platformOptions.reduce<ReleaseState['assetsByPlatform']>((acc, platform) => {
                    const asset = selectAssetForPlatform(launcherRelease?.assets, platform.id);
                    if (asset) acc[platform.id] = asset;
                    return acc;
                }, {});

                setReleaseState({
                    isLoading: false,
                    releaseName: releaseDisplayName(launcherRelease),
                    releaseUrl: launcherRelease?.html_url || GITHUB_LATEST_RELEASE_URL,
                    assetsByPlatform,
                });
            })
            .catch(() => {
                if (!isCancelled) {
                    setReleaseState((current) => ({ ...current, isLoading: false }));
                }
            });

        return () => {
            isCancelled = true;
            controller.abort();
        };
    }, []);

    const primaryPlatform = detectedPlatform === 'unknown' ? 'windows' : detectedPlatform;
    const primaryPlatformOption = platformOptions.find((platform) => platform.id === primaryPlatform) || platformOptions[0];
    const primaryAsset = releaseState.assetsByPlatform[primaryPlatformOption.id];
    const primaryDownloadLabel = `Download for ${primaryPlatformOption.shortLabel}`;
    const releaseLabel = releaseState.isLoading ? 'Checking latest release...' : releaseState.releaseName;

    return (
        <div className="bg-slate-50 text-slate-900 dark:bg-[#080d19] dark:text-white">
            <Helmet>
                <title>{seo.title}</title>
                <meta name="description" content={seo.description} />
                <meta name="keywords" content={seo.keywords} />
            </Helmet>

            <main className="relative z-10 contain-content">
                <section className="relative isolate flex min-h-[100dvh] w-full flex-col items-center justify-center overflow-hidden border-b border-slate-200/60 px-6 pb-8 pt-12 sm:px-12 sm:pb-10 sm:pt-[7vh] md:px-16 lg:min-h-[92vh] lg:px-20 xl:px-28 dark:border-white/[0.04]">
                    <div className="absolute inset-0 bg-[repeating-linear-gradient(45deg,transparent,transparent_10px,rgba(59,130,246,0.05)_10px,rgba(59,130,246,0.05)_11px)] [mask-image:radial-gradient(ellipse_60%_60%_at_50%_50%,#000_70%,transparent_100%)] pointer-events-none transform-gpu dark:bg-[repeating-linear-gradient(45deg,transparent,transparent_10px,rgba(255,255,255,0.03)_10px,rgba(255,255,255,0.03)_11px)]" />
                    <div className="absolute top-1/4 -left-1/4 h-[800px] w-[800px] rounded-full bg-blue-500/10 blur-[120px] mix-blend-multiply pointer-events-none transform-gpu dark:bg-blue-600/15 dark:mix-blend-screen" />
                    <div className="absolute bottom-1/4 -right-1/4 h-[600px] w-[600px] rounded-full bg-indigo-500/10 blur-[120px] mix-blend-multiply pointer-events-none transform-gpu dark:bg-indigo-600/15 dark:mix-blend-screen" />

                    <div className="relative z-20 mx-auto flex w-full max-w-[112rem] flex-col items-center text-center">
                        <div className="shrink-0">
                            <img
                                src="/assets/logo.svg"
                                alt="Modtale"
                                width={853}
                                height={128}
                                className="mb-2 h-16 w-auto object-contain drop-shadow-sm sm:mb-3 sm:h-20 md:h-24 lg:h-28 dark:hidden"
                                fetchPriority="high"
                                decoding="async"
                            />
                            <img
                                src="/assets/logo_light.svg"
                                alt="Modtale"
                                width={853}
                                height={128}
                                className="mb-2 hidden h-16 w-auto object-contain drop-shadow-sm sm:mb-3 sm:h-20 md:h-24 lg:h-28 dark:block"
                                fetchPriority="high"
                                decoding="async"
                            />
                        </div>

                        <h1 className="mt-1 max-w-5xl text-5xl font-black leading-[0.98] tracking-normal text-slate-900 sm:mt-2 sm:text-6xl lg:text-7xl xl:text-8xl dark:text-white">
                            <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 via-indigo-500 to-blue-500 dark:from-blue-400 dark:via-indigo-400 dark:to-blue-300">
                                Launcher
                            </span>
                        </h1>

                        <p className="mt-6 max-w-3xl text-base font-medium leading-relaxed text-slate-600 sm:text-lg lg:text-xl dark:text-slate-300">
                            Install, update, and manage Hytale projects from a native desktop app that understands Modtale releases, dependencies, local folders, and your game session.
                        </p>

                        <div className="mt-9 flex w-full max-w-3xl flex-col items-center justify-center gap-3 sm:flex-row">
                            <LauncherDownloadButton
                                asset={primaryAsset}
                                label={primaryDownloadLabel}
                                isLoading={releaseState.isLoading}
                            />
                            <a
                                href={releaseState.releaseUrl || GITHUB_RELEASES_URL}
                                target="_blank"
                                rel="noreferrer"
                                className={secondaryActionClass}
                            >
                                <ExternalLink className="h-5 w-5 text-slate-400" aria-hidden="true" />
                                Release Notes
                            </a>
                        </div>

                        <p className="mt-4 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                            {releaseLabel}
                        </p>

                    </div>

                    <div className="absolute bottom-0 left-0 right-0 z-10 h-[150px] bg-gradient-to-t from-slate-100/30 to-transparent pointer-events-none dark:from-[#080d19]" />
                </section>

                <LauncherShowcaseSection glowFrom="rgba(59, 130, 246, 0.08)" glowTo="rgba(16, 185, 129, 0.08)" align="right">
                    <div className="mx-auto max-w-4xl text-center">
                        <h2 className="text-4xl font-black leading-tight tracking-normal text-slate-900 sm:text-5xl dark:text-white">
                            Built around the Modtale project flow.
                        </h2>
                        <p className="mx-auto mt-5 max-w-2xl text-lg font-medium leading-relaxed text-slate-500 sm:text-xl dark:text-slate-400">
                            Browse, install, learn, and launch from the same native surface, with each workflow shown beside the screen players will actually use.
                        </p>
                    </div>
                </LauncherShowcaseSection>

                <div className="bg-slate-50 dark:bg-[#080d19]">
                    {featureSections.map((feature, index) => (
                        <LauncherShowcaseSection key={feature.title} glowFrom={feature.glowFrom} glowTo={feature.glowTo} align={index % 2 === 0 ? 'left' : 'right'}>
                            <LauncherFeaturePreview
                                feature={feature}
                                reverse={index % 2 === 1}
                                priority={index === 0}
                            />
                        </LauncherShowcaseSection>
                    ))}
                </div>

                <section className="relative z-20 overflow-hidden border-t border-slate-200/60 dark:border-white/[0.04]">
                    <div className="absolute inset-0 bg-gradient-to-b from-slate-50 via-slate-100/50 to-slate-50 dark:from-[#09101c] dark:via-[#070d17] dark:to-[#060b14]" />
                    <div className="absolute -top-10 left-[10%] h-56 w-56 rounded-full blur-3xl opacity-20 pointer-events-none" style={{ background: 'radial-gradient(circle, rgba(59, 130, 246, 0.22) 0%, transparent 72%)' }} />
                    <div className="absolute -bottom-16 right-[8%] h-72 w-72 rounded-full blur-3xl opacity-15 pointer-events-none" style={{ background: 'radial-gradient(circle, rgba(16, 185, 129, 0.18) 0%, transparent 74%)' }} />
                    <div
                        className="absolute inset-0 opacity-100 pointer-events-none"
                        style={{
                            backgroundImage: `
                                radial-gradient(circle at 1px 1px, rgba(148, 163, 184, 0.08) 1px, transparent 0),
                                linear-gradient(135deg, rgba(255, 255, 255, 0) 0%, rgba(59, 130, 246, 0.05) 45%, rgba(255, 255, 255, 0) 100%)
                            `,
                            backgroundSize: '22px 22px, 100% 100%',
                        }}
                    />
                    <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-slate-200/80 to-transparent pointer-events-none dark:via-white/10" />
                    <div className="absolute inset-x-0 bottom-0 h-28 bg-gradient-to-t from-slate-200/50 to-transparent pointer-events-none dark:from-black/30" />

                    <div className="relative z-20 mx-auto flex max-w-5xl flex-col items-center px-6 py-24 text-center sm:py-32 lg:py-40">
                        <div className="mb-10 flex justify-center sm:mb-12">
                            <img
                                src="/assets/logo.svg"
                                alt="Modtale"
                                className="h-10 w-auto object-contain drop-shadow-sm sm:h-12 dark:hidden"
                                loading="lazy"
                            />
                            <img
                                src="/assets/logo_light.svg"
                                alt="Modtale"
                                className="hidden h-10 w-auto object-contain drop-shadow-sm sm:h-12 dark:block"
                                loading="lazy"
                            />
                        </div>
                        <h2 className="text-4xl font-black leading-[1.05] tracking-normal text-slate-900 sm:text-5xl lg:text-6xl dark:text-white">
                            Ready for your next Hytale session.
                        </h2>
                        <p className="mt-6 max-w-2xl text-base font-medium leading-relaxed text-slate-600 sm:text-lg lg:text-xl dark:text-slate-300">
                            The launcher is open-source alongside the rest of Modtale, with native packages published from the project release workflow.
                        </p>
                        <div className="mt-9 flex w-full flex-col items-center justify-center gap-3 sm:flex-row">
                            <a
                                href="https://github.com/Modtale/modtale"
                                target="_blank"
                                rel="noreferrer"
                                className={secondaryActionClass}
                            >
                                <ExternalLink className="h-5 w-5 text-slate-400" aria-hidden="true" />
                                View Source
                            </a>
                        </div>
                        <div className="mt-6">
                            <Link to={SiteRoutes.browse()} className="text-sm font-black text-slate-500 transition-colors hover:text-blue-600 dark:text-slate-400 dark:hover:text-blue-300">
                                Browse projects first
                            </Link>
                        </div>
                    </div>
                </section>
            </main>
        </div>
    );
};
