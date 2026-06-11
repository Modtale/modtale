import React from 'react';
import { theme } from '@/styles/theme';

export type MetadataFormData = {
    title: string;
    slug?: string;
    summary: string;
    description: string;
    tags: string[];
    links: Record<string, string>;
    repositoryUrl: string;
    iconFile: File | null;
    iconPreview: string | null;
    license?: string;
};

export type VersionFormData = {
    projectIds: string[];
    incompatibleProjectIds: string[];
    channel?: 'RELEASE' | 'BETA' | 'ALPHA';
    versionNumber: string;
    gameVersions: string[];
    changelog: string;
    file: File | null;
    dependencies: string[];
    modIds: any[];
};

export const Label = ({ children, required, className }: { children: React.ReactNode, required?: boolean, className?: string }) => (
    <label className={`block text-xs font-bold uppercase ${theme.colors.textMuted} mb-2 tracking-wide flex items-center gap-1 ${className ?? ''}`}>
        {children} {required && <span className={theme.colors.dangerText}>*</span>}
    </label>
);

export const Input = (props: React.InputHTMLAttributes<HTMLInputElement>) => (
    <input
        {...props}
        className={`w-full ${theme.colors.bgSurface} border ${theme.colors.border} rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-modtale-accent focus:border-modtale-accent outline-none transition-all placeholder:text-slate-400 dark:text-white ${props.className ?? ''}`}
    />
);

export const ThemedInput = ({ label, disabled, ...props }: any) => (
    <div className="space-y-1.5">
        <label className={`text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest`}>{label}</label>
        <input {...props} disabled={disabled} className={`w-full ${theme.colors.bgSurface} border ${theme.colors.border} rounded-xl px-4 py-2.5 ${theme.colors.textPrimary} placeholder:text-slate-400 dark:placeholder:text-slate-600 outline-none transition-all text-sm ${disabled ? 'opacity-50 cursor-not-allowed' : 'focus:ring-2 focus:ring-modtale-accent'}`} />
    </div>
);
