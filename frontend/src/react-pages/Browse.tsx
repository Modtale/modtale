import React, { useState, useRef, useEffect, useCallback, useMemo, useLayoutEffect } from 'react';
import { Helmet } from 'react-helmet-async';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import type { Mod, Modpack, World } from '../types';
import { ModCard } from '../components/resources/ModCard';
import { HomeFilters } from '../components/home/HomeFilters';
import { Search, ChevronLeft, ChevronRight, CornerDownLeft, PackageSearch } from 'lucide-react';
import { api, BACKEND_URL } from '../utils/api';
import { captureError } from '../utils/errorTracking';
import { PROJECT_TYPES, BROWSE_VIEWS } from '../data/categories';
import type { Classification } from '../data/categories';
import { getProjectUrl } from '../utils/slug';
import { EmptyState } from '../components/ui/EmptyState';
import { getCategorySEO } from '../data/seo-constants';
import { generateItemListSchema, generateBreadcrumbSchema, getBreadcrumbsForClassification } from '../utils/schema';
import { useMobile } from '../context/MobileContext';

interface BrowseProps {
    onModClick: (mod: Mod) => void;
    onModpackClick: (modpack: Modpack) => void;
    onWorldClick: (world: World) => void;
    onAuthorClick: (authorId: string) => void;
    likedModIds: string[];
    onToggleFavoriteMod: (modId: string) => void;
    onToggleFavoriteModpack: (modpackId: string) => void;
    isLoggedIn: boolean;
    initialClassification?: Classification;
}

type SortOption = 'relevance' | 'downloads' | 'favorites' | 'newest' | 'updated' | 'trending' | 'gems' | 'popular';

const getRouteForClassification = (cls: Classification | 'All') => {
    switch(cls) {
        case 'PLUGIN': return '/plugins';
        case 'MODPACK': return '/modpacks';
        case 'SAVE': return '/worlds';
        case 'ART': return '/art';
        case 'DATA': return '/data';
        default: return '/mods';
    }
};

const getResolvedImageUrl = (url?: string) => {
    if (!url) return null;
    return url.startsWith('/api') ? `${BACKEND_URL}${url}` : url;
};

