import { useEffect, useState } from 'react';
import { projectClient } from '../api/projectClient';

export const useHMWiki = (projectId?: string, pageSlug?: string, enabled: boolean = false) => {
    const [modData, setModData] = useState<any>(null);
    const [content, setContent] = useState<any>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(false);

    useEffect(() => {
        if (!enabled || !projectId) return;
        let isMounted = true;
        setError(false);
        projectClient.getWikiData(projectId)
            .then(data => { if (isMounted) setModData(data); })
            .catch(() => { if (isMounted) setError(true); });
        return () => { isMounted = false; };
    }, [projectId, enabled]);

    useEffect(() => {
        if (!enabled || !projectId || !modData) return;
        let isMounted = true;
        setLoading(true);
        const targetSlug = pageSlug || modData.index?.slug || (modData.pages?.length > 0 ? modData.pages[0].slug : null);

        if (targetSlug) {
            projectClient.getWikiPage(projectId, targetSlug)
                .then(data => { if (isMounted) setContent(data); })
                .catch(() => { if (isMounted) setContent(null); })
                .finally(() => { if (isMounted) setLoading(false); });
        } else {
            setContent(null);
            setLoading(false);
        }
        return () => { isMounted = false; };
    }, [projectId, pageSlug, enabled, modData]);

    return { data: modData ? { mod: modData, content } : null, loading: loading || (enabled && !modData && !error), error };
};
