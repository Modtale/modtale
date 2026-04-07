import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, ExternalLink } from 'lucide-react';
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

export const WikiSidebar: React.FC<{ tree: any[], projectUrl: string, currentSlug?: string, indexSlug?: string, onNavigate?: (slug: string) => void }> = ({ tree, projectUrl, currentSlug, indexSlug, onNavigate }) => {
    const renderNodes = (pages: any[]) => {
        return (
            <ul className="space-y-1">
                {pages.map(p => {
                    const isActive = currentSlug === p.slug || (!currentSlug && (indexSlug === p.slug || p.slug === tree[0]?.slug));
                    const className = `block w-full text-left px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? 'bg-modtale-accent text-white' : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5'}`;

                    return (
                        <li key={p.id}>
                            {onNavigate ? (
                                <button onClick={() => onNavigate(p.slug)} className={className}>{p.title}</button>
                            ) : (
                                <Link to={`${projectUrl}/wiki/${p.slug}`} className={className}>{p.title}</Link>
                            )}
                            {p.children && p.children.length > 0 && (
                                <div className="pl-3 mt-1 border-l border-slate-200 dark:border-white/10 ml-3">
                                    {renderNodes(p.children)}
                                </div>
                            )}
                        </li>
                    )
                })}
            </ul>
        );
    };

    if (!tree || tree.length === 0) return null;

    return (
        <SidebarSection title="Wiki Navigation" icon={BookOpen} defaultOpen={true}>
            {renderNodes(tree)}
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
        <div className="prose dark:prose-invert prose-lg max-w-none prose-code:before:hidden prose-code:after:hidden">
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