import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, ExternalLink, ChevronDown, ChevronRight, ListTree, Search, X } from 'lucide-react';
import { Spinner } from '@/components/ui/Spinner';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import { SidebarSection } from '@/modules/project/components/ProjectLayout';
import { theme } from '@/styles/theme';
export { useHMWiki } from '../hooks/useHMWiki';

const nodeContainsDescendantSlug = (node: any, slug?: string): boolean => {
    if (!slug || !Array.isArray(node?.children)) return false;

    return node.children.some((child: any) => (
        child?.slug === slug || nodeContainsDescendantSlug(child, slug)
    ));
};

const isPageEmpty = (slug?: string, pageCache?: Record<string, any>): boolean => {
    if (!pageCache) return false;
    if (!slug) return true;
    if (!Object.prototype.hasOwnProperty.call(pageCache, slug)) return false;
    const page = pageCache[slug];
    if (!page) return true;
    return !page.content || typeof page.content !== 'string' || page.content.trim().length === 0;
};

const getWikiNodeTitle = (node: any): string => String(node?.title || node?.name || node?.slug || 'Untitled page');

const isNavigableWikiNode = (node: any, pageCache?: Record<string, any>): boolean => {
    if (!node?.slug) return false;
    const hasChildren = Array.isArray(node.children) && node.children.length > 0;
    return !(hasChildren && isPageEmpty(node.slug, pageCache));
};

type FlatWikiPage = {
    id: string;
    slug: string;
    title: string;
    parents: string[];
};

const flattenWikiPages = (nodes: any[] | undefined, pageCache?: Record<string, any>, parents: string[] = []): FlatWikiPage[] => {
    if (!Array.isArray(nodes)) return [];

    return nodes.flatMap((node) => {
        const title = getWikiNodeTitle(node);
        const self = isNavigableWikiNode(node, pageCache)
            ? [{ id: String(node.id || node.slug), slug: node.slug, title, parents }]
            : [];
        const children = flattenWikiPages(node.children, pageCache, [...parents, title]);

        return [...self, ...children];
    });
};

const WikiNode: React.FC<{
    node: any;
    projectUrl: string;
    currentSlug?: string;
    indexSlug?: string;
    onNavigate?: (slug: string) => void;
    onPrefetch?: (slug: string) => void;
    depth: number;
    isFirst: boolean;
    pageCache?: Record<string, any>;
}> = ({ node, projectUrl, currentSlug, indexSlug, onNavigate, onPrefetch, depth, isFirst, pageCache }) => {
    const hasChildren = node.children && node.children.length > 0;
    const isCategoryEmpty = hasChildren && (!node.slug || isPageEmpty(node.slug, pageCache));
    const isActive = currentSlug === node.slug || (!currentSlug && (indexSlug === node.slug || (isFirst && depth === 0 && node.slug)));
    const hasActiveDescendant = useMemo(() => hasChildren && nodeContainsDescendantSlug(node, currentSlug), [node, currentSlug, hasChildren]);
    const [isOpen, setIsOpen] = useState(true);
    const isBranchOpen = isOpen || hasActiveDescendant;

    useEffect(() => {
        if (hasActiveDescendant) {
            setIsOpen(true);
        }
    }, [hasActiveDescendant]);

    const className = `block w-full text-left px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? 'bg-modtale-accent text-white' : `${theme.colors.textSecondary} ${theme.colors.bgSurfaceHover}`}`;

    const navigateToNode = () => {
        if (!node.slug || !onNavigate) return;
        onNavigate(node.slug);
        const el = document.getElementById('wiki-preview-container');
        if (el) {
            window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - 120, behavior: 'smooth' });
        }
    };
    const prefetchNode = () => {
        if (node.slug) onPrefetch?.(node.slug);
    };

    if (hasChildren) {
        return (
            <li key={node.id}>
                <div className={`flex items-stretch gap-2 ${depth > 0 ? 'mt-2' : ''}`}>
                    {node.slug && !isCategoryEmpty ? (
                        onNavigate ? (
                            <button type="button" onClick={navigateToNode} onMouseEnter={prefetchNode} onFocus={prefetchNode} className={`flex-1 ${className}`}>{node.title}</button>
                        ) : (
                            <Link preventScrollReset={true} to={`${projectUrl}/wiki/${node.slug}`} onMouseEnter={prefetchNode} onFocus={prefetchNode} className={`flex-1 ${className}`}>{node.title}</Link>
                        )
                    ) : node.slug ? (
                        <div className={`flex-1 px-3 py-2 text-sm font-medium ${theme.colors.textMuted}`}>
                            <span className="truncate pr-2 block">{node.title}</span>
                        </div>
                    ) : (
                        <div className={`flex-1 px-3 py-2 text-[10px] font-black uppercase tracking-widest ${theme.colors.textMuted}`}>
                            <span className="truncate pr-2 block">{node.title}</span>
                        </div>
                    )}
                    <button
                        type="button"
                        onClick={() => setIsOpen(!isOpen)}
                        aria-expanded={isBranchOpen}
                        aria-label={`${isBranchOpen ? 'Collapse' : 'Expand'} ${node.title}`}
                        className={`shrink-0 rounded-lg px-2 ${isActive ? 'bg-modtale-accent text-white' : `${theme.colors.textMuted} ${theme.colors.bgSurfaceHover}`} transition-colors`}
                    >
                        {isBranchOpen ? <ChevronDown className="w-4 h-4 shrink-0" /> : <ChevronRight className="w-4 h-4 shrink-0" />}
                    </button>
                </div>
                {isBranchOpen && (
                    <ul className={`space-y-1 mt-1 ml-3 pl-3 border-l ${theme.colors.border}`}>
                        {node.children.map((child: any) => (
                            <WikiNode key={child.id} node={child} projectUrl={projectUrl} currentSlug={currentSlug} indexSlug={indexSlug} onNavigate={onNavigate} onPrefetch={onPrefetch} depth={depth + 1} isFirst={false} pageCache={pageCache} />
                        ))}
                    </ul>
                )}
            </li>
        );
    }

    return (
        <li key={node.id}>
            {onNavigate ? (
                <button type="button" onClick={navigateToNode} onMouseEnter={prefetchNode} onFocus={prefetchNode} className={className}>{node.title}</button>
            ) : (
                <Link preventScrollReset={true} to={`${projectUrl}/wiki/${node.slug}`} onMouseEnter={prefetchNode} onFocus={prefetchNode} className={className}>{node.title}</Link>
            )}
        </li>
    );
};

