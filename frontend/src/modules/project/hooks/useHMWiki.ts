import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { projectClient } from '../api/projectClient';

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

export const useHMWiki = (projectId?: string, pageSlug?: string, enabled: boolean = false) => {
    const [modData, setModData] = useState<any>(null);
    const [pageCache, setPageCache] = useState<Record<string, any>>({});
    const [wikiSlugs, setWikiSlugs] = useState<string[]>([]);
    const [metadataLoading, setMetadataLoading] = useState(false);
    const [pageLoading, setPageLoading] = useState(false);
    const [error, setError] = useState(false);
    const pageCacheRef = useRef<Record<string, any>>({});
    const inFlightPagesRef = useRef<Map<string, Promise<any>>>(new Map());
    const requestIdRef = useRef(0);
    const pageSlugRef = useRef(pageSlug);

    useEffect(() => {
        pageSlugRef.current = pageSlug;
    }, [pageSlug]);

    const cachePage = useCallback((slug: string, content: any) => {
        pageCacheRef.current = {
            ...pageCacheRef.current,
            [slug]: content
        };
        setPageCache(pageCacheRef.current);
    }, []);

    const fetchWikiPage = useCallback((currentProjectId: string, slug: string, requestId: number) => {
        if (Object.prototype.hasOwnProperty.call(pageCacheRef.current, slug)) {
            return Promise.resolve(pageCacheRef.current[slug]);
        }

        const pending = inFlightPagesRef.current.get(slug);
        if (pending) return pending;

        const request = projectClient.getWikiPage(currentProjectId, slug)
            .then((content) => {
                if (requestIdRef.current === requestId) {
                    cachePage(slug, content);
                }
                return content;
            })
            .finally(() => {
                if (requestIdRef.current === requestId) {
                    inFlightPagesRef.current.delete(slug);
                }
            });

        inFlightPagesRef.current.set(slug, request);
        return request;
    }, [cachePage]);

    useEffect(() => {
        if (!enabled || !projectId) {
            requestIdRef.current += 1;
            setModData(null);
            setWikiSlugs([]);
            pageCacheRef.current = {};
            inFlightPagesRef.current.clear();
            setPageCache({});
            setMetadataLoading(false);
            setPageLoading(false);
            setError(false);
            return;
        }

        const requestId = requestIdRef.current + 1;
        requestIdRef.current = requestId;
        pageCacheRef.current = {};
        inFlightPagesRef.current.clear();

        setMetadataLoading(true);
        setPageLoading(false);
        setError(false);
        setModData(null);
        setWikiSlugs([]);
        setPageCache({});

        projectClient.getWikiData(projectId)
            .then((data) => {
                if (requestIdRef.current !== requestId) return;
                setModData(data);

                const slugs = collectWikiSlugs(data?.pages);
                if (typeof data?.index?.slug === 'string' && data.index.slug.trim()) {
                    slugs.add(data.index.slug);
                }
                const targetSlug = resolveTargetSlug(data, pageSlugRef.current);
                if (targetSlug) {
                    slugs.add(targetSlug);
                }

                setWikiSlugs(
                    [...slugs]
                        .map((slug) => normalizeSlug(slug))
                        .filter((slug): slug is string => Boolean(slug))
                );
            })
            .catch(() => {
                if (requestIdRef.current === requestId) {
                    setError(true);
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

    const targetSlug = useMemo(() => (
        modData ? resolveTargetSlug(modData, pageSlug) : null
    ), [modData, pageSlug]);

    useEffect(() => {
        if (!enabled || !projectId || !modData || !targetSlug) {
            setPageLoading(false);
            return;
        }
        if (Object.prototype.hasOwnProperty.call(pageCacheRef.current, targetSlug)) {
            setPageLoading(false);
            setError(false);
            return;
        }

        let isCurrent = true;
        const requestId = requestIdRef.current;
        setPageLoading(true);
        setError(false);

        fetchWikiPage(projectId, targetSlug, requestId)
            .catch(() => {
                if (isCurrent && requestIdRef.current === requestId) {
                    setError(true);
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
    }, [enabled, fetchWikiPage, modData, projectId, targetSlug]);

    useEffect(() => {
        if (!enabled || !projectId || !modData || wikiSlugs.length === 0) return;

        const requestId = requestIdRef.current;
        const slugsToPrefetch = wikiSlugs.filter((slug) => slug !== targetSlug);
        void Promise.allSettled(slugsToPrefetch.map((slug) => fetchWikiPage(projectId, slug, requestId)));
    }, [enabled, fetchWikiPage, modData, projectId, targetSlug, wikiSlugs]);

    const content = useMemo(() => {
        if (!modData) return null;
        return targetSlug ? pageCache[targetSlug] ?? null : null;
    }, [modData, targetSlug, pageCache]);
    const activePagePending = Boolean(
        enabled
        && projectId
        && modData
        && targetSlug
        && !error
        && !Object.prototype.hasOwnProperty.call(pageCache, targetSlug)
    );

    return {
        data: modData ? { mod: modData, content, pageCache } : null,
        loading: metadataLoading || pageLoading || activePagePending,
        error
    };
};
