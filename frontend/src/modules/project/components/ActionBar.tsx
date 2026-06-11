import React, { useRef, useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Download, BookOpen, Image as ImageIcon, List, MessageSquare, ChevronDown, ExternalLink, Link as LinkIcon } from 'lucide-react';
import { theme } from '@/styles/theme';
import type { Project } from '@/types';

interface ActionBarProps {
    project: Project;
    projectUrl: string;
    links: { type: string, url: string, icon: any, label: string, colorClass: string }[];
    commentsRef: React.RefObject<HTMLDivElement | null>;
}

export const ActionBar: React.FC<ActionBarProps> = ({ project, projectUrl, links, commentsRef }) => {
    const [showMobileLinks, setShowMobileLinks] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const location = useLocation();

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) setShowMobileLinks(false);
        };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    const hasWiki = Boolean(project.hmWikiEnabled && project.hmWikiSlug);
    const hasGallery = Boolean(project.galleryImages && project.galleryImages.length > 0);
    const mediaButtonClass = hasWiki && hasGallery ? 'col-span-1 md:col-span-1' : 'col-span-2 md:col-span-1';

    return (
        <div className="flex flex-col 2xl:flex-row items-start 2xl:items-center justify-between gap-3 2xl:gap-6 w-full">
            <div className="flex flex-col md:flex-row items-stretch md:items-center gap-2 md:gap-3 w-full 2xl:w-auto">
                {(!project.versions || project.versions.length === 0) ? (
                    <button disabled className="flex-shrink-0 bg-modtale-accent hover:bg-modtale-accentHover disabled:bg-slate-200 dark:disabled:bg-slate-800 disabled:text-slate-500 text-white px-6 lg:px-8 py-3 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 group cursor-not-allowed">
                        <Download className="w-5 h-5 group-hover:animate-bounce" aria-hidden="true" /> Download
                    </button>
                ) : (
                    <Link to={`${projectUrl}/download`} className="flex-shrink-0 bg-modtale-accent hover:bg-modtale-accentHover text-white px-6 lg:px-8 py-3 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 group">
                        <Download className="w-5 h-5 group-hover:animate-bounce" aria-hidden="true" /> Download
                    </Link>
                )}

                <div className={`hidden md:block w-px h-10 ${theme.colors.bgSurfaceAlt} mx-1 lg:mx-2 shrink-0`}></div>

                <div className="grid grid-cols-2 md:flex md:flex-row gap-2 w-full md:w-auto shrink-0">
                    {hasWiki && (
                        <Link to={`${projectUrl}/wiki`} className={`${mediaButtonClass} flex items-center justify-center gap-1.5 lg:gap-2 px-4 lg:px-5 py-3 md:py-2.5 text-xs lg:text-sm font-bold ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} rounded-xl ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} ${theme.colors.bgSurfaceHover} transition-colors whitespace-nowrap`}>
                            <BookOpen className="w-4 h-4" aria-hidden="true" /> Wiki
                        </Link>
                    )}
                    {hasGallery && (
                        <Link to={`${projectUrl}/gallery#1`} className={`${mediaButtonClass} flex items-center justify-center gap-1.5 lg:gap-2 px-4 lg:px-5 py-3 md:py-2.5 text-xs lg:text-sm font-bold ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} rounded-xl ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} ${theme.colors.bgSurfaceHover} transition-colors whitespace-nowrap`}>
                            <ImageIcon className="w-4 h-4" aria-hidden="true" /> Gallery
                        </Link>
                    )}
                    <Link to={`${projectUrl}/changelog`} className={`flex items-center justify-center gap-1.5 lg:gap-2 px-4 lg:px-5 py-3 md:py-2.5 text-xs lg:text-sm font-bold ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} rounded-xl ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} ${theme.colors.bgSurfaceHover} transition-colors whitespace-nowrap`}>
                        <List className="w-4 h-4" aria-hidden="true" /> Changelog
                    </Link>
                    <a href={`${projectUrl}#comments`} onClick={(e) => { if (location.pathname === projectUrl) { e.preventDefault(); if (commentsRef.current) { const y = commentsRef.current.getBoundingClientRect().top + window.scrollY - 100; window.scrollTo({top: y, behavior: 'smooth'}); } } }} className={`flex items-center justify-center gap-1.5 lg:gap-2 px-4 lg:px-5 py-3 md:py-2.5 text-xs lg:text-sm font-bold ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} rounded-xl ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} ${theme.colors.bgSurfaceHover} transition-colors whitespace-nowrap`}>
                        <MessageSquare className="w-4 h-4" aria-hidden="true" /> Comments
                    </a>
                </div>
            </div>

            <div className="w-full 2xl:w-auto flex flex-col md:flex-row justify-start 2xl:justify-end gap-2 mt-1 2xl:mt-0">
                <div className="hidden md:flex gap-2 flex-wrap justify-start 2xl:justify-end">
                    {links.map((link, idx) => (
                        <a key={idx} href={link.url.startsWith('http') ? link.url : `https://${link.url}`} target="_blank" rel="noreferrer" className={`rounded-xl border transition-all flex items-center justify-center px-4 py-2.5 2xl:p-0 2xl:w-[42px] 2xl:h-[42px] gap-2 2xl:gap-0 ${link.colorClass}`} title={link.label} aria-label={link.label}>
                            <link.icon className="w-4 h-4 2xl:w-5 2xl:h-5 shrink-0" aria-hidden="true" />
                            <span className="text-sm font-bold whitespace-nowrap block 2xl:hidden">{link.label}</span>
                        </a>
                    ))}
                </div>

                {links.length > 0 && (
                    <div className="md:hidden relative w-full" ref={dropdownRef}>
                        <button onClick={() => { setShowMobileLinks(!showMobileLinks); }} className={`w-full flex items-center justify-center gap-2 p-3 rounded-xl ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} font-bold text-slate-700 dark:text-slate-300`}>
                            <LinkIcon className="w-4 h-4" aria-hidden="true" /> External Links <ChevronDown className={`w-4 h-4 transition-transform ${showMobileLinks ? 'rotate-180' : ''}`} aria-hidden="true" />
                        </button>
                        {showMobileLinks && (
                            <div className={`absolute top-full left-0 right-0 mt-1.5 ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-xl z-50 overflow-hidden animate-in fade-in slide-in-from-top-2 p-1`}>
                                {links.map((link, idx) => (
                                    <a key={idx} href={link.url.startsWith('http') ? link.url : `https://${link.url}`} target="_blank" rel="noreferrer" className={`flex items-center gap-3 p-2.5 ${theme.colors.bgSurfaceHover} transition-colors ${theme.colors.textSecondary} hover:${theme.colors.textPrimary}`}>
                                        <div className={`p-1.5 rounded-lg border bg-slate-50 dark:bg-slate-950 ${link.colorClass}`}>
                                            <link.icon className="w-4 h-4" aria-hidden="true" />
                                        </div>
                                        <span className="text-sm font-bold">{link.label}</span>
                                        <ExternalLink className="w-3 h-3 ml-auto opacity-50" aria-hidden="true" />
                                    </a>
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
};
