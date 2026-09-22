import { api, SERVER_BACKEND_URL } from '@/utils/api';
import type { NewsPost } from '@/data/news';

export interface NewsContent {
    title: string; description: string; excerpt: string; author: string; tags: string[];
    heroImage: string; heroAlt: string; body: string;
}
export interface NewsDraft {
    slug: string; version: number | null; draft: NewsContent; published: NewsContent | null;
    publishedAt?: string; updatedAt?: string; draftUpdatedAt?: string;
}
export const newsClient = {
    list: async () => {
        const data = (await api.get<NewsPost[]>('/news')).data;
        if (!Array.isArray(data)) throw new Error('Invalid news response');
        return data;
    },
    get: async (slug: string) => (await api.get<NewsPost>(`/news/${encodeURIComponent(slug)}`)).data,
    drafts: async () => (await api.get<NewsDraft[]>('/admin/news')).data,
    save: async (post: NewsDraft) => (await api.put<NewsDraft>(`/admin/news/${encodeURIComponent(post.slug)}`, { version: post.version, content: post.draft })).data,
    publish: async (post: NewsDraft) => (await api.post<NewsDraft>(`/admin/news/${encodeURIComponent(post.slug)}/publish`, { version: post.version })).data,
    unpublish: async (post: NewsDraft) => (await api.post<NewsDraft>(`/admin/news/${encodeURIComponent(post.slug)}/unpublish`, { version: post.version })).data,
    upload: async (file: File) => { const data = new FormData(); data.append('file', file); return (await api.post<{ url: string }>('/admin/news/media', data)).data.url; },
};

export async function fetchNewsPosts(): Promise<NewsPost[]> {
    const response = await fetch(`${SERVER_BACKEND_URL}/api/v1/news`, { signal: AbortSignal.timeout(5000) });
    if (!response.ok) throw new Error('News is unavailable');
    return response.json();
}
