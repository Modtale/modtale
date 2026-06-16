import { useState, useEffect, useRef, useCallback, useMemo, useLayoutEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { discoveryClient } from '../api/discoveryClient';
import { captureError } from '@/utils/errorTracking';
import type { Project } from '@/types';
import type { Classification } from '@/data/categories';

export type SortOption = 'relevance' | 'popular' | 'trending' | 'downloads' | 'favorites' | 'newest' | 'updated';
export type ViewCategory = 'all' | 'favorites' | 'your_projects';
const BROWSE_CACHE_PREFIX = 'modtale.browse-cache:';

const normalizeSort = (sort: string | null): SortOption => {
    switch (sort) {
        case 'popular':
        case 'trending':
        case 'downloads':
        case 'favorites':
        case 'newest':
        case 'updated':
            return sort;
        case 'new':
            return 'newest';
        default:
            return 'relevance';
    }
};

const normalizeViewCategory = (category: string | null): ViewCategory => {
    switch (category) {
        case 'favorites':
        case 'your_projects':
            return category;
        default:
            return 'all';
    }
};

export const useProjectSearch = (initialClassification: Classification | 'All', useSSRData: boolean, initialItems: Project[], initialTotalPages: number, initialTotalItems: number, initialItemsPerPage = 12) => {
    const [searchParams, setSearchParams] = useSearchParams();

    const parsedPage = parseInt(searchParams.get('page') || '0', 10);
    const page = Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : 0;
    const sortBy = normalizeSort(searchParams.get('sort'));
    const selectedVersion = searchParams.get('version') || 'Any';
    const minDownloads = parseInt(searchParams.get('minDl') || '0');
    const minFavorites = parseInt(searchParams.get('minFav') || '0');
    const filterDate = searchParams.get('date');
    const viewCategory = normalizeViewCategory(searchParams.get('category'));
    const rawTags = searchParams.get('tags');
    const selectedTags = useMemo(() => rawTags ? rawTags.split(',').filter(Boolean) : [], [rawTags]);
    const urlSearchTerm = searchParams.get('q') || '';

    const [searchTerm, setSearchTerm] = useState(urlSearchTerm);
    const [selectedClassification, setSelectedClassification] = useState<Classification | 'All'>(initialClassification);
    const [totalPages, setTotalPages] = useState(initialTotalPages);
    const [totalItems, setTotalItems] = useState(initialTotalItems);
    const [loading, setLoading] = useState(!useSSRData);
    const [isPending, setIsPending] = useState(!useSSRData);
    const [items, setItems] = useState<Project[]>(initialItems);
    const [itemsPerPage, setItemsPerPage] = useState(initialItemsPerPage);

    const abortControllerRef = useRef<AbortController | null>(null);
    const isFirstRender = useRef(true);
    const previousQueryKeyRef = useRef<string | null>(null);
    const cacheKey = useMemo(() => `${BROWSE_CACHE_PREFIX}${JSON.stringify({
        page,
        itemsPerPage,
        selectedClassification,
        selectedTags,
        urlSearchTerm,
        sortBy,
        selectedVersion,
        minDownloads,
        minFavorites,
        filterDate,
        viewCategory,
    })}`, [page, itemsPerPage, selectedClassification, selectedTags, urlSearchTerm, sortBy, selectedVersion, minDownloads, minFavorites, filterDate, viewCategory]);
    const queryKey = useMemo(() => JSON.stringify({
        page,
        itemsPerPage,
        selectedClassification,
        selectedTags,
        urlSearchTerm,
        sortBy,
        selectedVersion,
        minDownloads,
        minFavorites,
        filterDate,
        viewCategory,
    }), [page, itemsPerPage, selectedClassification, selectedTags, urlSearchTerm, sortBy, selectedVersion, minDownloads, minFavorites, filterDate, viewCategory]);

    useEffect(() => {
        if (urlSearchTerm !== searchTerm) {
            setSearchTerm(urlSearchTerm);
        }
    }, [urlSearchTerm]);

    useEffect(() => {
        setSelectedClassification(initialClassification);
    }, [initialClassification]);

    useLayoutEffect(() => {
        if (previousQueryKeyRef.current === null) {
            previousQueryKeyRef.current = queryKey;
            return;
        }

        if (previousQueryKeyRef.current !== queryKey) {
            previousQueryKeyRef.current = queryKey;
            setItems([]);
            setTotalPages(0);
            setTotalItems(0);
            setLoading(true);
            setIsPending(true);
        }
    }, [queryKey]);

    useEffect(() => {
        const handler = setTimeout(() => {
            if (searchTerm !== urlSearchTerm) {
                setIsPending(true);
                setSearchParams(prev => {
                    const next = new URLSearchParams(prev);
                    if (searchTerm) next.set('q', searchTerm);
                    else next.delete('q');
                    next.delete('page');
                    return next;
                }, { replace: true });
            }
        }, 500);
        return () => clearTimeout(handler);
    }, [searchTerm, urlSearchTerm, setSearchParams]);

    useEffect(() => {
        if (useSSRData || typeof window === 'undefined') return;

        try {
            const cached = window.sessionStorage.getItem(cacheKey);
            if (!cached) return;

            const parsed = JSON.parse(cached) as {
                content?: Project[];
                totalPages?: number;
                totalElements?: number;
            };

            if (!Array.isArray(parsed.content) || parsed.content.length === 0) return;

            setItems(parsed.content);
            setTotalPages(parsed.totalPages || 0);
            setTotalItems(parsed.totalElements || 0);
            setLoading(false);
        } catch {
            // Ignore malformed cache entries and continue with network data.
        }
    }, [cacheKey, useSSRData]);

    const fetchData = useCallback(async () => {
        if (isFirstRender.current && useSSRData) {
            isFirstRender.current = false;
            setIsPending(false);
            return;
        }
        isFirstRender.current = false;

        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
        }

        const controller = new AbortController();
        abortControllerRef.current = controller;
        setLoading(true);

        try {
            const data = await discoveryClient.searchProjects({
                page,
                size: itemsPerPage,
                classification: selectedClassification !== 'All' ? selectedClassification : undefined,
                tags: selectedTags.join(','),
                search: urlSearchTerm,
                sort: sortBy,
                gameVersion: selectedVersion !== 'Any' ? selectedVersion : undefined,
                minDownloads: minDownloads > 0 ? minDownloads : undefined,
                minFavorites: minFavorites > 0 ? minFavorites : undefined,
                dateRange: filterDate || 'all',
                category: viewCategory !== 'all' ? viewCategory : undefined,
            }, controller.signal);

            const nextTotalPages = data?.totalPages || 0;
            const nextTotalItems = data?.totalElements || 0;

            if (nextTotalPages > 0 && page >= nextTotalPages) {
                setSearchParams(prev => {
                    const next = new URLSearchParams(prev);
                    const lastPage = nextTotalPages - 1;
                    if (lastPage === 0) next.delete('page');
                    else next.set('page', lastPage.toString());
                    return next;
                }, { replace: true });
                return;
            }

            setItems(data?.content || []);
            setTotalPages(nextTotalPages);
            setTotalItems(nextTotalItems);

            if (typeof window !== 'undefined') {
                window.sessionStorage.setItem(cacheKey, JSON.stringify({
                    content: data?.content || [],
                    totalPages: nextTotalPages,
                    totalElements: nextTotalItems,
                }));
            }
        } catch (err: any) {
            if (err.name !== 'Canceled') {
                void captureError(err);
                setItems([]);
                setTotalPages(0);
                setTotalItems(0);
            }
        } finally {
            if (!controller.signal.aborted) {
                setLoading(false);
                setIsPending(false);
            }
        }
    }, [page, itemsPerPage, selectedClassification, selectedTags, urlSearchTerm, sortBy, selectedVersion, minDownloads, minFavorites, filterDate, viewCategory, useSSRData, setSearchParams]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const updateParams = useCallback((updates: Record<string, string | null>) => {
        const next = new URLSearchParams(searchParams);
        Object.entries(updates).forEach(([key, value]) => {
            if (
                value === null ||
                (key === 'page' && value === '0') ||
                (key === 'sort' && value === 'relevance') ||
                (key === 'category' && value === 'all') ||
                (key === 'version' && value === 'Any') ||
                (key === 'minDl' && value === '0') ||
                (key === 'minFav' && value === '0')
            ) {
                next.delete(key);
            } else {
                next.set(key, value);
            }
        });
        if (!updates.hasOwnProperty('page')) {
            next.delete('page');
        }

        if (next.toString() === searchParams.toString()) {
            return;
        }

        setItems([]);
        setTotalPages(0);
        setTotalItems(0);
        setLoading(true);
        setIsPending(true);
        setSearchParams(next);
    }, [searchParams, setSearchParams]);

    return {
        page, sortBy, selectedVersion, minDownloads, minFavorites, filterDate, selectedTags, urlSearchTerm,
        viewCategory,
        searchTerm, setSearchTerm, selectedClassification, setSelectedClassification, totalPages, totalItems, loading, isPending, items, setItems,
        itemsPerPage, setItemsPerPage, updateParams, searchParams, setSearchParams
    };
};
