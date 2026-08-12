import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { projectClient } from '../api/projectClient';

type WikiCacheEntry = {
    modData?: any;
    modPromise?: Promise<any>;
    pages: Map<string, any>;
    pagePromises: Map<string, Promise<any>>;
    prefetchRunId: number;
    bootstrapSeeded?: boolean;
};

const ROOT_WIKI_PAGE_FAST_PATH_SLUG = 'home-1';
const WIKI_BACKGROUND_PREFETCH_LIMIT = 4;

const collectWikiSlugs = (nodes: any[] | undefined, slugs = new Set<string>()) => {
    for (const node of nodes || []) {
        if (typeof node?.slug === 'string' && node.slug.trim()) {
            slugs.add(node.slug);
        }
        if (Array.isArray(node?.children) && node.children.length > 0) {
            collectWikiSlugs(node.children, slugs);
        }
    }
    return slugs;
};

const normalizeSlug = (slug?: string | null) => {
    if (typeof slug !== 'string') return null;
    const trimmed = slug.trim();
    return trimmed.length > 0 ? trimmed : null;
};

const resolveTargetSlug = (modData: any, pageSlug?: string) => (
    normalizeSlug(pageSlug)
    || normalizeSlug(modData?.index?.slug)
    || normalizeSlug(modData?.pages?.length > 0 ? modData.pages[0].slug : null)
);

const wikiCache = new Map<string, WikiCacheEntry>();

const getWikiCacheEntry = (projectId: string) => {
    let entry = wikiCache.get(projectId);
    if (!entry) {
        entry = {
            pages: new Map<string, any>(),
            pagePromises: new Map<string, Promise<any>>(),
            prefetchRunId: 0
        };
        wikiCache.set(projectId, entry);
    }
    return entry;
};

const getCachedPageRecord = (projectId: string) => (
    Object.fromEntries(getWikiCacheEntry(projectId).pages.entries())
);

const sameWikiProject = (left?: string, right?: string) => (
    Boolean(left && right && left.trim().toLowerCase() === right.trim().toLowerCase())
);

const getWikiBootstrap = (projectId: string) => {
    if (typeof window === 'undefined') return null;
    const bootstrap = window.__MODTALE_WIKI_BOOTSTRAP;
    return sameWikiProject(bootstrap?.projectId, projectId) ? bootstrap : null;
};

const hasOwn = (value: object | null | undefined, key: string) => (
    Boolean(value && Object.prototype.hasOwnProperty.call(value, key))
);

const seedWikiCacheFromBootstrap = (projectId: string) => {
    const entry = getWikiCacheEntry(projectId);
    if (entry.bootstrapSeeded) return entry;
    entry.bootstrapSeeded = true;

    const bootstrap = getWikiBootstrap(projectId);
    if (!bootstrap) return entry;

    if (hasOwn(bootstrap, 'metadataData') && bootstrap.metadataData) {
        entry.modData = bootstrap.metadataData;
    }

    for (const [slug, page] of Object.entries(bootstrap.pages || {})) {
        if (hasOwn(page, 'data') && page.data) {
            entry.pages.set(slug, page.data);
        }
    }

    return entry;
};

const getBootstrappedWikiData = (projectId: string) => {
    const bootstrap = getWikiBootstrap(projectId);
    if (hasOwn(bootstrap, 'metadataData')) {
        return bootstrap?.metadataData ? Promise.resolve(bootstrap.metadataData) : null;
    }

    const metadata = bootstrap?.metadata;
    if (!metadata) return null;

    return metadata.then((data) => (
        data ?? projectClient.getWikiData(projectId)
    ));
};

const getBootstrappedWikiPage = (projectId: string, slug: string) => {
    const pageRecord = getWikiBootstrap(projectId)?.pages?.[slug];
    if (hasOwn(pageRecord, 'data')) {
        return pageRecord?.data ? Promise.resolve(pageRecord.data) : null;
    }

    const page = pageRecord?.promise;
    if (!page) return null;

    return page.then((data) => (
        data ?? projectClient.getWikiPage(projectId, slug)
    ));
};

const collectWikiSlugList = (modData: any, pageSlug?: string) => {
    const slugs = collectWikiSlugs(modData?.pages);
    const indexSlug = normalizeSlug(modData?.index?.slug);
    const targetSlug = resolveTargetSlug(modData, pageSlug);

    if (indexSlug) slugs.add(indexSlug);
    if (targetSlug) slugs.add(targetSlug);

    return [...slugs]
        .map((slug) => normalizeSlug(slug))
        .filter((slug): slug is string => Boolean(slug));
};