const CategoryPillNav: React.FC<{ selectedClassification: Classification | 'All', onClassificationChange: (cls: Classification | 'All') => void }> = ({ selectedClassification, onClassificationChange }) => {
    const tabsRef = useRef<(HTMLElement | null)[]>([]);
    const navContainerRef = useRef<HTMLDivElement>(null);
    const [pillStyle, setPillStyle] = useState({ left: 0, width: 0, opacity: 0 });
    const [showLeftFade, setShowLeftFade] = useState(false);
    const [showRightFade, setShowRightFade] = useState(false);

    const calculatePillPosition = useCallback(() => {
        const index = PROJECT_TYPES.findIndex(t => t.id === selectedClassification);
        if (index === -1) return;
        const el = tabsRef.current[index];
        if (el && el.offsetWidth > 0) {
            const wrapper = el.offsetParent as HTMLElement;
            const offsetLeft = el.offsetLeft + (wrapper?.offsetLeft || 0);
            setPillStyle({ left: offsetLeft, width: el.offsetWidth, opacity: 1 });
        }
    }, [selectedClassification]);

    const checkScroll = useCallback(() => {
        if (!navContainerRef.current) return;
        const { scrollLeft, scrollWidth, clientWidth } = navContainerRef.current;
        setShowLeftFade(scrollLeft > 0);
        setShowRightFade(scrollLeft + clientWidth < scrollWidth - 1);
    }, []);

    useLayoutEffect(() => {
        calculatePillPosition();
        checkScroll();
        const timer = setTimeout(() => { calculatePillPosition(); checkScroll(); }, 100);
        window.addEventListener('resize', calculatePillPosition);
        window.addEventListener('resize', checkScroll);

        const nav = navContainerRef.current;
        if(nav) nav.addEventListener('scroll', checkScroll);

        return () => {
            clearTimeout(timer);
            window.removeEventListener('resize', calculatePillPosition);
            window.removeEventListener('resize', checkScroll);
            if(nav) nav.removeEventListener('scroll', checkScroll);
        };
    }, [calculatePillPosition, checkScroll]);

    return (
        <div className="relative group flex-shrink-0 max-w-full flex items-center">
            <div className={`absolute left-0 top-0 bottom-0 w-8 bg-gradient-to-r from-slate-50 via-slate-50/80 to-transparent dark:from-slate-900 dark:via-slate-900/80 pointer-events-none z-30 transition-opacity duration-300 ${showLeftFade ? 'opacity-100' : 'opacity-0'}`} />
            <div className={`absolute right-0 top-0 bottom-0 w-12 bg-gradient-to-l from-slate-50 via-slate-50/80 to-transparent dark:from-slate-900 dark:via-slate-900/80 pointer-events-none z-30 transition-opacity duration-300 ${showRightFade ? 'opacity-100' : 'opacity-0'}`} />

            <div ref={navContainerRef} className="relative inline-flex h-10 bg-white dark:bg-slate-800 p-1 rounded-xl border border-slate-200 dark:border-white/10 max-w-full overflow-x-auto snap-x scrollbar-hide z-10 shadow-sm" style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}>
                <style>{` .scrollbar-hide::-webkit-scrollbar { display: none; } `}</style>
                <div className="absolute top-1 bottom-1 bg-slate-100 dark:bg-modtale-accent shadow-sm rounded-lg transition-all duration-300 ease-out z-0" style={{ left: pillStyle.left, width: pillStyle.width, opacity: pillStyle.opacity }} />
                <div className="flex relative z-10 h-full">
                    {PROJECT_TYPES.map((type, index) => {
                        const Icon = type.icon;
                        const isSelected = selectedClassification === type.id;
                        return (
                            <Link
                                key={type.id}
                                to={getRouteForClassification(type.id)}
                                onClick={() => onClassificationChange(type.id as any)}
                                ref={el => tabsRef.current[index] = el}
                                className={`px-3 md:px-4 h-full rounded-lg text-xs md:text-sm font-bold flex items-center justify-center gap-2 transition-colors duration-200 whitespace-nowrap snap-center ${isSelected ? 'text-slate-900 dark:text-white' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'}`}
                            >
                                <Icon className={`w-3.5 h-3.5 md:w-4 md:h-4 ${isSelected ? 'text-modtale-accent dark:text-white' : ''}`} />
                                <span className="inline">{type.label.replace(' Assets', '').replace('Server ', '')}</span>
                            </Link>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};

export const Browse: React.FC<BrowseProps> = ({
                                                  onModClick, onModpackClick, onWorldClick, onAuthorClick,
                                                  likedModIds, onToggleFavoriteMod, onToggleFavoriteModpack, isLoggedIn,
                                                  initialClassification
                                              }) => {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const { isMobile } = useMobile();

    const page = parseInt(searchParams.get('page') || '0');
    const sortBy = (searchParams.get('sort') as SortOption) || 'relevance';
    const activeViewId = searchParams.get('view') || 'all';
    const selectedVersion = searchParams.get('version') || 'Any';
    const minDownloads = parseInt(searchParams.get('minDl') || '0');
    const minFavorites = parseInt(searchParams.get('minFav') || '0');
    const filterDate = searchParams.get('date');
    const rawTags = searchParams.get('tags');
    const selectedTags = useMemo(() => rawTags ? rawTags.split(',').filter(Boolean) : [], [rawTags]);
    const urlSearchTerm = searchParams.get('q') || '';

    const [searchTerm, setSearchTerm] = useState(urlSearchTerm);

    const [selectedClassification, setSelectedClassification] = useState<Classification | 'All'>(initialClassification || 'All');
    const [totalPages, setTotalPages] = useState(0);
    const [totalItems, setTotalItems] = useState(0);
    const [loading, setLoading] = useState(false);
    const [items, setItems] = useState<(Mod | Modpack | World)[]>([]);
    const [jumpPage, setJumpPage] = useState('');
    const [isTopFilterOpen, setIsTopFilterOpen] = useState(false);
    const [itemsPerPage, setItemsPerPage] = useState(12);

    const abortControllerRef = useRef<AbortController | null>(null);
    const cardsSectionRef = useRef<HTMLDivElement>(null);

    const itemListSchema = useMemo(() => generateItemListSchema(items), [items]);

    const breadcrumbSchema = useMemo(() => {
        const crumbs = getBreadcrumbsForClassification(selectedClassification);
        return generateBreadcrumbSchema(crumbs);
    }, [selectedClassification]);

    const lcpBannerUrl = useMemo(() => {
        if (items.length > 0 && items[0].bannerUrl) {
            return getResolvedImageUrl(items[0].bannerUrl);
        }
        return null;
    }, [items]);

    useEffect(() => {
        if (urlSearchTerm !== searchTerm) {
            setSearchTerm(urlSearchTerm);
        }
    }, [urlSearchTerm]);

    useEffect(() => {
        const handler = setTimeout(() => {
            if (searchTerm !== urlSearchTerm) {
                setSearchParams(prev => {
                    const next = new URLSearchParams(prev);
                    if (searchTerm) {
                        next.set('q', searchTerm);
                    } else {
                        next.delete('q');
                    }
                    next.set('page', '0');
                    return next;
                }, { replace: true });
            }
        }, 500);
        return () => clearTimeout(handler);
    }, [searchTerm, urlSearchTerm, setSearchParams]);

    useEffect(() => {
        if (initialClassification) {
            setSelectedClassification(initialClassification);
        } else {
            setSelectedClassification('All');
        }
    }, [initialClassification]);

    useEffect(() => {
        const handleResize = () => {
            const width = window.innerWidth;
            const isWide = width >= 1800;
            const targetSize = isWide ? 18 : 12;

            setItemsPerPage(prev => {
                if (prev !== targetSize) {
                    setSearchParams(prevParams => {
                        const next = new URLSearchParams(prevParams);
                        next.set('page', '0');
                        return next;
                    }, { replace: true });
                    return targetSize;
                }
                return prev;
            });
        };

        handleResize();
        window.addEventListener('scroll', handleResize);
        window.addEventListener('resize', handleResize);
        return () => {
            window.removeEventListener('scroll', handleResize);
            window.removeEventListener('resize', handleResize);
        };
    }, [setSearchParams]);

    useEffect(() => {
        if (cardsSectionRef.current && page === 0) {
            const offset = cardsSectionRef.current.offsetTop - 120;
            if (window.scrollY > offset) {
                window.scrollTo({ top: offset, behavior: 'smooth' });
            }
        }
    }, [selectedClassification, selectedTags, urlSearchTerm, selectedVersion, minFavorites, minDownloads, filterDate, activeViewId, sortBy]);

    const fetchData = useCallback(async () => {
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

            const res = await api.get('/projects', {
                params: {
                    page: page,
                    size: itemsPerPage,
                    classification: selectedClassification !== 'All' ? selectedClassification : undefined,
                    tags: selectedTags.join(','),
                    search: urlSearchTerm,
                    sort: sortBy,
                    gameVersion: selectedVersion !== 'Any' ? selectedVersion : undefined,
                    minRating: undefined,
                    minDownloads: minDownloads > 0 ? minDownloads : undefined,
                    minFavorites: minFavorites > 0 ? minFavorites : undefined,
                    dateRange: filterDate || 'all',
                    category: categoryParam,
                },
                signal: controller.signal
            });

            setItems(res.data?.content || []);
            setTotalPages(res.data?.totalPages || 0);
            setTotalItems(res.data?.totalElements || 0);

        } catch (err: any) {
            if (err.name !== 'Canceled') {
                captureError(err);
                setItems([]);
                setTotalPages(0);
            }
        } finally {
            if (!controller.signal.aborted) {
                setLoading(false);
            }
        }
    }, [page, itemsPerPage, selectedClassification, selectedTags, urlSearchTerm, sortBy, selectedVersion, minDownloads, minFavorites, filterDate, activeViewId]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const updateParams = useCallback((updates: Record<string, string | null>) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            Object.entries(updates).forEach(([key, value]) => {
                if (value === null) {
                    next.delete(key);
                } else {
                    next.set(key, value);
                }
            });
            if (!updates.hasOwnProperty('page')) {
                next.set('page', '0');
            }
            return next;
        });
    }, [setSearchParams]);

    const handleClassificationChange = useCallback((cls: Classification | 'All') => {
        if (cls !== selectedClassification) {
            const route = getRouteForClassification(cls);
            navigate(route);
        }
    }, [selectedClassification, navigate]);

    const handleSortChange = useCallback((newSort: any) => {
        const sortOption = newSort as SortOption;
        const updates: Record<string, string | null> = { sort: sortOption };

        if (activeViewId === 'hidden_gems' || activeViewId === 'favorites') {
        } else {
            if (sortOption === 'popular') updates.view = 'popular';
            else if (sortOption === 'trending') updates.view = 'trending';
            else if (sortOption === 'newest') updates.view = 'new';
            else if (sortOption === 'updated') updates.view = 'updated';
            else {
                if (['popular', 'trending', 'new', 'updated'].includes(activeViewId)) {
                    updates.view = 'all';
                }
            }
        }
        updateParams(updates);
    }, [activeViewId, updateParams]);

    const handleScrollTop = useCallback(() => {
        window.scrollTo({
            top: cardsSectionRef.current?.offsetTop ? cardsSectionRef.current.offsetTop - 120 : 0,
            behavior: 'smooth'
        });
    }, []);

    const handlePageChange = useCallback((p: number) => {
        if (p >= 0 && p < totalPages) {
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                next.set('page', p.toString());
                return next;
            });
            handleScrollTop();
        }
    }, [totalPages, setSearchParams, handleScrollTop]);

    const handleJump = (e: React.FormEvent) => {
        e.preventDefault();
        const p = parseInt(jumpPage);
        if (!isNaN(p) && p >= 1 && p <= totalPages) {
            handlePageChange(p - 1);
            setJumpPage('');
        }
    };

    const handleToggleLocal = useCallback((id: string, isModpack: boolean) => {
        if (!isLoggedIn) return;
        onToggleFavoriteMod(id);
        setItems(prev => prev.map(i => i.id === id ? { ...i, favoriteCount: (likedModIds.includes(id) ? Math.max(0, i.favoriteCount - 1) : i.favoriteCount + 1)} : i));
    }, [isLoggedIn, onToggleFavoriteMod, likedModIds]);

    const getProjectPath = (item: Mod | Modpack | World) => {
        return getProjectUrl(item);
    };

    const getPageTitle = useCallback(() => {
        if (selectedTags.length > 0) return `Tagged: ${selectedTags[0]}${selectedTags.length > 1 ? ` (+${selectedTags.length - 1})` : ''}`;
        if (activeViewId === 'all') return selectedClassification === 'All' ? 'All Projects' : `All ${PROJECT_TYPES.find(t=>t.id===selectedClassification)?.label}`;
        const view = BROWSE_VIEWS.find(v => v.id === activeViewId);
        if (view) return view.label;
        return 'Projects';
    }, [selectedTags, activeViewId, selectedClassification]);

    const getPageNumbers = () => { const total = totalPages; const current = page + 1; const delta = 2; const range = []; const rangeWithDots: (number | string)[] = []; let l; range.push(1); for (let i = current - delta; i <= current + delta; i++) { if (i < total && i > 1) { range.push(i); } } range.push(total); const uniqueRange = [...new Set(range)].sort((a, b) => a - b); for (const i of uniqueRange) { if (l) { if (i - l === 2) { rangeWithDots.push(l + 1); } else if (i - l !== 1) { rangeWithDots.push('...'); } } rangeWithDots.push(i); l = i; } return rangeWithDots; };

    const resetFilters = useCallback(() => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.delete('version');
            next.delete('minDl');
            next.delete('minFav');
            next.delete('date');
            next.delete('tags');
            next.set('page', '0');
            return next;
        });
        setIsTopFilterOpen(false);
    }, [setSearchParams]);

    const createViewUrl = (viewId: string) => {
        const search = new URLSearchParams(searchParams);
        search.set('view', viewId);
        if (viewId === 'hidden_gems') search.set('sort', 'favorites');
        else if (viewId === 'popular') search.set('sort', 'popular');
        else if (viewId === 'trending') search.set('sort', 'trending');
        else if (viewId === 'new') search.set('sort', 'newest');
        else if (viewId === 'updated') search.set('sort', 'updated');
        else search.set('sort', 'relevance');
        search.delete('page');
        return `?${search.toString()}`;
    };

    const createPageUrl = (p: number) => {
        const search = new URLSearchParams(searchParams);
        search.set('page', p.toString());
        return `?${search.toString()}`;
    };

    const activeFilterCount = (selectedVersion !== 'Any' ? 1 : 0) + (minDownloads > 0 ? 1 : 0) + (minFavorites > 0 ? 1 : 0) + (filterDate ? 1 : 0);
    const seoContent = getCategorySEO(selectedClassification);

    const categoryPills = <CategoryPillNav selectedClassification={selectedClassification} onClassificationChange={handleClassificationChange} />;

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-slate-300 relative">
            <Helmet>
                <title>{seoContent.title}</title>
                <meta name="description" content={seoContent.description} />
                <meta name="keywords" content={seoContent.keywords} />
                {lcpBannerUrl && <link rel="preload" as="image" href={lcpBannerUrl} fetchPriority="high" />}
                {itemListSchema && <script type="application/ld+json">{JSON.stringify(itemListSchema)}</script>}
                {breadcrumbSchema && <script type="application/ld+json">{JSON.stringify(breadcrumbSchema)}</script>}
            </Helmet>

            <main className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 pt-4 pb-8 transition-[max-width] duration-300">
                <div className="flex flex-col md:flex-row gap-8">
                    <div className="hidden md:block w-60 flex-shrink-0 z-30 sticky top-24 pt-3 h-fit space-y-4">
                        <div className="mb-6">
                            <div className="relative group">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-500" />
                                <input type="text" className="block w-full pl-9 pr-3 h-10 rounded-lg bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 text-sm shadow-sm focus:ring-2 focus:ring-modtale-accent focus:border-modtale-accent outline-none text-slate-900 dark:text-white" aria-label="Quick search" placeholder="Search projects..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
                            </div>
                        </div>
                        <div className="p-4 bg-white/50 dark:bg-slate-800/50 rounded-2xl border border-slate-200/50 dark:border-white/5 animate-in fade-in slide-in-from-left-4 duration-700">
                            <h2 className="text-xs font-black uppercase text-slate-400 mb-3 tracking-widest px-2">Browse</h2>
                            <div className="space-y-1">
                                {BROWSE_VIEWS.map(v => (
                                    <Link
                                        key={v.id}
                                        to={createViewUrl(v.id)}
                                        className={`block w-full text-left px-4 py-3 rounded-lg text-sm font-bold transition-all ${activeViewId === v.id ? 'bg-modtale-accent text-white shadow-md' : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5'}`}
                                    >
                                        {v.label}
                                    </Link>
                                ))}
                            </div>
                        </div>
                    </div>

                    <div className="flex-1 min-h-[500px]" ref={cardsSectionRef}>
                        <div className="sticky top-24 z-50 mb-4 bg-slate-50 dark:bg-slate-900 -mx-4 sm:mx-0 px-4 sm:px-0 pt-3 pb-3 border-b border-slate-200/50 dark:border-white/5 transition-all">
                            <HomeFilters
                                categoryPills={categoryPills}
                                pageTitle={getPageTitle()}
                                totalItems={totalItems}
                                loading={loading}
                                sortBy={sortBy}
                                onSortChange={handleSortChange}
                                selectedTags={selectedTags}
                                onToggleTag={useCallback((tag: string) => {
                                    const newTags = selectedTags.includes(tag)
                                        ? selectedTags.filter(t => t !== tag)
                                        : [...selectedTags, tag];
                                    updateParams({ tags: newTags.length > 0 ? newTags.join(',') : null });
                                }, [selectedTags, updateParams])}
                                onClearTags={useCallback(() => updateParams({ tags: null }), [updateParams])}
                                activeFilterCount={activeFilterCount}
                                onResetFilters={resetFilters}
                                isFilterOpen={isTopFilterOpen}
                                onToggleFilterMenu={useCallback(() => setIsTopFilterOpen(prev => !prev), [])}
                                searchTerm={searchTerm}
                                onSearchChange={setSearchTerm}
                                selectedVersion={selectedVersion}
                                setSelectedVersion={useCallback((v: string) => updateParams({ version: v !== 'Any' ? v : null }), [updateParams])}
                                minFavorites={minFavorites}
                                setMinFavorites={useCallback((v: number) => updateParams({ minFav: v > 0 ? v.toString() : null }), [updateParams])}
                                minDownloads={minDownloads}
                                setMinDownloads={useCallback((v: number) => updateParams({ minDl: v > 0 ? v.toString() : null }), [updateParams])}
                                filterDate={filterDate}
                                setFilterDate={useCallback((v: string | null) => updateParams({ date: v }), [updateParams])}
                                setPage={handlePageChange}
                                isMobile={isMobile}
                            />
                        </div>

                        {loading ? (
                            <div className="grid grid-cols-1 md:grid-cols-2 min-[1800px]:grid-cols-3 gap-4 md:gap-5 mt-4">
                                {[...Array(itemsPerPage)].map((_, i) => (
                                    <div
                                        key={i}
                                        className="h-[154px] bg-white dark:bg-modtale-card rounded-xl animate-pulse border border-slate-200 dark:border-white/5 relative overflow-hidden"
                                    >
                                        <div className="absolute inset-0 -translate-x-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-white/10 to-transparent"></div>
                                    </div>
                                ))}
                            </div>
                        ) : items.length > 0 ? (
                            <div className="grid grid-cols-1 md:grid-cols-2 min-[1800px]:grid-cols-3 gap-4 md:gap-5 mt-4">
                                {items.map((item, index) => {
                                    const isPriority = index < 6;
                                    return (
                                        <div
                                            key={item.id}
                                            className={isPriority ? "" : "animate-in fade-in zoom-in-95 duration-500 fill-mode-backwards"}
                                            style={isPriority ? {} : { animationDelay: `${(index - 6) * 50}ms` }}
                                        >
                                            <ModCard
                                                mod={item}
                                                path={getProjectPath(item)}
                                                isFavorite={likedModIds.includes(item.id)}
                                                onToggleFavorite={() => handleToggleLocal(item.id, item.classification === 'MODPACK')}
                                                isLoggedIn={isLoggedIn}
                                                onClick={() => { if(item.classification === 'MODPACK') onModpackClick(item as Modpack); else if (item.classification === 'SAVE') onWorldClick(item as World); else onModClick(item as Mod); }}
                                                priority={isPriority}
                                            />
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <div className="mt-8 animate-in fade-in zoom-in-95 duration-500">
                                <EmptyState
                                    icon={PackageSearch}
                                    title="No matches found"
                                    message="Try adjusting your search terms or filters to find what you're looking for."
                                />
                            </div>
                        )}

                        {totalPages > 1 && (
                            <div className="mt-12 flex flex-col md:flex-row justify-center items-center gap-4 pb-12 animate-in fade-in slide-in-from-bottom-4 duration-700 delay-300">
                                <div className="flex items-center gap-2">
                                    {page === 0 ? (
                                        <button disabled aria-label="Previous page" className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-400 opacity-50 cursor-not-allowed transition-colors"><ChevronLeft className="w-5 h-5" /></button>
                                    ) : (
                                        <Link to={createPageUrl(page - 1)} onClick={handleScrollTop} aria-label="Previous page" className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5 transition-colors"><ChevronLeft className="w-5 h-5" /></Link>
                                    )}

                                    <div className="hidden sm:flex gap-2">
                                        {getPageNumbers().map((p, idx) => (
                                            typeof p === 'number' ? (
                                                <Link key={p} to={createPageUrl(p - 1)} onClick={handleScrollTop} aria-label={`Page ${p}`} className={`w-10 h-10 flex items-center justify-center rounded-lg text-sm font-bold border transition-colors ${page === p - 1 ? 'bg-modtale-accent text-white border-modtale-accent' : 'text-slate-600 dark:text-slate-400 border-transparent hover:bg-slate-100 dark:hover:bg-white/5'}`}>{p}</Link>
                                            ) : (
                                                <span key={`dots-${idx}`} className="w-10 h-10 flex items-center justify-center text-slate-400">...</span>
                                            )
                                        ))}
                                    </div>

                                    {page === totalPages - 1 ? (
                                        <button disabled aria-label="Next page" className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-400 opacity-50 cursor-not-allowed transition-colors"><ChevronRight className="w-5 h-5" /></button>
                                    ) : (
                                        <Link to={createPageUrl(page + 1)} onClick={handleScrollTop} aria-label="Next page" className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5 transition-colors"><ChevronRight className="w-5 h-5" /></Link>
                                    )}
                                </div>
                                <div className="hidden md:block w-px h-6 bg-slate-200 dark:bg-white/10"></div>
                                <form onSubmit={handleJump} className="flex items-center gap-2">
                                    <span className="text-xs font-bold text-slate-600 dark:text-slate-400 uppercase">Go to</span>
                                    <input type="number" min={1} max={totalPages} value={jumpPage} aria-label="Jump to page" onChange={(e) => setJumpPage(e.target.value)} className="w-12 h-10 rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5 px-1 text-sm font-bold text-center dark:text-white focus:outline-none focus:ring-2 focus:ring-modtale-accent transition-all [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none" placeholder="#" />
                                    <button type="submit" disabled={!jumpPage} aria-label="Go to page" className="w-10 h-10 flex items-center justify-center rounded-lg bg-slate-100 dark:bg-white/5 hover:bg-modtale-accent hover:text-white dark:hover:bg-modtale-accent text-slate-500 dark:text-slate-400 transition-colors disabled:opacity-50"><CornerDownLeft className="w-4 h-4" /></button>
                                </form>
                            </div>
                        )}
                    </div>
                </div>
            </main>
        </div>
    );
};