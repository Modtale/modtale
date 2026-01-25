import React, {useState, useRef, useEffect, useCallback, useMemo, type JSX} from 'react';
import { Helmet } from 'react-helmet-async';
import { useNavigate } from 'react-router-dom';
import type { Mod, Modpack, World } from '../types';
import { ModCard } from '../components/resources/ModCard';
import { HomeHero } from '../components/home/HomeHero';
import { HomeFilters } from '../components/home/HomeFilters';
import { Search, ChevronLeft, ChevronRight, CornerDownLeft, PackageSearch } from 'lucide-react';
import { api } from '../utils/api';
import { captureError } from '../utils/errorTracking';
import { PROJECT_TYPES, BROWSE_VIEWS } from '../data/categories';
import type { Classification } from '../data/categories';
import { getProjectUrl } from '../utils/slug';
import { EmptyState } from '../components/ui/EmptyState';
import { getCategorySEO } from '../data/seo-constants';
import { generateItemListSchema, generateBreadcrumbSchema, getBreadcrumbsForClassification } from '../utils/schema';
import { AdUnit } from '../components/ads/AdUnit';

interface HomeProps {
    onModClick: (mod: Mod) => void;
    onModpackClick: (modpack: Modpack) => void;
    onWorldClick: (world: World) => void;
    onAuthorClick: (author: string) => void;
    likedModIds: string[];
    onToggleFavoriteMod: (modId: string) => void;
    onToggleFavoriteModpack: (modpackId: string) => void;
    isLoggedIn: boolean;
    initialClassification?: Classification;
}

type SortOption = 'relevance' | 'downloads' | 'rating' | 'newest' | 'updated' | 'trending' | 'gems' | 'popular';

const getRouteForClassification = (cls: Classification | 'All') => {
    switch(cls) {
        case 'PLUGIN': return '/plugins';
        case 'MODPACK': return '/modpacks';
        case 'SAVE': return '/worlds';
        case 'ART': return '/art';
        case 'DATA': return '/data';
        default: return '/';
    }
};

