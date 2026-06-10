import { useEffect, useMemo, useState } from 'react';
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

export const useHMWiki = (projectId?: string, pageSlug?: string, enabled: boolean = false) => {
    const [modData, setModData] = useState<any>(null);
    const [pageCache, setPageCache] = useState<Record<string, any>>({});
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(false);

    useEffect(() => {
        if (!enabled || !projectId) {
            setModData(null);
            setPageCache({});
            setLoading(false);
            setError(false);
            return;
        }

        let isMounted = true;
        setLoading(true);
        setError(false);
        setModData(null);
        setPageCache({});

        projectClient.getWikiData(projectId)
            .then(async (data) => {
                if (!isMounted) return;
                setModData(data);

                const slugs = collectWikiSlugs(data?.pages);
                if (typeof data?.index?.slug === 'string' && data.index.slug.trim()) {
                    slugs.add(data.index.slug);
                }
                const targetSlug = pageSlug || data?.index?.slug || (data?.pages?.length > 0 ? data.pages[0].slug : null);
                if (typeof targetSlug === 'string' && targetSlug.trim()) {
                    slugs.add(targetSlug);
                }

                const pageEntries = await Promise.allSettled(
                    [...slugs]
                        .filter((slug): slug is string => typeof slug === 'string' && slug.trim().length > 0)
                        .map(async (slug) => [slug, await projectClient.getWikiPage(projectId, slug)] as const)
                );

                if (!isMounted) return;

                const nextCache = pageEntries.reduce<Record<string, any>>((acc, result) => {
                    if (result.status === 'fulfilled') {
                        const [slug, content] = result.value;
                        acc[slug] = content;
                    }
                    return acc;
                }, {});

                setPageCache(nextCache);
                if (Object.keys(nextCache).length === 0 && slugs.size > 0) {
                    setError(true);
                }
            })
            .catch(() => {
                if (isMounted) {
                    setError(true);
                }
            })
            .finally(() => {
                if (isMounted) {
                    setLoading(false);
                }
            });

        return () => {
            isMounted = false;
        };
    }, [projectId, enabled]);

    const content = useMemo(() => {
        if (!modData) return null;
        const targetSlug = pageSlug || modData.index?.slug || (modData.pages?.length > 0 ? modData.pages[0].slug : null);
        return targetSlug ? pageCache[targetSlug] ?? null : null;
    }, [modData, pageSlug, pageCache]);

    return {
        data: modData ? { mod: modData, content } : null,
        loading,
        error
    };
};
