import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, ExternalLink, ChevronDown, ChevronRight } from 'lucide-react';
import { Spinner } from '@/components/ui/Spinner';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import { SidebarSection } from '@/modules/project/components/ProjectLayout';
import { theme } from '@/styles/theme';
export { useHMWiki } from '../hooks/useHMWiki';

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
                    type="button"
                    onClick={() => setIsOpen(!isOpen)}
                    className={`w-full flex items-center justify-between px-3 py-2 text-[10px] font-black uppercase tracking-widest ${theme.colors.textMuted} hover:${theme.colors.textPrimary} transition-colors ${depth > 0 ? 'mt-2' : ''}`}
                >
                    <span className="truncate pr-2">{node.title}</span>
                    {isOpen ? <ChevronDown className="w-3 h-3 shrink-0" /> : <ChevronRight className="w-3 h-3 shrink-0" />}
                </button>
                {isOpen && (
                    <ul className={`space-y-1 mt-1 ml-3 pl-3 border-l ${theme.colors.border}`}>
                        {node.children.map((child: any) => (
                            <WikiNode key={child.id} node={child} projectUrl={projectUrl} currentSlug={currentSlug} indexSlug={indexSlug} onNavigate={onNavigate} depth={depth + 1} isFirst={false} />
                        ))}
                    </ul>
                )}
            </li>
        );
    }

    const className = `block w-full text-left px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? 'bg-modtale-accent text-white' : `${theme.colors.textSecondary} ${theme.colors.bgSurfaceHover}`}`;

    return (
        <li key={node.id}>
            {onNavigate ? (
                <button type="button" onClick={() => { onNavigate(node.slug); const el = document.getElementById('wiki-preview-container'); if (el) window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - 120, behavior: 'smooth' }); }} className={className}>{node.title}</button>
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
                    <WikiNode key={p.id} node={p} projectUrl={projectUrl} currentSlug={currentSlug} indexSlug={indexSlug} onNavigate={onNavigate} depth={0} isFirst={idx === 0} />
                ))}
            </ul>
        </SidebarSection>
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
        <div id="wiki-preview-container" className="prose dark:prose-invert prose-lg max-w-none prose-code:before:hidden prose-code:after:hidden">
            {wikiData.content?.content ? (
                <>
                    <h1 className="text-4xl font-black mb-6">{wikiData.content.title || wikiData.mod.name}</h1>
                    <MarkdownRenderer content={wikiData.content.content} />
                </>
            ) : (
                <div className={`${theme.colors.textMuted} italic`}>Page content is empty.</div>
            )}
            <div className={`mt-16 pt-6 border-t ${theme.colors.border} flex flex-col sm:flex-row justify-between items-center gap-4 text-sm ${theme.colors.textMuted}`}>
                <div className="flex items-center gap-1.5 font-medium">
                    Powered by <a href="https://wiki.hytalemodding.dev" target="_blank" rel="noopener noreferrer" className={`${theme.colors.textSecondary} font-bold hover:${theme.colors.accent} transition-colors`}>HytaleModding</a>
                </div>
                <a href={`https://wiki.hytalemodding.dev/mod/${mod.hmWikiSlug}${wikiPageSlug && wikiPageSlug !== wikiData.mod.index?.slug ? `/${wikiPageSlug}` : ''}`} target="_blank" rel="noopener noreferrer" className={`flex items-center gap-1.5 font-bold ${theme.colors.bgSurfaceAlt} px-4 py-2 rounded-lg border ${theme.colors.border} hover:border-modtale-accent/30 transition-colors`}>
                    View on HytaleModding <ExternalLink className="w-4 h-4" />
                </a>
            </div>
        </div>
    );
};

export { Wiki as WikiContent };
