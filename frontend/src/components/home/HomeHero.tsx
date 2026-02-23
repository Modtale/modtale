import React, { useRef, useState, useLayoutEffect, useCallback } from 'react';
import { Search } from 'lucide-react';
import { Link } from 'react-router-dom';
import { PROJECT_TYPES, type Classification } from '../../data/categories';

interface HomeHeroProps {
    selectedClassification: Classification | 'All';
    onClassificationChange: (cls: Classification | 'All') => void;
    searchTerm: string;
    onSearchChange: (term: string) => void;
    currentTypeLabel: string;
    showMiniSearch: boolean;
    seoH1?: string | null;
}

const getRouteForClassification = (cls: string) => {
    switch(cls) {
        case 'PLUGIN': return '/plugins';
        case 'MODPACK': return '/modpacks';
        case 'SAVE': return '/worlds';
        case 'ART': return '/art';
        case 'DATA': return '/data';
        default: return '/';
    }
};

export const HomeHero: React.FC<HomeHeroProps> = React.memo(({
                                                                 selectedClassification,
                                                                 onClassificationChange,
                                                                 searchTerm,
                                                                 onSearchChange,
                                                                 currentTypeLabel,
                                                                 showMiniSearch,
                                                                 seoH1
                                                             }) => {
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

    const renderTitle = () => {
        if (seoH1) {
            if (seoH1.startsWith('Hytale ')) {
                const suffix = seoH1.replace('Hytale ', '');
                return <>Discover <span className="text-modtale-accent">Hytale</span> {suffix}</>;
            }
            return seoH1;
        }

        if (selectedClassification === 'All') {
            return <>Discover <span className="text-modtale-accent">Hytale</span> Mods</>;
        }

        return <>Discover <span className="text-modtale-accent">Hytale</span> {currentTypeLabel.replace(' Assets', '').replace('Server ', '')}</>;
    };

    return (
        <div className="relative bg-slate-50 dark:bg-[#141d30] border-b border-slate-200 dark:border-white/5 pt-24 md:pt-32 pb-4 px-4 overflow-hidden z-20 shadow-sm">

            <div className="absolute inset-0 bg-gradient-to-b from-modtale-accent/5 to-transparent pointer-events-none z-0" />

            <div className="max-w-7xl mx-auto relative z-10">
                <div className="text-center max-w-4xl mx-auto mb-6 md:mb-14">
                    <h1 className="text-3xl md:text-5xl font-black mb-4 md:mb-6 text-slate-800 dark:text-white tracking-tight drop-shadow-sm">
                        {renderTitle()}
                    </h1>

                    <div className={`relative max-w-3xl mx-auto group z-30 transition-all duration-300 ${showMiniSearch ? 'md:opacity-0 md:pointer-events-none' : 'md:opacity-100'} opacity-100`}>
                        <div className="absolute inset-y-0 left-0 pl-4 md:pl-5 flex items-center pointer-events-none z-10">
                            <Search
                                aria-hidden="true"
                                className="h-4 w-4 md:h-5 md:w-5 text-slate-600 dark:text-slate-300 opacity-90 group-focus-within:text-modtale-accent group-focus-within:opacity-100 transition-colors transition-opacity duration-300"
                            />
                        </div>
                        <input
                            type="text"
                            className="block w-full pl-10 md:pl-12 pr-6 py-2.5 md:py-3 rounded-xl md:rounded-2xl bg-white dark:bg-slate-800/50 border border-slate-200 dark:border-white/10 text-base md:text-xl focus:border-modtale-accent focus:ring-2 focus:ring-modtale-accent/20 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 transition-all shadow-lg shadow-black/5 backdrop-blur-md relative z-0"
                            placeholder={`Search ${currentTypeLabel.replace('Server ', '').toLowerCase()}...`}
                            value={searchTerm}
                            onChange={(e) => onSearchChange(e.target.value)}
                        />
                    </div>
                </div>

                <div className="flex justify-center -mx-4 md:mx-0 px-4 md:px-0 relative group">
                    <div className={`absolute left-0 top-0 bottom-0 w-16 bg-gradient-to-r from-slate-50 via-slate-50/80 to-transparent dark:from-[#141d30] dark:via-[#141d30]/80 pointer-events-none z-30 rounded-l-xl transition-opacity duration-300 ${showLeftFade ? 'opacity-100' : 'opacity-0'}`} />
                    <div className={`absolute right-0 top-0 bottom-0 w-24 bg-gradient-to-l from-slate-50 via-slate-50/80 to-transparent dark:from-[#141d30] dark:via-[#141d30]/80 pointer-events-none z-30 rounded-r-xl transition-opacity duration-300 ${showRightFade ? 'opacity-100' : 'opacity-0'}`} />

                    <div ref={navContainerRef} className="relative inline-flex bg-slate-100/50 dark:bg-slate-900/40 backdrop-blur-sm p-1.5 md:p-2 rounded-xl border border-slate-200 dark:border-white/5 max-w-full overflow-x-auto snap-x scrollbar-hide z-10" style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}>
                        <style>{` .scrollbar-hide::-webkit-scrollbar { display: none; } `}</style>
                        <div className="absolute top-1.5 md:top-2 bottom-1.5 md:bottom-2 bg-white dark:bg-modtale-accent shadow-sm rounded-lg transition-all duration-300 ease-out z-0" style={{ left: pillStyle.left, width: pillStyle.width, opacity: pillStyle.opacity }} />
                        <div className="flex relative z-10 gap-1 md:gap-0">
                            {PROJECT_TYPES.map((type, index) => {
                                const Icon = type.icon;
                                const isSelected = selectedClassification === type.id;
                                return (
                                    <Link
                                        key={type.id}
                                        to={getRouteForClassification(type.id)}
                                        onClick={() => onClassificationChange(type.id as any)}
                                        ref={el => tabsRef.current[index] = el}
                                        className={`px-4 md:px-5 py-2 rounded-lg text-xs md:text-sm font-bold flex items-center justify-center gap-2 transition-colors duration-200 whitespace-nowrap snap-center ${isSelected ? 'text-slate-900 dark:text-white' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'}`}
                                    >
                                        <Icon className={`w-3.5 h-3.5 md:w-4 md:h-4 ${isSelected ? 'text-modtale-accent dark:text-white' : ''}`} />
                                        <span className="inline">{type.label.replace(' Assets', '').replace('Server ', '')}</span>
                                    </Link>
                                );
                            })}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
});

HomeHero.displayName = 'HomeHero';