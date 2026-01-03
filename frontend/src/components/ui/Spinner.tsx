import React from 'react';
import { Loader2 } from 'lucide-react';

interface SpinnerProps {
    className?: string;
    fullScreen?: boolean;
    label?: string;
}

export const Spinner: React.FC<SpinnerProps> = ({ className, fullScreen = true, label }) => (
    <div className={`flex flex-col items-center justify-center ${fullScreen ? 'min-h-[50vh] flex-1' : 'p-8'} ${className}`}>
        <Loader2 className="w-8 h-8 animate-spin text-modtale-accent mb-3" />
        {label && <p className="text-sm font-bold text-slate-400 uppercase tracking-widest animate-pulse">{label}</p>}
    </div>
);