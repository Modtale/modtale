import { useEffect, useState } from 'react';
import { useSSRData } from '@/context/SSRContext';
import type { NewsPost } from '@/data/news';
import { newsClient } from './newsClient';

export function useNewsPosts(enabled = true) {
    const { initialData } = useSSRData();
    const [posts, setPosts] = useState<NewsPost[]>(initialData?.newsPosts || []);
    const [error, setError] = useState(false);
    const [loading, setLoading] = useState(!initialData?.newsPosts);
    useEffect(() => { if (!enabled) return; let active = true; newsClient.list().then(data => { if (active) { setPosts(data); setError(false); } }).catch(() => { if (active) setError(true); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; }; }, [enabled]);
    return { posts, loading, error };
}
export function useNewsPost(slug?: string) {
    const { initialData } = useSSRData();
    const [result, setResult] = useState<{ post?: NewsPost; slug?: string; error?: boolean; missing?: boolean }>({ post: initialData?.newsPost, slug });
    useEffect(() => {
        if (!slug) return;
        let active = true;
        newsClient.get(slug).then(post => { if (active) setResult({ slug, post }); }).catch(e => { if (active) setResult({ slug, error: true, missing: e.response?.status === 404 }); });
        return () => { active = false; };
    }, [slug]);
    return result.slug === slug ? result : {};
}
