import React, { useRef, useState, useCallback, useLayoutEffect } from 'react';
import { Link } from 'react-router-dom';
import { PROJECT_TYPES } from '@/data/categories';
import type { Classification } from '@/data/categories';

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

export const CategoryPillNav: React.FC<{ selectedClassification: Classification | 'All', onClassificationChange: (cls: Classification | 'All') => void, currentSearchParams: URLSearchParams }> = ({ selectedClassification, onClassificationChange, currentSearchParams }) => {
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
        setShowRightFade(Math.ceil(scrollLeft + clientWidth) < scrollWidth - 1);
    }, []);

    useLayoutEffect(() => {
        calculatePillPosition();
        checkScroll();

        const nav = navContainerRef.current;
        if (!nav) return;

        const resizeObserver = new ResizeObserver(() => {
            calculatePillPosition();
            checkScroll();
        });

        resizeObserver.observe(nav);
        nav.addEventListener('scroll', checkScroll);

        const timer = setTimeout(() => {
            calculatePillPosition();
            checkScroll();
        }, 150);

        return () => {
            clearTimeout(timer);
            resizeObserver.disconnect();
            nav.removeEventListener('scroll', checkScroll);
        };
    }, [calculatePillPosition, checkScroll]);

    return (
        <div className="relative group min-w-0 max-w-full inline-flex items-center align-middle">
            <div className={`absolute left-0 top-0 bottom-0 w-8 bg-gradient-to-r from-slate-50/80 via-slate-50/80 to-transparent dark:from-[#0B1120]/80 dark:via-[#0B1120]/80 pointer-events-none z-30 transition-opacity duration-300 ${showLeftFade ? 'opacity-100' : 'opacity-0'}`} />
            <div className={`absolute right-0 top-0 bottom-0 w-12 bg-gradient-to-l from-slate-50/80 via-slate-50/80 to-transparent dark:from-[#0B1120]/80 dark:via-[#0B1120]/80 pointer-events-none z-30 transition-opacity duration-300 ${showRightFade ? 'opacity-100' : 'opacity-0'}`} />

            <div ref={navContainerRef} className="relative inline-flex h-11 bg-white dark:bg-slate-900 p-1 rounded-2xl border border-slate-200 dark:border-white/10 max-w-full overflow-x-auto snap-x scrollbar-hide z-10 shadow-sm" style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}>
                <style>{` .scrollbar-hide::-webkit-scrollbar { display: none; } `}</style>
                <div className="absolute top-1 bottom-1 bg-modtale-accent shadow-md rounded-xl transition-all duration-300 ease-out z-0" style={{ left: pillStyle.left, width: pillStyle.width, opacity: pillStyle.opacity }} />

                <div className="flex relative z-10 h-full w-max">
                    {PROJECT_TYPES.map((type, index) => {
                        const Icon = type.icon;
                        const isSelected = selectedClassification === type.id;
                        const searchString = currentSearchParams.toString();
                        const toPath = getRouteForClassification(type.id as Classification | 'All') + (searchString ? `?${searchString}` : '');

                        return (
                            <Link
                                key={type.id}
                                to={toPath}
                                onClick={() => onClassificationChange(type.id as Classification | 'All')}
                                ref={(el) => { tabsRef.current[index] = el; }}
                                className={`px-3 lg:px-4 h-full rounded-xl text-xs md:text-sm font-bold flex items-center justify-center gap-2 transition-colors duration-200 whitespace-nowrap snap-center ${isSelected ? 'text-white' : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'}`}
                            >
                                <Icon className={`w-3.5 h-3.5 lg:w-4 lg:h-4 pointer-events-none`} />
                                <span className="inline pointer-events-none">{type.label.replace(' Assets', '').replace('Server ', '')}</span>
                            </Link>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};