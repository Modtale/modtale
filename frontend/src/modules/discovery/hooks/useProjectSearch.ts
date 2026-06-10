import { useState, useEffect, useRef, useCallback, useMemo, useLayoutEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { discoveryClient } from '../api/discoveryClient';
import { captureError } from '@/utils/errorTracking';
import type { Project } from '@/types';
import type { Classification } from '@/data/categories';

export type SortOption = 'relevance' | 'downloads' | 'favorites' | 'newest' | 'updated';

const normalizeSort = (sort: string | null): SortOption => {
    switch (sort) {
        case 'downloads':
        case 'favorites':
        case 'newest':
        case 'updated':
            return sort;
        default:
            return 'relevance';
    }
};

export const useProjectSearch = (initialClassification: Classification | 'All', useSSRData: boolean, initialItems: Project[], initialTotalPages: number, initialTotalItems: number) => {
    const [searchParams, setSearchParams] = useSearchParams();

    const parsedPage = parseInt(searchParams.get('page') || '0', 10);
    const page = Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : 0;
    const sortBy = normalizeSort(searchParams.get('sort'));
    const activeViewId = searchParams.get('view') || 'all';
    const selectedVersion = searchParams.get('version') || 'Any';
    const minDownloads = parseInt(searchParams.get('minDl') || '0');
    const minFavorites = parseInt(searchParams.get('minFav') || '0');
    const filterDate = searchParams.get('date');
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
    const [itemsPerPage, setItemsPerPage] = useState(12);

    const abortControllerRef = useRef<AbortController | null>(null);
    const isFirstRender = useRef(true);
    const previousQueryKeyRef = useRef<string | null>(null);

    useEffect(() => {
        if (urlSearchTerm !== searchTerm) {
            setSearchTerm(urlSearchTerm);
        }
    }, [urlSearchTerm]);

    useEffect(() => {
        setSelectedClassification(initialClassification);
    }, [initialClassification]);

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
        activeViewId,
    }), [page, itemsPerPage, selectedClassification, selectedTags, urlSearchTerm, sortBy, selectedVersion, minDownloads, minFavorites, filterDate, activeViewId]);

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
            let categoryParam: string | undefined = undefined;
            if (activeViewId === 'favorites') categoryParam = 'Favorites';
            else if (activeViewId === 'hidden_gems') categoryParam = 'hidden_gems';
            else if (activeViewId === 'popular') categoryParam = 'popular';
            else if (activeViewId === 'trending') categoryParam = 'trending';

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
                category: categoryParam,
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
    }, [page, itemsPerPage, selectedClassification, selectedTags, urlSearchTerm, sortBy, selectedVersion, minDownloads, minFavorites, filterDate, activeViewId, useSSRData, setSearchParams]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const updateParams = useCallback((updates: Record<string, string | null>) => {
        setIsPending(true);
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            Object.entries(updates).forEach(([key, value]) => {
                if (
                    value === null ||
                    (key === 'page' && value === '0') ||
                    (key === 'sort' && value === 'relevance') ||
                    (key === 'view' && value === 'all') ||
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
            return next;
        });
    }, [setSearchParams]);

    return {
        page, sortBy, activeViewId, selectedVersion, minDownloads, minFavorites, filterDate, selectedTags, urlSearchTerm,
        searchTerm, setSearchTerm, selectedClassification, setSelectedClassification, totalPages, totalItems, loading, isPending, items, setItems,
        itemsPerPage, setItemsPerPage, updateParams, searchParams, setSearchParams
    };
};
