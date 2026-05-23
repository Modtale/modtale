import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';

export interface DropdownOption {
    value: string;
    label: React.ReactNode;
    leftAdornment?: React.ReactNode;
    rightAdornment?: React.ReactNode;
    disabled?: boolean;
}

interface DropdownSelectProps {
    options: DropdownOption[];
    value?: string;
    onChange: (value: string) => void;
    placeholder?: React.ReactNode;
    disabled?: boolean;
    onOpen?: () => void;
    showSelectedCheck?: boolean;
    containerClassName?: string;
    buttonClassName?: string;
    menuClassName?: string;
    optionClassName?: string;
    menuAlign?: 'left' | 'right';
    maxMenuHeightClassName?: string;
    emptyLabel?: React.ReactNode;
    buttonLabel?: React.ReactNode;
}

export const DropdownSelect: React.FC<DropdownSelectProps> = ({
    options,
    value,
    onChange,
    placeholder = 'Select...',
    disabled = false,
    onOpen,
    showSelectedCheck = true,
    containerClassName = '',
    buttonClassName = '',
    menuClassName = '',
    optionClassName = '',
    menuAlign = 'left',
    maxMenuHeightClassName = 'max-h-60',
    emptyLabel = 'No options found',
    buttonLabel
}) => {
    const [isOpen, setIsOpen] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const selectedOption = useMemo(() => options.find((option) => option.value === value), [options, value]);

    const handleToggle = () => {
        if (disabled) return;
        if (!isOpen && onOpen) onOpen();
        setIsOpen((prev) => !prev);
    };

    const alignmentClass = menuAlign === 'right' ? 'right-0' : 'left-0';

    return (
        <div className={`relative ${containerClassName}`.trim()} ref={containerRef}>
            <button
                type="button"
                disabled={disabled}
                onClick={handleToggle}
                className={buttonClassName}
            >
                <span className="min-w-0 truncate text-left">
                    {buttonLabel || selectedOption?.label || placeholder}
                </span>
                <ChevronDown className={`w-4 h-4 transition-transform shrink-0 ${isOpen ? 'rotate-180' : ''}`} />
            </button>

            {isOpen && !disabled && (
                <div className={`absolute ${alignmentClass} top-full mt-2 ${maxMenuHeightClassName} overflow-y-auto custom-scrollbar z-[100] ${menuClassName}`.trim()}>
                    {options.length > 0 ? (
                        options.map((option) => {
                            const isSelected = option.value === value;
                            return (
                                <button
                                    key={option.value}
                                    type="button"
                                    disabled={option.disabled}
                                    onClick={() => {
                                        if (option.disabled) return;
                                        onChange(option.value);
                                        setIsOpen(false);
                                    }}
                                    className={`${optionClassName} ${option.disabled ? 'opacity-50 cursor-not-allowed' : ''}`.trim()}
                                >
                                    <div className="min-w-0 flex items-center gap-2 truncate">
                                        {option.leftAdornment}
                                        <span className="truncate">{option.label}</span>
                                    </div>
                                    <div className="flex items-center gap-2 shrink-0">
                                        {option.rightAdornment}
                                        {showSelectedCheck && isSelected && <Check className="w-4 h-4" />}
                                    </div>
                                </button>
                            );
                        })
                    ) : (
                        <div className="px-4 py-3 text-sm text-center">{emptyLabel}</div>
                    )}
                </div>
            )}
        </div>
    );
};
