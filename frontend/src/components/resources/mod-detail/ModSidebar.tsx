import React, { useState } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';

interface SidebarSectionProps {
    title: string;
    icon?: React.ElementType;
    children: React.ReactNode;
    defaultOpen?: boolean;
    className?: string;
}

export const ModSidebar: React.FC<SidebarSectionProps> = ({
                                                                  title,
                                                                  icon: Icon,
                                                                  children,
                                                                  defaultOpen = true,
                                                                  className = ""
                                                              }) => {
    const [isOpen, setIsOpen] = useState(defaultOpen);
    return (
        <div className={`border-b border-white/5 last:border-0 pb-4 mb-4 last:mb-0 last:pb-0 ${className}`}>
            <button
                onClick={() => setIsOpen(!isOpen)}
                className="w-full flex items-center justify-between text-xs font-bold text-slate-500 uppercase tracking-widest mb-3 hover:text-white transition-colors"
            >
                <span className="flex items-center gap-2">{Icon && <Icon className="w-3 h-3" />} {title}</span>
                {isOpen ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
            </button>
            {isOpen && <div className="animate-in fade-in slide-in-from-top-1 duration-200">{children}</div>}
        </div>
    );
};