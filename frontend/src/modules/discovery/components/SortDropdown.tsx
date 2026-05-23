import React, { useMemo } from 'react';
import { DropdownSelect } from '@/components/ui/DropdownSelect';
import { theme } from '@/styles/theme';

interface SortDropdownProps {
    value: string;
    onChange: (val: string) => void;
    onOpen: () => void;
    isMobile: boolean;
}

export const SortDropdown: React.FC<SortDropdownProps> = ({ value, onChange, onOpen, isMobile }) => {
    const options = [
        { id: 'relevance', label: 'Relevance', mobileOnly: false },
        { id: 'popular', label: 'Popular', mobileOnly: true },
        { id: 'trending', label: 'Trending', mobileOnly: true },
        { id: 'downloads', label: 'Downloads', mobileOnly: false },
        { id: 'favorites', label: 'Favorites', mobileOnly: false },
        { id: 'newest', label: 'Newest', mobileOnly: true },
        { id: 'updated', label: 'Updated', mobileOnly: true }
    ];

    const visibleOptions = useMemo(
        () => options.filter((option) => isMobile || !option.mobileOnly),
        [isMobile]
    );

    const currentLabel = options.find((option) => option.id === value)?.label || 'Sort';

    return (
        <DropdownSelect
            value={value}
            onChange={onChange}
            onOpen={onOpen}
            options={visibleOptions.map((option) => ({ value: option.id, label: option.label }))}
            placeholder="Sort"
            containerClassName="relative flex-1 lg:flex-none h-10 w-full lg:w-auto"
            buttonLabel={currentLabel}
            buttonClassName={`w-full lg:w-auto h-full flex items-center justify-center lg:justify-between gap-1.5 border rounded-xl px-3 text-xs font-bold transition-all whitespace-nowrap lg:min-w-[110px] shadow-sm ${theme.colors.bgBase} ${theme.colors.border} ${theme.colors.textSecondary} ${theme.colors.bgSurfaceHover}`}
            menuAlign="right"
            menuClassName={`w-[240px] sm:w-64 md:w-48 max-w-[calc(100vw-2rem)] ${theme.colors.bgBase} border ${theme.colors.border} rounded-2xl shadow-xl py-2 z-[70] animate-in fade-in zoom-in-95 duration-200 overflow-hidden`}
            optionClassName={`w-full text-left px-4 py-2.5 text-sm font-bold flex justify-between items-center transition-colors ${theme.colors.textSecondary} ${theme.colors.bgSurfaceHover}`}
        />
    );
};
