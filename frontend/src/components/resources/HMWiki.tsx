// components/resources/HMWiki.tsx
import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, ExternalLink, ChevronDown, ChevronRight } from 'lucide-react';
import { api } from '@/utils/api';
import { Spinner } from '@/components/ui/Spinner';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import { SidebarSection } from '@/components/resources/ProjectLayout';

export const useHMWiki = (hmWikiSlug?: string, pageSlug?: string, enabled: boolean = false) => {
    const [modData, setModData] = useState<any>(null);
    const [content, setContent] = useState<any>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(false);

    useEffect(() => {
        if (!enabled || !hmWikiSlug) return;
        let isMounted = true;
        setError(false);

        api.get(`/wiki/${hmWikiSlug}`)
            .then(res => {
                if (isMounted) setModData(res.data);
            })
            .catch(() => {
                if (isMounted) setError(true);
            });

        return () => { isMounted = false; };
    }, [hmWikiSlug, enabled]);

    useEffect(() => {
        if (!enabled || !hmWikiSlug || !modData) return;

        let isMounted = true;
        setLoading(true);

        const targetSlug = pageSlug || modData.index?.slug || (modData.pages?.length > 0 ? modData.pages[0].slug : null);

        if (targetSlug) {
            api.get(`/wiki/${hmWikiSlug}/${targetSlug}`)
                .then(res => {
                    if (isMounted) setContent(res.data);
                })
                .catch(() => {
                    if (isMounted) setContent(null);
                })
                .finally(() => {
                    if (isMounted) setLoading(false);
                });
        } else {
            setContent(null);
            setLoading(false);
        }

        return () => { isMounted = false; };
    }, [hmWikiSlug, pageSlug, enabled, modData]);

    return {
        data: modData ? { mod: modData, content } : null,
        loading: loading || (enabled && !modData && !error),
        error
    };
};

const WikiNode: React.FC<{
    node: any;
    projectUrl: string;
    currentSlug?: string;
    indexSlug?: string;
    onNavigate?: (slug: string) => void;
    depth: number;
    isFirst: boolean;
}> = ({ node, projectUrl, currentSlug, indexSlug, onNavigate, depth, isFirst }) => {
    const hasChildren = node.children && node.children.length > 0;
    const [isOpen, setIsOpen] = useState(true);

    const isActive = !hasChildren && (currentSlug === node.slug || (!currentSlug && (indexSlug === node.slug || (isFirst && depth === 0))));

    if (hasChildren) {
        return (
            <li key={node.id}>
                <button
                    onClick={() => setIsOpen(!isOpen)}
                    className={`w-full flex items-center justify-between px-3 py-2 text-[10px] font-black uppercase tracking-widest text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 transition-colors ${depth > 0 ? 'mt-2' : ''}`}
                >
                    <span className="truncate pr-2">{node.title}</span>
                    {isOpen ? <ChevronDown className="w-3 h-3 shrink-0" /> : <ChevronRight className="w-3 h-3 shrink-0" />}
                </button>
                {isOpen && (
                    <ul className="space-y-1 mt-1 ml-3 pl-3 border-l border-slate-200 dark:border-white/10">
                        {node.children.map((child: any, idx: number) => (
                            <WikiNode
                                key={child.id}
                                node={child}
                                projectUrl={projectUrl}
                                currentSlug={currentSlug}
                                indexSlug={indexSlug}
                                onNavigate={onNavigate}
                                depth={depth + 1}
                                isFirst={false}
                            />
                        ))}
                    </ul>
                )}
            </li>
        );
    }

    const className = `block w-full text-left px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? 'bg-modtale-accent text-white' : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5'}`;

    return (
        <li key={node.id}>
            {onNavigate ? (
                <button onClick={() => {
                    onNavigate(node.slug);
                    const wikiContainer = document.getElementById('wiki-preview-container');
                    if (wikiContainer) {
                        const y = wikiContainer.getBoundingClientRect().top + window.scrollY - 120;
                        window.scrollTo({ top: y, behavior: 'smooth' });
                    }
                }} className={className}>{node.title}</button>
            ) : (
                <Link preventScrollReset={true} to={`${projectUrl}/wiki/${node.slug}`} className={className}>{node.title}</Link>
            )}
        </li>
    );
};

export const WikiSidebar: React.FC<{ tree: any[], projectUrl: string, currentSlug?: string, indexSlug?: string, onNavigate?: (slug: string) => void }> = ({ tree, projectUrl, currentSlug, indexSlug, onNavigate }) => {
    if (!tree || tree.length === 0) return null;

    return (
        <SidebarSection title="Wiki Navigation" icon={BookOpen} defaultOpen={true}>
            <ul className="space-y-1">
                {tree.map((p, idx) => (
                    <WikiNode
                        key={p.id}
                        node={p}
                        projectUrl={projectUrl}
                        currentSlug={currentSlug}
                        indexSlug={indexSlug}
                        onNavigate={onNavigate}
                        depth={0}
                        isFirst={idx === 0}
                    />
                ))}
            </ul>
        </SidebarSection>
    );
};

export const WikiContent: React.FC<{
    wikiLoading: boolean;
    wikiError: boolean;
    wikiData: any;
    wikiPageSlug?: string;
    mod: any;
}> = ({ wikiLoading, wikiError, wikiData, wikiPageSlug, mod }) => {
    if (wikiLoading) {
        return <div className="flex justify-center p-12"><Spinner /></div>;
    }

    if (wikiError || !wikiData) {
        return (
            <div className="text-center py-12 text-slate-500">
                <BookOpen className="w-12 h-12 mx-auto mb-4 opacity-50" />
                <h3 className="text-lg font-bold text-slate-700 dark:text-slate-300">No Wiki Available</h3>
                <p className="mt-2">This project does not have a valid HytaleModding wiki set up.</p>
            </div>
        );
    }

    return (
        <div id="wiki-preview-container" className="prose dark:prose-invert prose-lg max-w-none prose-code:before:hidden prose-code:after:hidden">
            {wikiData.content?.content ? (
                <>
                    <h1 className="text-4xl font-black mb-6">{wikiData.content.title || wikiData.mod.name}</h1>
                    <MarkdownRenderer content={wikiData.content.content} />
                </>
            ) : (
                <div className="text-slate-500 italic">Page content is empty.</div>
            )}

            <div className="mt-16 pt-6 border-t border-slate-200 dark:border-white/10 flex flex-col sm:flex-row justify-between items-center gap-4 text-sm text-slate-500">
                <div className="flex items-center gap-1.5 font-medium">
                    Powered by <a href="https://wiki.hytalemodding.dev" target="_blank" rel="noopener noreferrer" className="text-slate-700 dark:text-slate-300 font-bold hover:text-modtale-accent transition-colors">HytaleModding</a>
                </div>
                <a
                    href={`https://wiki.hytalemodding.dev/mod/${mod.hmWikiSlug}${wikiPageSlug && wikiPageSlug !== wikiData.mod.index?.slug ? `/${wikiPageSlug}` : ''}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1.5 hover:text-modtale-accent transition-colors font-bold bg-slate-100 dark:bg-white/5 px-4 py-2 rounded-lg border border-slate-200 dark:border-white/10 hover:border-modtale-accent/30"
                >
                    View on HytaleModding <ExternalLink className="w-4 h-4" />
                </a>
            </div>
        </div>
    );
};