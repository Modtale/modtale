import { useEffect, useMemo, useState } from 'react';
import { useSSRData } from '@/context/SSRContext';
import { api } from '@/utils/api';
import type { Project } from '@/types';

const NEWS_PROJECT_TIMEOUT_MS = 1800;

const dedupeProjects = (items: Project[]) => Array.from(new Map(items.map((project) => [project.id, project])).values());

export const useNewsProjects = () => {
    const { initialData } = useSSRData();
    const initialProjects = useMemo(
        () => Array.isArray(initialData?.newsProjects) ? dedupeProjects(initialData.newsProjects) : [],
        [initialData]
    );
    const [projects, setProjects] = useState<Project[]>(initialProjects);
    const [loading, setLoading] = useState(initialProjects.length === 0);

    useEffect(() => {
        if (initialProjects.length > 0) {
            setProjects(initialProjects);
        }

        let isCancelled = false;

        const loadProjects = async () => {
            if (initialProjects.length === 0) {
                setLoading(true);
            }

            try {
                const res = await api.get('/projects', {
                    params: { size: 10, sort: 'trending', view: 'marquee' },
                    timeout: NEWS_PROJECT_TIMEOUT_MS,
                });
                const nextProjects = Array.isArray(res.data?.content) ? dedupeProjects(res.data.content) : [];
                if (!isCancelled && nextProjects.length > 0) {
                    setProjects(nextProjects);
                }
            } catch {
            } finally {
                if (!isCancelled) {
                    setLoading(false);
                }
            }
        };

        void loadProjects();

        return () => {
            isCancelled = true;
        };
    }, [initialProjects]);

    return { projects, loading };
};
