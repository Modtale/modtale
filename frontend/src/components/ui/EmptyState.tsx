import React from 'react';

interface EmptyStateProps {
    icon: React.ElementType;
    title: string;
    message?: string;
    onAction?: () => void;
    actionLabel?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({ icon: Icon, title, message, onAction, actionLabel }) => (
    <div className="flex flex-col items-center justify-center text-center py-16 px-4 border-2 border-dashed border-slate-300 dark:border-white/10 rounded-2xl bg-slate-50/50 dark:bg-white/[0.02]">
        <div className="w-16 h-16 bg-slate-100 dark:bg-white/5 rounded-full flex items-center justify-center mb-4">
            <Icon className="w-8 h-8 text-slate-400"/>
        </div>
        <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-2">{title}</h3>
        {message && <p className="text-slate-500 dark:text-slate-400 max-w-sm mb-6 leading-relaxed">{message}</p>}

        {onAction && actionLabel && (
            <button
                onClick={onAction}
                className="px-6 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl transition-colors shadow-lg shadow-blue-500/20"
            >
                {actionLabel}
            </button>
        )}
    </div>
);