import React, { useState, useEffect, useRef } from 'react';
import { ChevronDown, Check } from 'lucide-react';
import { theme } from '@/styles/theme';

interface SortDropdownProps {
    value: string;
    onChange: (val: string) => void;
    onOpen: () => void;
    isMobile: boolean;
}

export const SortDropdown: React.FC<SortDropdownProps> = ({ value, onChange, onOpen, isMobile }) => {
    const [isOpen, setIsOpen] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    const options = [
        { id: 'relevance', label: 'Relevance', mobileOnly: false },
        { id: 'popular', label: 'Popular', mobileOnly: true },
        { id: 'trending', label: 'Trending', mobileOnly: true },
        { id: 'downloads', label: 'Downloads', mobileOnly: false },
        { id: 'favorites', label: 'Favorites', mobileOnly: false },
        { id: 'newest', label: 'Newest', mobileOnly: true },
        { id: 'updated', label: 'Updated', mobileOnly: true }
    ];

    const visibleOptions = options.filter(opt => isMobile || !opt.mobileOnly);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) setIsOpen(false);
        };

        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    const handleToggle = () => {
        if (!isOpen) onOpen();
        setIsOpen(!isOpen);
    };

    const currentLabel = options.find(o => o.id === value)?.label || 'Sort';

    return (
        <div className="relative flex-1 lg:flex-none h-10 w-full lg:w-auto" ref={containerRef}>
            <button
                type="button"
                onClick={handleToggle}
                className={`w-full lg:w-auto h-full flex items-center justify-center lg:justify-between gap-1.5 border rounded-xl px-3 text-xs font-bold transition-all whitespace-nowrap lg:min-w-[110px] shadow-sm ${
                    isOpen
                        ? 'bg-modtale-accent text-white border-transparent'
                        : `${theme.colors.bgBase} ${theme.colors.border} ${theme.colors.textSecondary} ${theme.colors.bgSurfaceHover}`
                }`}
            >
                <span className="truncate">{currentLabel}</span>
                <ChevronDown className={`w-3.5 h-3.5 transition-transform flex-shrink-0 ${isOpen ? 'rotate-180 text-white' : 'text-slate-400'}`} />
            </button>
            {isOpen && (
                <div className={`absolute right-0 top-full mt-2 w-[240px] sm:w-64 md:w-48 max-w-[calc(100vw-2rem)] ${theme.colors.bgBase} border ${theme.colors.border} rounded-2xl shadow-xl py-2 z-[70] animate-in fade-in zoom-in-95 duration-200 overflow-hidden`}>
                    <div className={`px-4 py-2 mb-1 border-b ${theme.colors.borderFaint}`}>
                        <span className="text-[10px] font-black uppercase text-slate-400 dark:text-slate-500 tracking-widest">Sort By</span>
                    </div>
                    {visibleOptions.map(opt => (
                        <button
                            key={opt.id}
                            type="button"
                            onClick={() => {
                                onChange(opt.id);
                                setIsOpen(false);
                            }}
                            className={`w-full text-left px-4 py-2.5 text-sm font-bold flex justify-between items-center transition-colors ${
                                value === opt.id
                                    ? 'bg-modtale-accent text-white'
                                    : `${theme.colors.textSecondary} ${theme.colors.bgSurfaceHover}`
                            }`}
                        >
                            {opt.label}
                            {value === opt.id && <Check className="w-3.5 h-3.5" />}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};