const collectNearbyWikiSlugs = (modData: any, pageSlug?: string) => {
    const slugs = collectWikiSlugList(modData, pageSlug);
    const targetSlug = resolveTargetSlug(modData, pageSlug);
    const targetIndex = targetSlug ? slugs.indexOf(targetSlug) : -1;
    const prioritized: string[] = [];
    const seen = new Set<string>();

    const addSlug = (slug?: string | null) => {
        const normalized = normalizeSlug(slug);
        if (!normalized || seen.has(normalized)) return;
        seen.add(normalized);
        prioritized.push(normalized);
    };

    addSlug(targetSlug);
    addSlug(modData?.index?.slug);

    if (targetIndex >= 0) {
        for (let offset = 1; prioritized.length < WIKI_BACKGROUND_PREFETCH_LIMIT + 1 && offset <= slugs.length; offset += 1) {
            addSlug(slugs[targetIndex + offset]);
            addSlug(slugs[targetIndex - offset]);
        }
    }

    for (const slug of slugs) {
        if (prioritized.length >= WIKI_BACKGROUND_PREFETCH_LIMIT + 1) break;
        addSlug(slug);
    }

    return prioritized;
};

const fetchWikiDataCached = (projectId: string) => {
    const entry = seedWikiCacheFromBootstrap(projectId);
    if (entry.modData) return Promise.resolve(entry.modData);
    if (entry.modPromise) return entry.modPromise;

    entry.modPromise = (getBootstrappedWikiData(projectId) ?? projectClient.getWikiData(projectId))
        .then((data) => {
            entry.modData = data;
            return data;
        })
        .catch((error) => {
            entry.modPromise = undefined;
            throw error;
        });

    return entry.modPromise;
};

const fetchWikiPageCached = (projectId: string, slug: string) => {
    const entry = seedWikiCacheFromBootstrap(projectId);
    if (entry.pages.has(slug)) {
        return Promise.resolve(entry.pages.get(slug));
    }

    const pending = entry.pagePromises.get(slug);
    if (pending) return pending;

    const request = (getBootstrappedWikiPage(projectId, slug) ?? projectClient.getWikiPage(projectId, slug))
        .then((content) => {
            entry.pages.set(slug, content);
            return content;
        })
        .catch((error) => {
            entry.pagePromises.delete(slug);
            throw error;
        })
        .finally(() => {
            entry.pagePromises.delete(slug);
        });

    entry.pagePromises.set(slug, request);
    return request;
};

const scheduleLowPriorityWikiWork = (callback: () => void) => {
    if (typeof window === 'undefined') {
        const timeout = setTimeout(callback, 100);
        return () => clearTimeout(timeout);
    }

    const idleWindow = window as Window & {
        requestIdleCallback?: (callback: () => void, options?: { timeout?: number }) => number;
        cancelIdleCallback?: (handle: number) => void;
    };

    if (idleWindow.requestIdleCallback) {
        const handle = idleWindow.requestIdleCallback(callback, { timeout: 750 });
        return () => idleWindow.cancelIdleCallback?.(handle);
    }

    const timeout = window.setTimeout(callback, 100);
    return () => window.clearTimeout(timeout);
};

export const prefetchInitialWikiPage = (projectId?: string) => {
    if (!projectId) return;

    void fetchWikiDataCached(projectId)
        .then((data) => {
            const initialSlug = resolveTargetSlug(data);
            if (initialSlug) {
                return fetchWikiPageCached(projectId, initialSlug);
            }
            return null;
        })
        .catch(() => {
            // Background warmup should never surface user-facing errors.
        });
};

export const prefetchWikiPage = (projectId?: string, slug?: string | null) => {
    const normalizedSlug = normalizeSlug(slug);
    if (!projectId || !normalizedSlug) return;

    void fetchWikiPageCached(projectId, normalizedSlug).catch(() => {
        // Hover/focus prefetch should never surface user-facing errors.
    });
};

export const clearHMWikiCacheForTests = () => {
    wikiCache.clear();
};

