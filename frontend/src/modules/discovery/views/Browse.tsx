import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Helmet } from 'react-helmet-async';
import { useSearchParams, useLocation, Link } from 'react-router-dom';
import { Search, X, ChevronLeft, ChevronRight, CornerDownLeft, PackageSearch } from 'lucide-react';

import { useMobile } from '@/context/MobileContext';
import { useSSRData } from '@/context/SSRContext';
import { generateItemListSchema, generateBreadcrumbSchema, getBreadcrumbsForClassification } from '@/utils/schema';
import { getCategorySEO, generateDynamicSEO } from '@/data/seo-constants';
import { BROWSE_VIEWS, PROJECT_TYPES } from '@/data/categories';
import type { Classification } from '@/data/categories';
import { EmptyState } from '@/components/ui/EmptyState';

import { useProjectSearch } from '../hooks/useProjectSearch';
import { BrowseFilters } from '../components/BrowseFilters';
import { CategoryPillNav } from '../components/CategoryPillNav';
import { ProjectGrid } from '../components/ProjectGrid';
import { BrowseAdPlacement } from '../components/BrowseAdPlacement';
import { BrowseSkeletons } from '../components/BrowseSkeletons';

interface BrowseViewProps {
    likedProjectIds: string[];
    onToggleFavorite: (id: string) => void;
    isLoggedIn: boolean;
    initialClassification?: Classification;
}

