import React, { useRef, useState, useLayoutEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { PROJECT_TYPES } from '@/data/categories';
import type { Classification } from '@/data/categories';
import { SiteRoutes } from '@/utils/routes';

export const CategoryPillNav: React.FC<{ selectedClassification: Classification | 'All', onClassificationChange: (cls: Classification | 'All') => void, currentSearchParams: URLSearchParams }> = ({ selectedClassification, onClassificationChange, currentSearchParams }) => {
    const tabsRef = useRef<(HTMLAnchorElement | null)[]>([]);
    const navContainerRef = useRef<HTMLDivElement>(null);
    const [pillStyle, setPillStyle] = useState({ left: 0, width: 0, opacity: 0 });
    const [showLeftFade, setShowLeftFade] = useState(false);
    const [showRightFade, setShowRightFade] = useState(false);

    const updatePill = useCallback(() => {
        const index = PROJECT_TYPES.findIndex((t) => t.id === selectedClassification);
        if (index < 0) return;

        const tab = tabsRef.current[index];
        const container = navContainerRef.current;
        if (!tab || !container) return;

        const tabParent = tab.offsetParent as HTMLElement | null;
        const left = tab.offsetLeft + (tabParent?.offsetLeft || 0);
        const width = tab.offsetWidth;

        if (width > 0) {
            setPillStyle({ left, width, opacity: 1 });

            const targetScroll = Math.max(0, left - container.clientWidth / 2 + width / 2);
            container.scrollTo({ left: targetScroll, behavior: 'smooth' });
        }
    }, [selectedClassification]);

    const updateFade = useCallback(() => {
        const container = navContainerRef.current;
        if (!container) return;

        const { scrollLeft, scrollWidth, clientWidth } = container;
        setShowLeftFade(scrollLeft > 1);
        setShowRightFade(Math.ceil(scrollLeft + clientWidth) < scrollWidth - 1);
    }, []);

    useLayoutEffect(() => {
        let frame = 0;
        const run = () => {
            frame = requestAnimationFrame(() => {
                updatePill();
                updateFade();
            });
        };

        run();

        const container = navContainerRef.current;
        if (!container) return () => cancelAnimationFrame(frame);

        const onScroll = () => updateFade();
        container.addEventListener('scroll', onScroll, { passive: true });

        const observer = new ResizeObserver(() => run());
        observer.observe(container);

        return () => {
            cancelAnimationFrame(frame);
            observer.disconnect();
            container.removeEventListener('scroll', onScroll);
        };
    }, [updatePill, updateFade]);

    return (
        <div className="relative group min-w-0 max-w-full inline-flex items-center align-middle">
            <div className={`absolute left-0 top-0 bottom-0 w-8 bg-gradient-to-r from-slate-50/80 via-slate-50/80 to-transparent dark:from-[#0B1120]/80 dark:via-[#0B1120]/80 pointer-events-none z-30 transition-opacity duration-300 ${showLeftFade ? 'opacity-100' : 'opacity-0'}`} />
            <div className={`absolute right-0 top-0 bottom-0 w-12 bg-gradient-to-l from-slate-50/80 via-slate-50/80 to-transparent dark:from-[#0B1120]/80 dark:via-[#0B1120]/80 pointer-events-none z-30 transition-opacity duration-300 ${showRightFade ? 'opacity-100' : 'opacity-0'}`} />

            <div ref={navContainerRef} className="relative inline-flex h-11 bg-white dark:bg-slate-900 p-1 rounded-2xl border border-slate-200 dark:border-white/10 max-w-full overflow-x-auto snap-x z-10 shadow-sm">
                <div className="absolute top-1 bottom-1 bg-modtale-accent shadow-md rounded-xl transition-all duration-300 ease-out z-0" style={{ left: pillStyle.left, width: pillStyle.width, opacity: pillStyle.opacity }} />

                <div className="flex relative z-10 h-full w-max">
                    {PROJECT_TYPES.map((type, index) => {
                        const Icon = type.icon;
                        const isSelected = selectedClassification === type.id;
                        const nextParams = new URLSearchParams(currentSearchParams);
                        nextParams.delete('page');
                        const toPath = SiteRoutes.browse(type.id === 'All' ? undefined : type.id) + (nextParams.toString() ? `?${nextParams.toString()}` : '');

                        return (
                            <Link
                                key={type.id}
                                to={toPath}
                                onClick={() => onClassificationChange(type.id as Classification | 'All')}
                                ref={(el) => { tabsRef.current[index] = el; }}
                                className={`px-3 lg:px-4 h-full rounded-xl text-xs md:text-sm font-bold flex items-center justify-center gap-2 transition-colors duration-200 whitespace-nowrap snap-center ${isSelected ? 'text-white' : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'}`}
                            >
                                <Icon className="w-3.5 h-3.5 lg:w-4 lg:h-4 pointer-events-none" />
                                <span className="inline pointer-events-none">{type.label.replace(' Assets', '').replace('Server ', '')}</span>
                            </Link>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};
