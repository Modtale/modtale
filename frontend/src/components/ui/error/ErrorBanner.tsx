import React from 'react';
import { AlertCircle } from 'lucide-react';

interface ErrorBannerProps {
    message: string;
    className?: string;
}

export const ErrorBanner: React.FC<ErrorBannerProps> = ({ message, className }) => (
    <div className={`p-4 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 rounded-xl flex items-start gap-3 text-red-600 dark:text-red-400 animate-in fade-in slide-in-from-top-2 ${className}`}>
        <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
        <div>
            <h4 className="font-bold text-sm">Error</h4>
            <p className="text-sm opacity-90">{message}</p>
        </div>
    </div>
);