export const Browse: React.FC<BrowseViewProps> = ({
                                                      likedProjectIds, onToggleFavorite, isLoggedIn, initialClassification
                                                  }) => {
    const [searchParams, setSearchParams] = useSearchParams();
    const location = useLocation();
    const { isMobile } = useMobile();
    const { initialData } = useSSRData();

    const [isMounted, setIsMounted] = useState(false);

    const hasComplexParams = searchParams.has('q') || searchParams.has('tags') || searchParams.has('version') || searchParams.has('minDl') || searchParams.has('minFav') || searchParams.has('date') || (searchParams.get('page') && parseInt(searchParams.get('page')!, 10) > 0);
    const hasUsableBrowseSSRData = Boolean(initialData?.browseData) && initialData?.browseDataReady !== false;
    const useSSR = hasUsableBrowseSSRData && !hasComplexParams;
    const canRenderHydratedContent = useSSR || isMounted;

    const {
        page, sortBy, activeViewId, selectedVersion, minDownloads, minFavorites, filterDate, selectedTags, urlSearchTerm,
        searchTerm, setSearchTerm, selectedClassification, setSelectedClassification, totalPages, totalItems, loading, isPending, items,
        itemsPerPage, setItemsPerPage, updateParams
    } = useProjectSearch(initialClassification || 'All', !!useSSR, useSSR ? initialData.browseData.content : [], useSSR ? initialData.browseData.totalPages : 0, useSSR ? initialData.browseData.totalElements : 0);

    const [viewStyle, setViewStyle] = useState<'grid' | 'list' | 'compact'>('grid');
    const [isFilterOpen, setIsFilterOpen] = useState(false);

    const [isScrolled, setIsScrolled] = useState(false);
    const isScrolledRef = useRef(false);
    const cardsSectionRef = useRef<HTMLDivElement>(null);
    const [jumpPage, setJumpPage] = useState('');

    useEffect(() => {
        if (typeof window === 'undefined') return;
        const query = searchParams.toString();
        const currentBrowseUrl = `${window.location.pathname}${query ? `?${query}` : ''}`;
        window.sessionStorage.setItem('modtale.lastBrowseUrl', currentBrowseUrl);
    }, [location.pathname, searchParams]);

    useEffect(() => {
        setIsMounted(true);
        const saved = typeof localStorage !== 'undefined' ? localStorage.getItem('modtale_view_style') : null;
        if (saved === 'grid' || saved === 'list' || saved === 'compact') {
            setViewStyle(saved as 'grid' | 'list' | 'compact');
        }
    }, []);

    useEffect(() => {
        setItemsPerPage(viewStyle === 'compact' ? 48 : 12);
    }, [viewStyle, setItemsPerPage]);

    const itemListSchema = useMemo(() => generateItemListSchema(items), [items]);
    const breadcrumbSchema = useMemo(() => {
        const crumbs = getBreadcrumbsForClassification(selectedClassification);
        return generateBreadcrumbSchema(crumbs);
    }, [selectedClassification]);

    const adContextProjectId = items.length > 0 ? items[0].id : '';
    useEffect(() => {
        let ticking = false;
        const handleScroll = () => {
            if (!ticking) {
                window.requestAnimationFrame(() => {
                    const curr = Math.max(0, window.scrollY);
                    if (curr > 300 && !isScrolledRef.current) {
                        isScrolledRef.current = true;
                        setIsScrolled(true);
                    } else if (curr <= 50 && isScrolledRef.current) {
                        isScrolledRef.current = false;
                        setIsScrolled(false);
                    }
                    ticking = false;
                });
                ticking = true;
            }
        };
        window.addEventListener('scroll', handleScroll, { passive: true });
        return () => window.removeEventListener('scroll', handleScroll);
    }, []);

    const handleScrollTop = useCallback(() => {
        window.scrollTo({ top: cardsSectionRef.current?.offsetTop ? cardsSectionRef.current.offsetTop - 120 : 0, behavior: 'smooth' });
    }, []);

    const handlePageChange = useCallback((p: number) => {
        if (p >= 0 && p < totalPages) {
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                if (p === 0) next.delete('page');
                else next.set('page', p.toString());
                return next;
            });
            handleScrollTop();
        }
    }, [totalPages, setSearchParams, handleScrollTop]);

    const handleViewStyleChange = useCallback((style: 'grid' | 'list' | 'compact') => {
        setViewStyle(style);
        if (typeof window !== 'undefined') localStorage.setItem('modtale_view_style', style);
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.delete('page');
            return next;
        });
    }, [setSearchParams]);

    const handleSortChange = useCallback((nextSort: string) => {
        updateParams({ sort: nextSort });
    }, [updateParams]);

    const getPageNumbers = () => {
        const total = totalPages;
        const current = page + 1;
        const delta = 2;
        const range = [];
        const dots: (number | string)[] = [];
        let l;
        range.push(1);
        for (let i = current - delta; i <= current + delta; i++) { if (i < total && i > 1) range.push(i); }
        if (total > 1) range.push(total);
        const unique = [...new Set(range)].sort((a, b) => a - b);
        for (const i of unique) {
            if (l) {
                if (i - l === 2) dots.push(l + 1);
                else if (i - l !== 1) dots.push('...');
            }
            dots.push(i);
            l = i;
        }
        return dots;
    };

    const getPageTitle = useCallback(() => {
        if (selectedTags.length > 0) return `Tagged: ${selectedTags[0]}${selectedTags.length > 1 ? ` (+${selectedTags.length - 1})` : ''}`;
        if (activeViewId === 'all') return selectedClassification === 'All' ? 'All Projects' : getCategorySEO(selectedClassification).h1 || `All ${PROJECT_TYPES.find(t=>t.id===selectedClassification)?.label}`;
        const view = BROWSE_VIEWS.find(v => v.id === activeViewId);
        if (view) return view.label;
        return 'Projects';
    }, [selectedTags, activeViewId, selectedClassification]);

    const seoContent = getCategorySEO(selectedClassification);
    const dynamicSEO = generateDynamicSEO({ title: seoContent.title, description: seoContent.description }, page, sortBy, activeViewId, urlSearchTerm);

    const getPageUrl = (targetPage: number) => {
        const s = new URLSearchParams(searchParams);
        if (targetPage === 0) s.delete('page');
        else s.set('page', targetPage.toString());
        const query = s.toString();
        return query ? `?${query}` : '?';
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-[#0B1120] text-slate-900 dark:text-slate-300 relative transition-colors duration-300">
            <Helmet>
                <title>{dynamicSEO.title}</title>
                <meta name="description" content={dynamicSEO.description} />
                <meta name="keywords" content={seoContent.keywords} />
                {itemListSchema && <script type="application/ld+json">{JSON.stringify(itemListSchema)}</script>}
                {breadcrumbSchema && <script type="application/ld+json">{JSON.stringify(breadcrumbSchema)}</script>}
            </Helmet>

            <main className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 pt-2 pb-8 transition-[max-width] duration-300">
                <h1 className="sr-only">{seoContent.h1}</h1>

                <div className="flex flex-col md:flex-row gap-8">
                    <div className="hidden md:block w-60 flex-shrink-0 z-30 sticky top-24 pt-3 h-fit space-y-4">
                        <div className="mb-6">
                            <div className="relative group h-11">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-500 pointer-events-none z-10 group-focus-within:text-modtale-accent transition-colors" />
                                <input type="text" className="block w-full pl-9 pr-9 h-full rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 text-sm shadow-sm focus:ring-2 focus:ring-modtale-accent outline-none text-slate-900 dark:text-white transition-all relative z-0" aria-label="Quick search" placeholder="Search projects..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
                                {searchTerm && <button type="button" onClick={() => setSearchTerm('')} className="absolute right-3 top-1/2 -translate-y-1/2 p-1 z-20" aria-label="Clear search"><X className="w-4 h-4 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors" /></button>}
                            </div>
                        </div>
                        <div className="p-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl shadow-sm animate-in fade-in slide-in-from-left-4 duration-700">
                            <span className="block text-xs font-black uppercase text-slate-500 dark:text-slate-400 mb-3 tracking-widest px-2 drop-shadow-sm">Browse</span>
                            <div className="space-y-1.5">
                                {BROWSE_VIEWS.map(v => {
                                    const s = new URLSearchParams(searchParams);
                                    if (v.id === 'all') s.delete('view'); else s.set('view', v.id);
                                    if (v.defaultSort === 'relevance') s.delete('sort');
                                    else s.set('sort', v.defaultSort);
                                    s.delete('page');
                                    const query = s.toString() ? `?${s.toString()}` : '?';

                                    return (
                                        <Link key={v.id} to={query} className={`block w-full text-left px-4 py-3 rounded-xl text-sm font-bold transition-all ${activeViewId === v.id ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5'}`}>
                                            <span className="pointer-events-none">{v.label}</span>
                                        </Link>
                                    );
                                })}
                            </div>
                        </div>
                        {adContextProjectId && (
                            <BrowseAdPlacement
                                projectId={adContextProjectId}
                                placement="TALL_BANNER"
                            />
                        )}
                    </div>

                    <div className="flex-1 min-h-[500px] min-w-0" ref={cardsSectionRef}>
                        <div className={`sticky top-24 z-50 mb-4 bg-slate-50/90 dark:bg-[#0B1120]/90 backdrop-blur-xl -mx-4 sm:mx-0 px-4 sm:px-0 border-b border-slate-200 dark:border-white/10 transition-all duration-300 ${isScrolled && isMobile ? 'pt-2 pb-2 shadow-sm' : 'pt-3 pb-3'}`}>
                            <BrowseFilters
                                categoryPills={<CategoryPillNav selectedClassification={selectedClassification} onClassificationChange={setSelectedClassification} currentSearchParams={searchParams} />}
                                pageTitle={getPageTitle()}
                                totalItems={totalItems}
                                loading={loading}
                                sortBy={sortBy}
                                onSortChange={handleSortChange}
                                selectedTags={selectedTags}
                                onToggleTag={(tag) => {
                                    const next = selectedTags.includes(tag) ? selectedTags.filter(t => t !== tag) : [...selectedTags, tag];
                                    updateParams({ tags: next.length ? next.join(',') : null });
                                }}
                                onClearTags={() => updateParams({ tags: null })}
                                activeFilterCount={0}
                                onResetFilters={() => updateParams({ version: null, minDl: null, minFav: null, date: null, tags: null })}
                                isFilterOpen={isFilterOpen}
                                onToggleFilterMenu={() => setIsFilterOpen(prev => !prev)}
                                searchTerm={searchTerm}
                                onSearchChange={setSearchTerm}
                                selectedVersion={selectedVersion}
                                setSelectedVersion={(v) => updateParams({ version: v !== 'Any' ? v : null })}
                                minFavorites={minFavorites}
                                setMinFavorites={(v) => updateParams({ minFav: v > 0 ? v.toString() : null })}
                                minDownloads={minDownloads}
                                setMinDownloads={(v) => updateParams({ minDl: v > 0 ? v.toString() : null })}
                                filterDate={filterDate}
                                setFilterDate={(v) => updateParams({ date: v })}
                                setPage={handlePageChange}
                                isMobile={isMobile}
                                viewStyle={viewStyle}
                                onViewStyleChange={handleViewStyleChange}
                                isScrolled={isScrolled}
                            />
                        </div>

                        {!canRenderHydratedContent || isPending ? (
                            <BrowseSkeletons viewStyle={viewStyle} count={itemsPerPage} />
                        ) : items.length > 0 ? (
                            <ProjectGrid items={items} loading={loading} viewStyle={viewStyle} itemsPerPage={itemsPerPage} likedProjectIds={likedProjectIds} onToggleFavorite={onToggleFavorite} isLoggedIn={isLoggedIn} />
                        ) : (
                            <div className="mt-8 animate-in fade-in zoom-in-95 duration-500">
                                <EmptyState icon={PackageSearch} title="No matches found" message="Try adjusting your search terms or filters to find what you're looking for." />
                            </div>
                        )}

                        {totalPages > 1 && canRenderHydratedContent && (
                            <nav aria-label="Pagination" className="mt-12 flex flex-col md:flex-row justify-center items-center gap-8 pb-12 animate-in fade-in slide-in-from-bottom-4 duration-700 delay-300">
                                <div className="rounded-2xl border border-modtale-accent/20 bg-white/80 dark:bg-[#0f172a]/80 backdrop-blur-xl px-2 py-2 flex items-center gap-1">
                                    {page === 0 ? (
                                        <span aria-disabled="true" className="w-9 h-9 flex items-center justify-center rounded-xl text-slate-400 opacity-30 cursor-not-allowed"><ChevronLeft className="w-4 h-4" /></span>
                                    ) : (
                                        <Link to={getPageUrl(page - 1)} rel="prev" aria-label="Previous Page" onClick={handleScrollTop} className="w-9 h-9 flex items-center justify-center rounded-xl text-slate-500 hover:text-modtale-accent hover:bg-modtale-accent/10 transition-all"><ChevronLeft className="w-4 h-4" /></Link>
                                    )}

                                    <div className="hidden sm:flex items-center gap-1">
                                        {getPageNumbers().map((p, idx) => typeof p === 'number' ? (
                                            <Link key={p} to={getPageUrl(p - 1)} onClick={handleScrollTop} aria-label={`Page ${p}`} aria-current={page === p - 1 ? 'page' : undefined} className={`w-9 h-9 flex items-center justify-center text-sm font-black rounded-xl transition-all ${page === p - 1 ? 'text-white bg-gradient-to-br from-modtale-accent to-sky-600' : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-white/5'}`}>
                                                {p}
                                            </Link>
                                        ) : (
                                            <span key={`dots-${idx}`} className="px-2 text-slate-400 text-xs font-black tracking-widest">...</span>
                                        ))}
                                    </div>

                                    {page === totalPages - 1 ? (
                                        <span aria-disabled="true" className="w-9 h-9 flex items-center justify-center rounded-xl text-slate-400 opacity-30 cursor-not-allowed"><ChevronRight className="w-4 h-4" /></span>
                                    ) : (
                                        <Link to={getPageUrl(page + 1)} rel="next" aria-label="Next Page" onClick={handleScrollTop} className="w-9 h-9 flex items-center justify-center rounded-xl text-slate-500 hover:text-modtale-accent hover:bg-modtale-accent/10 transition-all"><ChevronRight className="w-4 h-4" /></Link>
                                    )}
                                </div>
                                <form onSubmit={(e) => { e.preventDefault(); const p = parseInt(jumpPage); if (p >= 1 && p <= totalPages) handlePageChange(p - 1); setJumpPage(''); }} className="flex items-center gap-3 group rounded-2xl border border-slate-200 dark:border-white/10 px-3 py-2 bg-white/70 dark:bg-[#0f172a]/70 backdrop-blur">
                                    <label htmlFor="jump-page" className="text-[10px] font-black uppercase text-slate-400 tracking-[0.2em] pointer-events-none group-focus-within:text-modtale-accent transition-colors">Jump</label>
                                    <div className="relative flex items-center">
                                        <input id="jump-page" type="number" min={1} max={totalPages} value={jumpPage} onChange={(e) => setJumpPage(e.target.value)} className="w-14 h-8 rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900/70 text-center text-sm font-black text-slate-900 dark:text-white focus:border-modtale-accent focus:ring-2 focus:ring-modtale-accent/20 focus:outline-none transition-all [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none" placeholder="#" />
                                        <button type="submit" disabled={!jumpPage} aria-label="Go" className="ml-2 h-8 w-8 flex items-center justify-center rounded-lg text-slate-400 hover:text-white hover:bg-modtale-accent disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-slate-400 transition-all active:scale-90"><CornerDownLeft className="w-3.5 h-3.5" /></button>
                                    </div>
                                </form>
                            </nav>
                        )}
                    </div>
                </div>
            </main>
        </div>
    );
};