export const Home: React.FC<HomeProps> = ({
                                              onModClick, onModpackClick, onWorldClick, onAuthorClick,
                                              likedModIds, onToggleFavoriteMod, onToggleFavoriteModpack, isLoggedIn,
                                              initialClassification
                                          }) => {
    const navigate = useNavigate();
    const [searchTerm, setSearchTerm] = useState('');
    const [debouncedSearch, setDebouncedSearch] = useState('');
    const [selectedClassification, setSelectedClassification] = useState<Classification | 'All'>(initialClassification || 'All');
    const [selectedTags, setSelectedTags] = useState<string[]>([]);
    const [selectedVersion, setSelectedVersion] = useState<string>('Any');
    const [minRating, setMinRating] = useState<number>(0);
    const [minDownloads, setMinDownloads] = useState<number>(0);
    const [filterDate, setFilterDate] = useState<string | null>(null);
    const [sortBy, setSortBy] = useState<SortOption>('relevance');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalItems, setTotalItems] = useState(0);
    const [loading, setLoading] = useState(false);
    const [items, setItems] = useState<(Mod | Modpack | World)[]>([]);
    const [jumpPage, setJumpPage] = useState('');
    const [activeViewId, setActiveViewId] = useState('all');
    const [showMiniSearch, setShowMiniSearch] = useState(false);
    const [isMobile, setIsMobile] = useState(false);
    const [isTopFilterOpen, setIsTopFilterOpen] = useState(false);
    const [itemsPerPage, setItemsPerPage] = useState(12);

    const abortControllerRef = useRef<AbortController | null>(null);
    const cardsSectionRef = useRef<HTMLDivElement>(null);

    const itemListSchema = useMemo(() => generateItemListSchema(items), [items]);

    const breadcrumbSchema = useMemo(() => {
        const crumbs = getBreadcrumbsForClassification(selectedClassification);
        return generateBreadcrumbSchema(crumbs);
    }, [selectedClassification]);

    useEffect(() => {
        const handler = setTimeout(() => {
            setDebouncedSearch(searchTerm);
        }, 500);
        return () => clearTimeout(handler);
    }, [searchTerm]);

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
            setIsMobile(width < 768);
            const threshold = width < 768 ? 200 : 260;
            setShowMiniSearch(window.scrollY > threshold);

            const isWide = width >= 1800;
            const targetSize = isWide ? 18 : 12;

            setItemsPerPage(prev => {
                if (prev !== targetSize) {
                    setPage(0);
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
    }, []);

    useEffect(() => {
        setPage(0);
    }, [selectedClassification, selectedTags, debouncedSearch, selectedVersion, minRating, minDownloads, filterDate, activeViewId, sortBy]);

    const fetchData = useCallback(async (targetPage: number) => {
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
                    page: targetPage,
                    size: itemsPerPage,
                    classification: selectedClassification !== 'All' ? selectedClassification : undefined,
                    tags: selectedTags.join(','),
                    search: debouncedSearch,
                    sort: sortBy,
                    gameVersion: selectedVersion !== 'Any' ? selectedVersion : undefined,
                    minRating: minRating > 0 ? minRating : undefined,
                    minDownloads: minDownloads > 0 ? minDownloads : undefined,
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
    }, [selectedClassification, selectedTags, debouncedSearch, sortBy, selectedVersion, minRating, minDownloads, filterDate, activeViewId, itemsPerPage]);

    useEffect(() => {
        fetchData(page);
    }, [fetchData, page]);

    const handleClassificationChange = (cls: Classification | 'All') => {
        if (cls !== selectedClassification) {
            const route = getRouteForClassification(cls);
            navigate(route);
            setActiveViewId('all');
            setSortBy('relevance');
        }
    };

    const handleViewChange = (viewId: string) => {
        setActiveViewId(viewId);
        if (viewId === 'hidden_gems') setSortBy('rating');
        else if (viewId === 'popular') setSortBy('popular');
        else if (viewId === 'trending') setSortBy('trending');
        else if (viewId === 'new') setSortBy('newest');
        else if (viewId === 'updated') setSortBy('updated');
        else setSortBy('relevance');
    };

    const handleSortChange = (newSort: any) => {
        const sortOption = newSort as SortOption;

        if (activeViewId === 'hidden_gems' || activeViewId === 'favorites') {
            setSortBy(sortOption);
            return;
        }

        if (sortOption === 'popular') {
            setActiveViewId('popular');
            setSortBy('popular');
        } else if (sortOption === 'trending') {
            setActiveViewId('trending');
            setSortBy('trending');
        } else if (sortOption === 'newest') {
            setActiveViewId('new');
            setSortBy('newest');
        } else if (sortOption === 'updated') {
            setActiveViewId('updated');
            setSortBy('updated');
        } else {
            if (['popular', 'trending', 'new', 'updated'].includes(activeViewId)) {
                setActiveViewId('all');
            }
            setSortBy(sortOption);
        }
    };

    const handlePageChange = (p: number) => { if (p >= 0 && p < totalPages) { setPage(p); window.scrollTo({ top: cardsSectionRef.current?.offsetTop ? cardsSectionRef.current.offsetTop - 120 : 0, behavior: 'smooth' }); } };
    const handleJump = (e: React.FormEvent) => { e.preventDefault(); const p = parseInt(jumpPage); if (!isNaN(p) && p >= 1 && p <= totalPages) { handlePageChange(p - 1); setJumpPage(''); } };

    const handleToggleLocal = (id: string, isModpack: boolean) => {
        if (!isLoggedIn) return;
        onToggleFavoriteMod(id);
        setItems(prev => prev.map(i => i.id === id ? { ...i, favoriteCount: (likedModIds.includes(id) ? Math.max(0, i.favoriteCount - 1) : i.favoriteCount + 1)} : i));
    };

    const getProjectPath = (item: Mod | Modpack | World) => {
        return getProjectUrl(item);
    };

    const getPageTitle = () => {
        if (selectedTags.length > 0) return `Tagged: ${selectedTags[0]}${selectedTags.length > 1 ? ` (+${selectedTags.length - 1})` : ''}`;
        if (activeViewId === 'all') return selectedClassification === 'All' ? 'All Projects' : `All ${PROJECT_TYPES.find(t=>t.id===selectedClassification)?.label}`;
        const view = BROWSE_VIEWS.find(v => v.id === activeViewId);
        if (view) return view.label;
        return 'Projects';
    };

    const getPageNumbers = () => { const total = totalPages; const current = page + 1; const delta = 2; const range = []; const rangeWithDots: (number | string)[] = []; let l; range.push(1); for (let i = current - delta; i <= current + delta; i++) { if (i < total && i > 1) { range.push(i); } } range.push(total); const uniqueRange = [...new Set(range)].sort((a, b) => a - b); for (const i of uniqueRange) { if (l) { if (i - l === 2) { rangeWithDots.push(l + 1); } else if (i - l !== 1) { rangeWithDots.push('...'); } } rangeWithDots.push(i); l = i; } return rangeWithDots; };

    const resetFilters = () => { setSelectedVersion('Any'); setMinRating(0); setMinDownloads(0); setFilterDate(null); setIsTopFilterOpen(false); setSelectedTags([]); setPage(0); }
    const activeFilterCount = (selectedVersion !== 'Any' ? 1 : 0) + (minRating > 0 ? 1 : 0) + (minDownloads > 0 ? 1 : 0) + (filterDate ? 1 : 0);
    const seoContent = getCategorySEO(selectedClassification);

    const renderItemsWithAds = () => {
        const elements: JSX.Element[] = [];
        items.forEach((item, index) => {
            elements.push(
                <div
                    key={item.id}
                    className="animate-in fade-in zoom-in-95 duration-500 fill-mode-backwards"
                    style={{ animationDelay: `${index * 50}ms` }}
                >
                    <ModCard
                        mod={item}
                        path={getProjectPath(item)}
                        isFavorite={likedModIds.includes(item.id)}
                        onToggleFavorite={(id) => handleToggleLocal(id, item.classification === 'MODPACK')}
                        isLoggedIn={isLoggedIn}
                        onClick={() => { if(item.classification === 'MODPACK') onModpackClick(item as Modpack); else if (item.classification === 'SAVE') onWorldClick(item as World); else onModClick(item as Mod); }}
                    />
                </div>
            );
            if (index === 5) {
                elements.push(
                    <div key="ad-unit-1" className="animate-in fade-in zoom-in-95 duration-500 delay-300">
                        <AdUnit variant="card" />
                    </div>
                );
            }
        });
        return elements;
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-slate-300">
            <Helmet>
                <title>{seoContent.title}</title>
                <meta name="description" content={seoContent.description} />
                <meta name="keywords" content={seoContent.keywords} />
                {itemListSchema && <script type="application/ld+json">{JSON.stringify(itemListSchema)}</script>}
                {breadcrumbSchema && <script type="application/ld+json">{JSON.stringify(breadcrumbSchema)}</script>}
            </Helmet>

            <HomeHero
                selectedClassification={selectedClassification}
                onClassificationChange={handleClassificationChange}
                searchTerm={searchTerm}
                onSearchChange={setSearchTerm}
                currentTypeLabel={PROJECT_TYPES.find(t => t.id === selectedClassification)?.label || 'Content'}
                showMiniSearch={showMiniSearch}
                seoH1={seoContent.h1 || undefined}
            />

            <main className="max-w-7xl min-[1800px]:max-w-[112rem] mx-auto px-4 sm:px-6 lg:px-8 py-4 transition-[max-width] duration-300">
                <div className="flex flex-col md:flex-row gap-8">
                    <div className="hidden md:block w-60 flex-shrink-0 z-30 sticky top-28 h-fit space-y-4">
                        <div className={`mb-6 transition-all duration-300 ${showMiniSearch ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-4 pointer-events-none'}`}>
                            <div className="relative group"><Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" /><input type="text" className="block w-full pl-9 pr-3 py-2 rounded-lg bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 text-sm shadow-sm" placeholder="Quick search..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} /></div>
                        </div>
                        <div className="p-4 bg-white/50 dark:bg-slate-800/50 rounded-2xl border border-slate-200/50 dark:border-white/5 animate-in fade-in slide-in-from-left-4 duration-700">
                            <h3 className="text-xs font-black uppercase text-slate-400 mb-3 tracking-widest px-2">Browse</h3>
                            <div className="space-y-1">
                                {BROWSE_VIEWS.map(v => (
                                    <button
                                        key={v.id}
                                        onClick={() => handleViewChange(v.id)}
                                        className={`w-full text-left px-4 py-3 rounded-lg text-sm font-bold transition-all ${activeViewId === v.id ? 'bg-modtale-accent text-white shadow-md' : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5'}`}
                                    >
                                        {v.label}
                                    </button>
                                ))}
                            </div>
                        </div>

                        <div className="mt-6">
                            <AdUnit variant="sidebar" />
                        </div>
                    </div>

                    <div className="flex-1 min-h-[500px]" ref={cardsSectionRef}>
                        <div className="sticky top-24 z-50 mb-4 bg-slate-50 dark:bg-slate-900 -mx-4 sm:-mx-6 lg:-mx-8 px-4 sm:px-6 lg:px-8 py-2 border-b border-slate-200/50 dark:border-white/5 transition-all">
                            <HomeFilters
                                pageTitle={getPageTitle()}
                                totalItems={totalItems}
                                loading={loading}
                                sortBy={sortBy}
                                onSortChange={handleSortChange}
                                selectedTags={selectedTags}
                                onToggleTag={(tag) => setSelectedTags(prev => prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag])}
                                onClearTags={() => setSelectedTags([])}
                                activeFilterCount={activeFilterCount}
                                onResetFilters={resetFilters}
                                isFilterOpen={isTopFilterOpen}
                                onToggleFilterMenu={() => setIsTopFilterOpen(!isTopFilterOpen)}
                                searchTerm={searchTerm}
                                onSearchChange={setSearchTerm}
                                selectedVersion={selectedVersion}
                                setSelectedVersion={setSelectedVersion}
                                minRating={minRating}
                                setMinRating={setMinRating}
                                minDownloads={minDownloads}
                                setMinDownloads={setMinDownloads}
                                filterDate={filterDate}
                                setFilterDate={setFilterDate}
                                setPage={setPage}
                                showMiniSearch={showMiniSearch}
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
                                {renderItemsWithAds()}
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

                        <div className="mt-12 mb-8">
                            <AdUnit variant="banner" className="max-w-4xl mx-auto" />
                        </div>

                        {totalPages > 1 && (
                            <div className="mt-12 flex flex-col md:flex-row justify-center items-center gap-4 pb-12 animate-in fade-in slide-in-from-bottom-4 duration-700 delay-300">
                                <div className="flex items-center gap-2">
                                    <button onClick={() => handlePageChange(page - 1)} disabled={page === 0} className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"><ChevronLeft className="w-5 h-5" /></button>
                                    <div className="hidden sm:flex gap-2">{getPageNumbers().map((p, idx) => (typeof p === 'number' ? ( <button key={p} onClick={() => handlePageChange(p - 1)} className={`w-10 h-10 rounded-lg text-sm font-bold border transition-colors ${page === p - 1 ? 'bg-modtale-accent text-white border-modtale-accent' : 'text-slate-600 dark:text-slate-400 border-transparent hover:bg-slate-100 dark:hover:bg-white/5'}`}>{p}</button> ) : ( <span key={`dots-${idx}`} className="w-10 h-10 flex items-center justify-center text-slate-400">...</span> )))}</div>
                                    <button onClick={() => handlePageChange(page + 1)} disabled={page === totalPages - 1} className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"><ChevronRight className="w-5 h-5" /></button>
                                </div>
                                <div className="hidden md:block w-px h-6 bg-slate-200 dark:bg-white/10"></div>
                                <form onSubmit={handleJump} className="flex items-center gap-2">
                                    <span className="text-xs font-bold text-slate-500 uppercase">Go to</span>
                                    <input type="number" min={1} max={totalPages} value={jumpPage} onChange={(e) => setJumpPage(e.target.value)} className="w-12 h-10 rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5 px-1 text-sm font-bold text-center dark:text-white focus:outline-none focus:ring-2 focus:ring-modtale-accent transition-all [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none" placeholder="#" />
                                    <button type="submit" disabled={!jumpPage} className="w-10 h-10 flex items-center justify-center rounded-lg bg-slate-100 dark:bg-white/5 hover:bg-modtale-accent hover:text-white dark:hover:bg-modtale-accent text-slate-500 dark:text-slate-400 transition-colors disabled:opacity-50"><CornerDownLeft className="w-4 h-4" /></button>
                                </form>
                            </div>
                        )}
                    </div>
                </div>
            </main>
        </div>
    );
};