import React from 'react';
import { Eye, EyeOff, Globe, Archive, Tag, Link2, ToggleRight, ToggleLeft, Trash2, Settings as SettingsIcon } from 'lucide-react';
import { theme } from '@/styles/theme';
import type { Project } from '@/types';
import type { MetadataFormData } from '../components/FormShared';
import { Permission } from '@/modules/permissions/permissions';

interface SettingsProps {
    projectData: Project | null;
    metaData: MetadataFormData;
    setMetaData: React.Dispatch<React.SetStateAction<MetadataFormData>>;
    setProjectData: React.Dispatch<React.SetStateAction<Project | null>>;
    readOnly: boolean;
    hasProjectPermission: (perm: Permission) => boolean;
    handleRestore?: () => void;
    handlePrivate?: () => void;
    handleUnlist?: () => void;
    handleArchive?: () => void;
    handleDelete?: () => void;
    slugError: string | null;
    handleSlugChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
    getUrlPrefix: () => string;
    markDirty: () => void;
    isLoading: boolean;
}

export const Settings: React.FC<SettingsProps> = ({
                                                      projectData, metaData, setMetaData, setProjectData, readOnly, hasProjectPermission,
                                                      handleRestore, handlePrivate, handleUnlist, handleArchive, handleDelete, slugError, handleSlugChange, getUrlPrefix, markDirty, isLoading
                                                  }) => {
    const canManageVisibility = projectData?.status !== 'PENDING';
    const canShowPublishedToggle = projectData?.status === 'PUBLISHED'
        || projectData?.status === 'UNLISTED'
        || projectData?.status === 'ARCHIVED'
        || (projectData?.status === 'PRIVATE' && !!projectData?.createdAt);

    return (
        <div className="space-y-6">
            <div className={`flex items-center justify-between mb-4 pb-2 border-b ${theme.colors.borderFaint}`}>
                <h3 className={`text-xs font-bold ${theme.colors.textMuted} uppercase tracking-widest flex items-center gap-2`}><SettingsIcon className="w-3 h-3"/> Project Settings</h3>
            </div>

            <div className={`${theme.colors.bgSurface} p-6 rounded-2xl border ${theme.colors.border}`}>
                {canManageVisibility && (
                    <div className={`mb-6 pb-6 border-b ${theme.colors.borderFaint}`}>
                        <h3 className={`text-sm font-bold ${theme.colors.textPrimary} flex items-center gap-2 mb-4`}><Eye className={`w-4 h-4 ${theme.colors.textMuted}`} /> Project Visibility</h3>
                        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                            {canShowPublishedToggle && (
                                <button type="button" onClick={handleRestore} disabled={isLoading || projectData?.status === 'PUBLISHED' || !hasProjectPermission(Permission.PROJECT_STATUS_PUBLISH)} className={`p-4 rounded-xl border flex flex-col items-center gap-3 transition-all ${projectData?.status === 'PUBLISHED' ? 'bg-green-500/10 border-green-500 text-green-500' : `${theme.colors.bgBase} ${theme.colors.border} hover:border-green-500 hover:text-green-500 disabled:opacity-50`}`}>
                                    <Globe className="w-6 h-6" />
                                    <div className="text-center"><div className="font-bold text-sm">Published</div><div className="text-[10px] opacity-70">Visible to everyone</div></div>
                                </button>
                            )}
                            <button type="button" onClick={handlePrivate} disabled={isLoading || projectData?.status === 'PRIVATE' || !hasProjectPermission(Permission.PROJECT_STATUS_UNLIST)} className={`p-4 rounded-xl border flex flex-col items-center gap-3 transition-all ${projectData?.status === 'PRIVATE' ? 'bg-blue-500/10 border-blue-500 text-blue-500' : `${theme.colors.bgBase} ${theme.colors.border} hover:border-blue-500 hover:text-blue-500 disabled:opacity-50`}`}>
                                <Eye className="w-6 h-6" />
                                <div className="text-center"><div className="font-bold text-sm">Private</div><div className="text-[10px] opacity-70">Hidden, but fully editable</div></div>
                            </button>
                            <button type="button" onClick={handleUnlist} disabled={isLoading || projectData?.status === 'UNLISTED' || !hasProjectPermission(Permission.PROJECT_STATUS_UNLIST)} className={`p-4 rounded-xl border flex flex-col items-center gap-3 transition-all ${projectData?.status === 'UNLISTED' ? 'bg-orange-500/10 border-orange-500 text-orange-500' : `${theme.colors.bgBase} ${theme.colors.border} hover:border-orange-500 hover:text-orange-500 disabled:opacity-50`}`}>
                                <EyeOff className="w-6 h-6" />
                                <div className="text-center"><div className="font-bold text-sm">Unlisted</div><div className="text-[10px] opacity-70">Hidden from search</div></div>
                            </button>
                            <button type="button" onClick={handleArchive} disabled={isLoading || !hasProjectPermission(Permission.PROJECT_STATUS_ARCHIVE)} className={`p-4 rounded-xl border flex flex-col items-center gap-3 transition-all ${theme.colors.bgBase} ${theme.colors.border} hover:border-slate-500 hover:text-slate-500 disabled:opacity-50`}>
                                <Archive className="w-6 h-6" />
                                <div className="text-center"><div className="font-bold text-sm">Archived</div><div className="text-[10px] opacity-70">Read-only state</div></div>
                            </button>
                        </div>
                    </div>
                )}

                {projectData?.id && (
                    <div className={`mb-6 pb-6 border-b ${theme.colors.borderFaint}`}>
                        <div className="flex flex-col gap-2">
                            <div><h3 className={`text-sm font-bold ${theme.colors.textPrimary} flex items-center gap-2`}><Tag className={`w-4 h-4 ${theme.colors.textMuted}`} /> Project ID</h3></div>
                            <code className={`${theme.colors.bgSurfaceHover} border ${theme.colors.border} rounded-lg px-3 py-2 text-xs font-mono select-all w-fit`}>{projectData.id}</code>
                        </div>
                    </div>
                )}

                <div className={`mb-6 pb-6 border-b ${theme.colors.borderFaint}`}>
                    <div className="flex flex-col gap-2">
                        <div><h3 className={`text-sm font-bold ${theme.colors.textPrimary} flex items-center gap-2`}><Link2 className={`w-4 h-4 ${theme.colors.textMuted}`} /> Project Slug</h3></div>
                        <div className={`flex items-center w-full ${theme.colors.bgBase} border rounded-xl overflow-hidden focus-within:ring-2 focus-within:ring-modtale-accent transition-all ${slugError ? 'border-red-500' : theme.colors.border}`}>
                            <div className={`px-4 py-2 ${theme.colors.bgSurface} border-r ${theme.colors.border} ${theme.colors.textMuted} text-sm font-mono whitespace-nowrap select-none`}>{getUrlPrefix()}</div>
                            <input id="project-custom-slug-input" disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)} value={metaData.slug || ''} onChange={handleSlugChange} className={`flex-1 bg-transparent border-none px-4 py-2 text-sm font-mono ${theme.colors.textPrimary} focus:outline-none placeholder:text-slate-400 ${slugError ? 'text-red-500' : ''}`} />
                        </div>
                        {slugError && <p className="text-[10px] text-red-500 font-bold">{slugError}</p>}
                    </div>
                </div>

                <div className="flex items-center justify-between mb-4">
                    <div><h3 className={`text-sm font-bold ${theme.colors.textPrimary}`}>Allow Modpacks</h3><p className={`text-xs ${theme.colors.textMuted}`}>Allow inclusion in modpacks?</p></div>
                    <button type="button" disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)} onClick={() => { markDirty(); setProjectData(prev => prev ? {...prev, allowModpacks: !prev.allowModpacks} : null); }} className={`transition-colors ${readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA) ? 'opacity-50' : projectData?.allowModpacks ? 'text-green-500' : theme.colors.textSecondary}`}>{projectData?.allowModpacks ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}</button>
                </div>

                <div className="flex items-center justify-between mb-4">
                    <div><h3 className={`text-sm font-bold ${theme.colors.textPrimary}`}>Allow Comments</h3><p className={`text-xs ${theme.colors.textMuted}`}>Enable community comments?</p></div>
                    <button type="button" disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)} onClick={() => { markDirty(); setProjectData(prev => prev ? {...prev, allowComments: !prev.allowComments} : null); }} className={`transition-colors ${readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA) ? 'opacity-50' : projectData?.allowComments ? 'text-green-500' : theme.colors.textSecondary}`}>{projectData?.allowComments ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}</button>
                </div>

                <div className="flex items-center justify-between mb-4">
                    <div>
                        <h3 className={`text-sm font-bold ${theme.colors.textPrimary}`}>HytaleModding Wiki</h3>
                        <p className={`text-xs ${theme.colors.textMuted} mt-0.5`}>Embed your wiki directly on your project page.</p>
                    </div>
                    <button type="button" disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)} onClick={() => { markDirty(); setProjectData(prev => prev ? {...prev, hmWikiEnabled: !prev.hmWikiEnabled} : null); }} className={`transition-colors ${readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA) ? 'opacity-50' : projectData?.hmWikiEnabled ? 'text-green-500' : theme.colors.textSecondary}`}>{projectData?.hmWikiEnabled ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}</button>
                </div>

                {projectData?.hmWikiEnabled && (
                    <div className={`mb-4 p-4 ${theme.colors.bgSurfaceAlt} rounded-xl border ${theme.colors.border} animate-in slide-in-from-top-2`}>
                        <label className={`text-[10px] font-black uppercase ${theme.colors.textMuted} tracking-widest px-1 mb-2 block`}>Wiki Project Slug / ID</label>
                        <input value={projectData.hmWikiSlug || ''} onChange={e => { markDirty(); const newSlug = e.target.value; setProjectData(prev => prev ? {...prev, hmWikiSlug: newSlug} : null); setMetaData(prev => { const currentWiki = prev.links.WIKI || ''; if (!currentWiki || /^https?:\/\/wiki\.hytalemodding\.dev\/mods?\//i.test(currentWiki)) { return { ...prev, links: { ...prev.links, WIKI: newSlug ? `https://wiki.hytalemodding.dev/mod/${newSlug}` : '' } }; } return prev; }); }} disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)} placeholder="e.g., my-awesome-mod" className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-lg px-3 py-2 text-sm font-mono focus:border-modtale-accent focus:ring-1 focus:ring-modtale-accent outline-none transition-all`} />
                    </div>
                )}
            </div>
            {!readOnly && hasProjectPermission(Permission.PROJECT_DELETE) && <button type="button" onClick={handleDelete} className="w-full bg-red-500/10 border border-red-500/20 text-red-500 hover:bg-red-500 hover:text-white p-4 rounded-xl font-bold flex justify-center gap-2 transition-all"><Trash2 className="w-4 h-4"/> Delete Project</button>}
        </div>
    );
};
