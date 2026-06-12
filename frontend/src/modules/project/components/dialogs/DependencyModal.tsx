import React, { useState, useEffect } from 'react';
import { Link as LinkIcon, X, Check, AlertCircle, Download } from 'lucide-react';
import { theme } from '@/styles/theme';
import { api, BACKEND_URL } from '@/utils/api';
import { useScrollLock } from '@/hooks/useScrollLock';
import type { ProjectVersion } from '@/types';
import { isEmbeddedDependency, isExternalDependency, isOptionalDependency } from '@/modules/project/utils/dependencyEntries';

interface DependencyModalProps {
    dependencies: NonNullable<ProjectVersion['dependencies']>;
    onClose: () => void;
    onDownloadBundle: (selectedDeps: string[]) => void;
    onDownloadProjectOnly: () => void;
    isInline?: boolean;
    initialMetaCache?: Record<string, { title: string; author: string; icon: string }>;
    initialSelected?: string[];
}

export const DependencyModal: React.FC<DependencyModalProps> = ({
                                                                    dependencies,
                                                                    onClose,
                                                                    onDownloadBundle,
                                                                    onDownloadProjectOnly,
                                                                    isInline = false,
                                                                    initialMetaCache,
                                                                    initialSelected
                                                                }) => {
    useScrollLock(!isInline);
    const [selected, setSelected] = useState<Set<string>>(() => {
        if (initialSelected) return new Set(initialSelected);
        return new Set(dependencies.filter(d => !isEmbeddedDependency(d) && !isExternalDependency(d)).map(d => d.projectId));
    });
    const [metaCache, setMetaCache] = useState<Record<string, { title: string; author: string; icon: string }>>(() => initialMetaCache || {});
    const selectableDependencies = dependencies.filter(dep => !isEmbeddedDependency(dep) && !isExternalDependency(dep));

    useEffect(() => {
        const fetchMeta = async () => {
            const missingIds = selectableDependencies.filter(d => !metaCache[d.projectId]).map(d => d.projectId);
            if (missingIds.length === 0) return;

            const newCache = { ...metaCache };
            await Promise.all(missingIds.map(async (id) => {
                try {
                    const res = await api.get(`/projects/${id}/meta`);
                    newCache[id] = { title: res.data.title, author: res.data.author, icon: res.data.icon };
                } catch (e) {
                    newCache[id] = { title: id, author: 'Unknown', icon: '' };
                }
            }));
            setMetaCache(newCache);
        };
        fetchMeta();
    }, [selectableDependencies, metaCache]);

    const missingRequired = selectableDependencies.filter(d => !isOptionalDependency(d) && !selected.has(d.projectId)).length > 0;

    const toggleDep = (id: string) => {
        const next = new Set(selected);
        if (next.has(id)) next.delete(id); else next.add(id);
        setSelected(next);
    };

    const toggleAll = () => {
        if (selected.size === selectableDependencies.length) {
            setSelected(new Set());
        } else {
            setSelected(new Set(selectableDependencies.map(d => d.projectId)));
        }
    };

    const getIconUrl = (path?: string) => {
        if (!path) return '/assets/favicon.svg';
        return path.startsWith('http') ? path : `${BACKEND_URL}${path.startsWith('/') ? '' : '/'}${path}`;
    };

    const content = (
        <div className={`${theme.components.modalContent} ${isInline ? 'w-full min-h-[420px] transform transition-transform duration-500' : 'max-w-lg max-h-[85dvh]'}`} onClick={e => e.stopPropagation()}>
            <div className={theme.components.modalHeader}>
                <h3 className={`text-xl font-black ${theme.colors.textPrimary} flex items-center gap-2`}>
                    <LinkIcon className={`w-5 h-5 ${theme.colors.accent}`} /> Dependencies
                </h3>
                {!isInline && (
                    <button type="button" onClick={onClose} className={`p-2 rounded-full ${theme.colors.bgSurfaceHover} ${theme.colors.textMuted} hover:${theme.colors.dangerText} transition-colors`}><X className="w-5 h-5" /></button>
                )}
            </div>

            <div className={theme.components.modalBody}>
                <div className="flex flex-col items-start gap-2">
                    <div className={`text-sm ${theme.colors.textSecondary} font-medium`}>
                        Select dependencies to include in your bundle download.
                    </div>
                    <button type="button" onClick={toggleAll} className={`text-xs font-bold ${theme.colors.accent} hover:underline`}>
                        {selected.size === selectableDependencies.length ? 'Deselect All' : 'Select All'}
                    </button>
                </div>

                <div className="space-y-2">
                    {selectableDependencies.map(dep => {
                        const meta = metaCache[dep.projectId];
                        const isSelected = selected.has(dep.projectId);
                        const isRequiredMissing = !isOptionalDependency(dep) && !isSelected;

                        return (
                            <div
                                key={dep.projectId}
                                className={`flex items-center justify-between p-4 rounded-2xl border shadow-sm transition-all cursor-pointer group ${
                                    isSelected
                                        ? 'border-blue-400/40 bg-blue-50/60 dark:bg-blue-500/10 hover:bg-blue-50 dark:hover:bg-blue-500/20'
                                        : isRequiredMissing
                                            ? `${theme.colors.dangerBorder} ${theme.colors.dangerBg} hover:border-red-500`
                                            : `${theme.colors.border} ${theme.colors.bgBase} hover:border-blue-400 dark:hover:border-blue-500`
                                }`}
                                onClick={() => toggleDep(dep.projectId)}
                                role="checkbox"
                                aria-checked={isSelected}
                                tabIndex={0}
                                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') toggleDep(dep.projectId); }}
                            >
                                <div className="flex items-center gap-4 min-w-0">
                                    {isSelected ? (
                                        <div className="w-6 h-6 rounded-full bg-modtale-accent text-white flex items-center justify-center shrink-0 shadow-md">
                                            <Check className="w-3.5 h-3.5" aria-hidden="true" />
                                        </div>
                                    ) : isRequiredMissing ? (
                                        <div className={`w-6 h-6 rounded-full border-2 ${theme.colors.dangerBorder} ${theme.colors.dangerBg} flex items-center justify-center shrink-0 shadow-sm transition-colors ${theme.colors.dangerText}`}>
                                            <span className="text-[14px] font-black leading-none">!</span>
                                        </div>
                                    ) : (
                                        <div className={`w-6 h-6 rounded-full border-2 ${theme.colors.border} ${theme.colors.bgSurfaceAlt} flex items-center justify-center shrink-0 shadow-sm transition-colors group-hover:border-blue-400`} />
                                    )}

                                    <div className={`w-12 h-12 rounded-xl border ${theme.colors.border} ${theme.colors.bgBase} flex items-center justify-center shrink-0 overflow-hidden shadow-sm p-1`}>
                                        <img
                                            src={getIconUrl(meta?.icon)}
                                            alt=""
                                            className="w-full h-full object-cover rounded-lg"
                                            onError={(e) => e.currentTarget.src='/assets/favicon.svg'}
                                        />
                                    </div>

                                    <div className="min-w-0">
                                        <div className={`font-bold ${theme.colors.textPrimary} truncate block`}>
                                            {meta?.title || dep.projectTitle || dep.projectId}
                                        </div>
                                        <div className={`text-xs ${theme.colors.textSecondary} font-mono mt-1 flex items-center gap-1.5`}>
                                            <span className="truncate max-w-[100px]">by {meta?.author || '...'}</span>
                                            <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-white/20"></span>
                                            <span className="font-mono opacity-80">v{dep.versionNumber}</span>
                                        </div>
                                    </div>
                                </div>
                                <div className="flex-shrink-0 ml-4 flex items-center">
                                    {isRequiredMissing ? (
                                        <span className="text-[10px] font-black uppercase bg-red-500 text-white px-2.5 py-1 rounded-md shadow-md flex items-center gap-1"><AlertCircle className="w-3 h-3"/> Required</span>
                                    ) : !isOptionalDependency(dep) ? (
                                        <span className={`text-[10px] font-bold uppercase bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-300 px-2.5 py-1 rounded-md border border-blue-200 dark:border-blue-500/30`}>Required</span>
                                    ) : (
                                        <span className={`text-[10px] font-bold uppercase ${theme.colors.bgSurfaceAlt} ${theme.colors.textSecondary} px-2.5 py-1 rounded-md border ${theme.colors.border}`}>Optional</span>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>

                {missingRequired && (
                    <div className={`flex items-start gap-2 text-xs ${theme.colors.dangerText} ${theme.colors.dangerBg} p-3 rounded-xl border ${theme.colors.dangerBorder} shadow-sm`}>
                        <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 animate-pulse" />
                        <p>You have unselected <span className="font-bold">Required</span> dependencies. The project may not function correctly without them.</p>
                    </div>
                )}
            </div>

            <div className={theme.components.modalFooter}>
                <button
                    type="button"
                    onClick={() => {
                        if (selected.size === 0) onDownloadProjectOnly();
                        else onDownloadBundle(Array.from(selected));
                    }}
                    className={`w-full ${theme.components.buttonPrimary} py-3.5 flex-col items-start`}
                >
                    <div className="flex items-center gap-2">
                        <Download className="w-5 h-5 group-hover:-translate-y-0.5 transition-transform" />
                        <span className="leading-tight text-base">
                            {selected.size === 0 ? "Download Project Only" : "Download Bundle"}
                        </span>
                    </div>
                    {selected.size > 0 && (
                        <span className="text-[10px] font-normal text-blue-100 mt-1">
                            Includes project + {selected.size} dependenc{selected.size === 1 ? 'y' : 'ies'}
                        </span>
                    )}
                </button>
            </div>
        </div>
    );

    if (isInline) return content;

    return (
        <div className={theme.components.modalOverlay} onClick={onClose}>
            {content}
        </div>
    );
};