export const useHMWiki = (projectId?: string, pageSlug?: string, enabled: boolean = false) => {
    const [modData, setModData] = useState<any>(() => (
        enabled && projectId ? seedWikiCacheFromBootstrap(projectId).modData ?? null : null
    ));
    const [pageCache, setPageCache] = useState<Record<string, any>>(() => (
        enabled && projectId ? (seedWikiCacheFromBootstrap(projectId), getCachedPageRecord(projectId)) : {}
    ));
    const [wikiSlugs, setWikiSlugs] = useState<string[]>([]);
    const [metadataLoading, setMetadataLoading] = useState(false);
    const [pageLoading, setPageLoading] = useState(false);
    const [error, setError] = useState(false);
    const requestIdRef = useRef(0);
    const pageSlugRef = useRef(pageSlug);

    useEffect(() => {
        pageSlugRef.current = pageSlug;
    }, [pageSlug]);

    const syncCachedPages = useCallback((currentProjectId: string) => {
        setPageCache(getCachedPageRecord(currentProjectId));
    }, []);

    useEffect(() => {
        if (!enabled || !projectId) {
            requestIdRef.current += 1;
            setModData(null);
            setWikiSlugs([]);
            setPageCache({});
            setMetadataLoading(false);
            setPageLoading(false);
            setError(false);
            return;
        }

        const requestId = requestIdRef.current + 1;
        requestIdRef.current = requestId;
        const cachedModData = seedWikiCacheFromBootstrap(projectId).modData ?? null;

        setModData(cachedModData);
        setWikiSlugs(cachedModData ? collectNearbyWikiSlugs(cachedModData, pageSlugRef.current) : []);
        syncCachedPages(projectId);
        setMetadataLoading(!cachedModData);
        setPageLoading(false);
        setError(false);

        fetchWikiDataCached(projectId)
            .then((data) => {
                if (requestIdRef.current !== requestId) return;
                setModData(data);
                setWikiSlugs(collectNearbyWikiSlugs(data, pageSlugRef.current));
            })
            .catch(() => {
                if (requestIdRef.current === requestId) {
                    const selectedSlug = normalizeSlug(pageSlugRef.current) || ROOT_WIKI_PAGE_FAST_PATH_SLUG;
                    setError(!selectedSlug || !getWikiCacheEntry(projectId).pages.has(selectedSlug));
                }
            })
            .finally(() => {
                if (requestIdRef.current === requestId) {
                    setMetadataLoading(false);
                }
            });

        return () => {
            if (requestIdRef.current === requestId) {
                requestIdRef.current += 1;
            }
        };
    }, [projectId, enabled]);

    const explicitPageSlug = useMemo(() => normalizeSlug(pageSlug), [pageSlug]);
    const targetIsOptimisticRoot = Boolean(enabled && projectId && !explicitPageSlug && !modData);
    const targetSlug = useMemo(() => (
        explicitPageSlug
        || (modData ? resolveTargetSlug(modData) : ROOT_WIKI_PAGE_FAST_PATH_SLUG)
    ), [explicitPageSlug, modData]);

    useEffect(() => {
        if (!enabled || !projectId || !targetSlug) {
            setPageLoading(false);
            return;
        }
        if (Object.prototype.hasOwnProperty.call(pageCache, targetSlug)) {
            setPageLoading(false);
            setError(false);
            return;
        }

        let isCurrent = true;
        const requestId = requestIdRef.current;
        getWikiCacheEntry(projectId).prefetchRunId += 1;
        setPageLoading(true);
        setError(false);

        fetchWikiPageCached(projectId, targetSlug)
            .then(() => {
                if (isCurrent && requestIdRef.current === requestId) {
                    setError(false);
                    syncCachedPages(projectId);
                }
            })
            .catch(() => {
                if (isCurrent && requestIdRef.current === requestId) {
                    setError(!targetIsOptimisticRoot);
                }
            })
            .finally(() => {
                if (isCurrent && requestIdRef.current === requestId) {
                    setPageLoading(false);
                }
            });

        return () => {
            isCurrent = false;
        };
    }, [enabled, pageCache, projectId, syncCachedPages, targetIsOptimisticRoot, targetSlug]);

    useEffect(() => {
        if (!enabled || !projectId || !modData || !targetSlug || wikiSlugs.length === 0) return;
        if (!Object.prototype.hasOwnProperty.call(pageCache, targetSlug)) return;

        let isCancelled = false;
        const requestId = requestIdRef.current;
        const entry = getWikiCacheEntry(projectId);
        const prefetchRunId = entry.prefetchRunId + 1;
        entry.prefetchRunId = prefetchRunId;
        const slugsToPrefetch = wikiSlugs
            .filter((slug) => slug !== targetSlug)
            .slice(0, WIKI_BACKGROUND_PREFETCH_LIMIT);

        const cancelScheduledWork = scheduleLowPriorityWikiWork(() => {
            void (async () => {
                for (const slug of slugsToPrefetch) {
                    if (isCancelled || entry.prefetchRunId !== prefetchRunId) return;
                    if (entry.pages.has(slug)) continue;

                    try {
                        await fetchWikiPageCached(projectId, slug);
                        if (!isCancelled && requestIdRef.current === requestId) {
                            syncCachedPages(projectId);
                        }
                    } catch {
                        // Ignore background page failures; selected-page fetches surface errors.
                    }
                }
            })();
        });

        return () => {
            isCancelled = true;
            cancelScheduledWork();
        };
    }, [enabled, modData, pageCache, projectId, syncCachedPages, targetSlug, wikiSlugs]);

    const content = useMemo(() => {
        return targetSlug ? pageCache[targetSlug] ?? null : null;
    }, [targetSlug, pageCache]);
    const activePagePending = Boolean(
        enabled
        && projectId
        && targetSlug
        && !error
        && !Object.prototype.hasOwnProperty.call(pageCache, targetSlug)
    );

    return {
        data: modData || content ? { mod: modData ?? { pages: [] }, content, pageCache } : null,
        loading: pageLoading || activePagePending || (metadataLoading && !content),
        error
    };
};