export const WikiSidebar: React.FC<{ tree: any[], projectUrl: string, currentSlug?: string, indexSlug?: string, onNavigate?: (slug: string) => void, onPrefetch?: (slug: string) => void, pageCache?: Record<string, any> }> = ({ tree, projectUrl, currentSlug, indexSlug, onNavigate, onPrefetch, pageCache }) => {
    if (!tree || tree.length === 0) return null;
    return (
        <SidebarSection title="Wiki Navigation" icon={BookOpen} defaultOpen={true}>
            <ul className="space-y-1">
                {tree.map((p, idx) => (
                    <WikiNode key={p.id} node={p} projectUrl={projectUrl} currentSlug={currentSlug} indexSlug={indexSlug} onNavigate={onNavigate} onPrefetch={onPrefetch} depth={0} isFirst={idx === 0} pageCache={pageCache} />
                ))}
            </ul>
        </SidebarSection>
    );
};

const WikiMobileTreeNode: React.FC<{
    node: any;
    projectUrl: string;
    activeSlug?: string;
    onNavigate?: (slug: string) => void;
    onPrefetch?: (slug: string) => void;
    onClose: () => void;
    depth: number;
    pageCache?: Record<string, any>;
}> = ({ node, projectUrl, activeSlug, onNavigate, onPrefetch, onClose, depth, pageCache }) => {
    const hasChildren = Array.isArray(node.children) && node.children.length > 0;
    const isActive = activeSlug === node.slug;
    const hasActiveDescendant = useMemo(() => hasChildren && nodeContainsDescendantSlug(node, activeSlug), [node, activeSlug, hasChildren]);
    const [isOpen, setIsOpen] = useState(hasActiveDescendant);
    const isBranchOpen = isOpen || hasActiveDescendant;
    const title = getWikiNodeTitle(node);
    const canNavigate = isNavigableWikiNode(node, pageCache);
    const rowClass = `flex min-h-11 min-w-0 items-center rounded-lg px-3 py-2.5 text-left text-sm font-bold transition-colors ${
        isActive ? 'bg-modtale-accent text-white shadow-sm' : `${theme.colors.textSecondary} hover:bg-slate-100 dark:hover:bg-white/5`
    }`;

    useEffect(() => {
        if (hasActiveDescendant) setIsOpen(true);
    }, [hasActiveDescendant]);

    const navigate = () => {
        if (!node.slug) return;
        if (onNavigate) onNavigate(node.slug);
        onClose();
    };
    const prefetchNode = () => {
        if (node.slug) onPrefetch?.(node.slug);
    };

    return (
        <li>
            <div className="flex items-stretch gap-1">
                {canNavigate ? (
                    onNavigate ? (
                        <button
                            type="button"
                            onClick={navigate}
                            onPointerDown={prefetchNode}
                            onMouseEnter={prefetchNode}
                            onFocus={prefetchNode}
                            className={`flex-1 ${rowClass}`}
                            style={{ paddingLeft: `${0.75 + depth * 0.75}rem` }}
                        >
                            <span className="min-w-0 flex-1 truncate">{title}</span>
                        </button>
                    ) : (
                        <Link
                            preventScrollReset={true}
                            to={`${projectUrl}/wiki/${node.slug}`}
                            onClick={onClose}
                            onPointerDown={prefetchNode}
                            onMouseEnter={prefetchNode}
                            onFocus={prefetchNode}
                            className={`flex-1 ${rowClass}`}
                            style={{ paddingLeft: `${0.75 + depth * 0.75}rem` }}
                        >
                            <span className="min-w-0 flex-1 truncate">{title}</span>
                        </Link>
                    )
                ) : (
                    <div
                        className={`flex-1 ${rowClass} ${isActive ? '' : theme.colors.textMuted}`}
                        style={{ paddingLeft: `${0.75 + depth * 0.75}rem` }}
                    >
                        <span className="min-w-0 flex-1 truncate">{title}</span>
                    </div>
                )}

                {hasChildren && (
                    <button
                        type="button"
                        onClick={() => setIsOpen(!isBranchOpen)}
                        aria-expanded={isBranchOpen}
                        aria-label={`${isBranchOpen ? 'Collapse' : 'Expand'} ${title}`}
                        className={`grid min-h-11 w-11 shrink-0 place-items-center rounded-lg transition-colors ${
                            isActive ? 'bg-modtale-accent text-white' : `${theme.colors.textMuted} hover:bg-slate-100 dark:hover:bg-white/5`
                        }`}
                    >
                        {isBranchOpen ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                    </button>
                )}
            </div>

            {hasChildren && isBranchOpen && (
                <ul className="mt-1 space-y-1">
                    {node.children.map((child: any) => (
                        <WikiMobileTreeNode
                            key={child.id || child.slug}
                            node={child}
                            projectUrl={projectUrl}
                            activeSlug={activeSlug}
                            onNavigate={onNavigate}
                            onPrefetch={onPrefetch}
                            onClose={onClose}
                            depth={depth + 1}
                            pageCache={pageCache}
                        />
                    ))}
                </ul>
            )}
        </li>
    );
};

export const WikiMobileNavigation: React.FC<{ tree: any[], projectUrl: string, currentSlug?: string, indexSlug?: string, onNavigate?: (slug: string) => void, onPrefetch?: (slug: string) => void, pageCache?: Record<string, any> }> = ({ tree, projectUrl, currentSlug, indexSlug, onNavigate, onPrefetch, pageCache }) => {
    const [isOpen, setIsOpen] = useState(false);
    const [query, setQuery] = useState('');
    const flatPages = useMemo(() => flattenWikiPages(tree, pageCache), [tree, pageCache]);
    const activeSlug = currentSlug || indexSlug || flatPages[0]?.slug;
    const activePage = flatPages.find((page) => page.slug === activeSlug);
    const normalizedQuery = query.trim().toLowerCase();
    const filteredPages = useMemo(() => {
        if (!normalizedQuery) return flatPages;

        return flatPages.filter((page) => (
            `${page.title} ${page.slug} ${page.parents.join(' ')}`.toLowerCase().includes(normalizedQuery)
        ));
    }, [flatPages, normalizedQuery]);

    if (!tree || tree.length === 0) return null;

    const close = () => {
        setIsOpen(false);
        setQuery('');
    };

    const navigateToSlug = (slug: string) => {
        onPrefetch?.(slug);
        if (onNavigate) onNavigate(slug);
        close();
    };

    const pageLinkClass = `block w-full rounded-lg px-3 py-2.5 text-left transition-colors ${
        theme.colors.textSecondary
    } hover:bg-slate-100 dark:hover:bg-white/5`;

    const renderPageResult = (page: FlatWikiPage) => (
        <li key={page.id}>
            {onNavigate ? (
                <button type="button" onClick={() => navigateToSlug(page.slug)} onPointerDown={() => onPrefetch?.(page.slug)} onMouseEnter={() => onPrefetch?.(page.slug)} onFocus={() => onPrefetch?.(page.slug)} className={pageLinkClass}>
                    <span className="block truncate text-sm font-bold">{page.title}</span>
                    {page.parents.length > 0 && (
                        <span className={`mt-0.5 block truncate text-[11px] font-medium ${theme.colors.textMuted}`}>{page.parents.join(' / ')}</span>
                    )}
                </button>
            ) : (
                <Link preventScrollReset={true} to={`${projectUrl}/wiki/${page.slug}`} onClick={close} onPointerDown={() => onPrefetch?.(page.slug)} onMouseEnter={() => onPrefetch?.(page.slug)} onFocus={() => onPrefetch?.(page.slug)} className={pageLinkClass}>
                    <span className="block truncate text-sm font-bold">{page.title}</span>
                    {page.parents.length > 0 && (
                        <span className={`mt-0.5 block truncate text-[11px] font-medium ${theme.colors.textMuted}`}>{page.parents.join(' / ')}</span>
                    )}
                </Link>
            )}
        </li>
    );

    return (
        <>
            {!isOpen && (
                <div className="fixed inset-x-4 bottom-4 z-[70] md:hidden">
                    <button
                        type="button"
                        onClick={() => setIsOpen(true)}
                        aria-label="Open wiki navigation"
                        className={`flex min-h-14 w-full items-center gap-3 rounded-2xl border ${theme.colors.border} bg-white/95 px-4 py-3 text-left shadow-2xl shadow-slate-950/20 backdrop-blur dark:bg-slate-900/95`}
                    >
                        <ListTree className={`h-5 w-5 shrink-0 ${theme.colors.accent}`} aria-hidden="true" />
                        <span className="min-w-0 flex-1">
                            <span className={`block text-[10px] font-black uppercase tracking-widest ${theme.colors.textMuted}`}>Wiki pages</span>
                            <span className={`block truncate text-sm font-bold ${theme.colors.textPrimary}`}>{activePage?.title || 'Browse wiki'}</span>
                        </span>
                        <ChevronDown className={`h-4 w-4 shrink-0 ${theme.colors.textMuted}`} aria-hidden="true" />
                    </button>
                </div>
            )}

            {isOpen && (
                <div className="md:hidden">
                    <button
                        type="button"
                        aria-label="Close wiki navigation overlay"
                        onClick={close}
                        className="fixed inset-0 z-[70] bg-slate-950/25 backdrop-blur-[1px]"
                    />
                    <section
                        role="dialog"
                        aria-label="Wiki pages"
                        className={`fixed inset-x-3 bottom-3 z-[80] overflow-hidden rounded-2xl border ${theme.colors.border} bg-white shadow-2xl shadow-slate-950/30 dark:bg-slate-900`}
                    >
                        <div className={`flex items-center gap-3 border-b ${theme.colors.border} px-4 py-3`}>
                            <ListTree className={`h-5 w-5 shrink-0 ${theme.colors.accent}`} aria-hidden="true" />
                            <div className="min-w-0 flex-1">
                                <h2 className={`truncate text-sm font-black ${theme.colors.textPrimary}`}>Wiki pages</h2>
                                <p className={`truncate text-xs ${theme.colors.textMuted}`}>{flatPages.length} pages</p>
                            </div>
                            <button
                                type="button"
                                onClick={close}
                                aria-label="Close wiki navigation"
                                className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${theme.colors.textMuted} hover:bg-slate-100 dark:hover:bg-white/5 transition-colors`}
                            >
                                <X className="h-5 w-5" aria-hidden="true" />
                            </button>
                        </div>

                        <div className="p-3">
                            <label className={`flex min-h-11 items-center gap-2 rounded-xl border ${theme.colors.border} ${theme.colors.bgSurfaceAlt} px-3`}>
                                <Search className={`h-4 w-4 shrink-0 ${theme.colors.textMuted}`} aria-hidden="true" />
                                <span className="sr-only">Search wiki pages</span>
                                <input
                                    value={query}
                                    onChange={(event) => setQuery(event.target.value)}
                                    placeholder="Search pages"
                                    className={`min-w-0 flex-1 bg-transparent py-2 text-sm font-semibold ${theme.colors.textPrimary} placeholder:text-slate-400 focus:outline-none dark:placeholder:text-slate-500`}
                                />
                            </label>
                        </div>

                        <div className="max-h-[min(68dvh,34rem)] overflow-y-auto overscroll-contain px-3 pb-3">
                            {normalizedQuery ? (
                                filteredPages.length > 0 ? (
                                    <ul className="space-y-1">
                                        {filteredPages.map(renderPageResult)}
                                    </ul>
                                ) : (
                                    <div className={`px-3 py-8 text-center text-sm ${theme.colors.textMuted}`}>No pages found.</div>
                                )
                            ) : (
                                <ul className="space-y-1">
                                    {tree.map((node) => (
                                        <WikiMobileTreeNode
                                            key={node.id || node.slug}
                                            node={node}
                                            projectUrl={projectUrl}
                                            activeSlug={activeSlug}
                                            onNavigate={onNavigate}
                                            onPrefetch={onPrefetch}
                                            onClose={close}
                                            depth={0}
                                            pageCache={pageCache}
                                        />
                                    ))}
                                </ul>
                            )}
                        </div>
                    </section>
                </div>
            )}
        </>
    );
};

export const Wiki: React.FC<{ wikiLoading: boolean; wikiError: boolean; wikiData: any; wikiPageSlug?: string; mod: any }> = ({ wikiLoading, wikiError, wikiData, wikiPageSlug, mod }) => {
    if (wikiLoading) return <div className="flex justify-center p-12"><Spinner /></div>;
    if (wikiError || !wikiData) {
        return (
            <div className="text-center py-12 text-slate-500">
                <BookOpen className="w-12 h-12 mx-auto mb-4 opacity-50" />
                <h3 className={`text-lg font-bold ${theme.colors.textSecondary}`}>No Wiki Available</h3>
                <p className="mt-2">This project does not have a valid HytaleModding wiki set up.</p>
            </div>
        );
    }

    return (
        <div id="wiki-preview-container" className="prose dark:prose-invert prose-base md:prose-lg max-w-none min-w-0 break-words pb-24 md:pb-0 prose-a:break-words prose-pre:max-w-full prose-pre:overflow-x-auto prose-code:before:hidden prose-code:after:hidden">
            {wikiData.content?.content ? (
                <>
                    <h1 className="text-3xl md:text-4xl font-black mb-6 break-words">{wikiData.content.title || wikiData.mod.name}</h1>
                    <MarkdownRenderer content={wikiData.content.content} deferRich fastOnly />
                </>
            ) : (
                <div className={`${theme.colors.textMuted} italic`}>Page content is empty.</div>
            )}
            <div className={`mt-16 pt-6 border-t ${theme.colors.border} flex flex-col sm:flex-row justify-between items-center gap-4 text-sm ${theme.colors.textMuted}`}>
                <div className="flex items-center gap-1.5 font-medium">
                    Powered by <a href="https://wiki.hytalemodding.dev" target="_blank" rel="noopener noreferrer" className={`${theme.colors.textSecondary} font-bold hover:${theme.colors.accent} transition-colors`}>HytaleModding</a>
                </div>
                <a href={`https://wiki.hytalemodding.dev/mod/${mod.hmWikiSlug}${wikiPageSlug && wikiPageSlug !== wikiData.mod.index?.slug ? `/${wikiPageSlug}` : ''}`} target="_blank" rel="noopener noreferrer" className={`flex w-full sm:w-auto items-center justify-center gap-1.5 text-center font-bold ${theme.colors.bgSurfaceAlt} px-4 py-2 rounded-lg border ${theme.colors.border} hover:border-modtale-accent/30 transition-colors`}>
                    View on HytaleModding <ExternalLink className="w-4 h-4" />
                </a>
            </div>
        </div>
    );
};

export { Wiki as WikiContent };
