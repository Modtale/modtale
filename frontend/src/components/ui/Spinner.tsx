import React from 'react';
import { Loader2 } from 'lucide-react';

interface SpinnerProps {
    className?: string;
    fullScreen?: boolean;
    label?: string;
}

export const Spinner: React.FC<SpinnerProps> = ({ className, fullScreen = false, label }) => {
    if (fullScreen) {
        return (
            <div className={`flex flex-col items-center justify-center min-h-[50vh] flex-1 p-8 ${className || ''}`}>
                <Loader2 className="w-8 h-8 animate-spin text-modtale-accent mb-3" />
                {label && <p className="text-sm font-bold text-slate-400 uppercase tracking-widest animate-pulse">{label}</p>}
            </div>
        );
    }

    return (
        <div className="inline-flex items-center gap-2">
            <Loader2 className={`animate-spin ${className || 'w-5 h-5 text-modtale-accent'}`} />
            {label && <span className="text-xs font-bold uppercase tracking-widest opacity-75">{label}</span>}
        </div>
    );
};