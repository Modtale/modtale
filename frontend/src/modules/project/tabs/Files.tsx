import React from 'react';
import { UploadCloud, Edit2, Trash2 } from 'lucide-react';
import { VersionFields } from '../components/VersionFields';
import { Spinner } from '../../../components/ui/Spinner';
import { theme } from '../../../styles/theme';
import type { Project, ProjectVersion } from '@/types';
import type { VersionFormData } from '../components/FormShared';
import { Permission } from '@/modules/permissions/permissions';

interface FilesProps {
    projectData: Project | null;
    versionData: VersionFormData;
    setVersionData: React.Dispatch<React.SetStateAction<VersionFormData>>;
    readOnly: boolean;
    hasProjectPermission: (perm: Permission) => boolean;
    classification: string;
    handleUploadVersion: () => void;
    handleEditVersion: (version: ProjectVersion) => void;
    handleDeleteVersion?: (versionId: string) => void;
    isLoading: boolean;
}

export const Files: React.FC<FilesProps> = ({ projectData, versionData, setVersionData, readOnly, hasProjectPermission, classification, handleUploadVersion, handleEditVersion, handleDeleteVersion, isLoading }) => {
    const hasUploadedDraftVersion = projectData?.status === 'DRAFT' && (projectData.versions?.length || 0) > 0;

    return (
        <div className="space-y-8">
            {!readOnly && hasProjectPermission(Permission.VERSION_CREATE) && !hasUploadedDraftVersion && (
                <div className={`${theme.colors.bgSurface} p-6 rounded-2xl border ${theme.colors.border}`}>
                    <VersionFields data={versionData} onChange={setVersionData} isModpack={classification === 'MODPACK'} projectType={typeof classification === 'string' ? classification : 'PLUGIN'} currentProjectId={projectData?.id} disabled={readOnly} />
                    <div className="mt-6 flex justify-end">
                        <button type="button" onClick={handleUploadVersion} disabled={isLoading || readOnly} className={theme.components.buttonPrimary}>
                            {isLoading ? <Spinner className="w-5 h-5"/> : <UploadCloud className="w-5 h-5" />} Upload Version
                        </button>
                    </div>
                </div>
            )}
            {hasUploadedDraftVersion && (
                <div className={`${theme.colors.bgSurface} p-4 rounded-xl border ${theme.colors.border}`}>
                    <p className={`text-sm font-medium ${theme.colors.textSecondary}`}>
                        This draft already has one uploaded version. New version uploads are hidden until the project leaves draft status.
                    </p>
                </div>
            )}
            {projectData?.versions?.map(v => (
                <div key={v.id} className={`${theme.colors.bgSurface} border ${theme.colors.border} rounded-xl p-4 flex justify-between items-center group`}>
                    <div>
                        <div className="flex items-center gap-3"><span className={`font-mono font-bold ${theme.colors.textPrimary} text-lg`}>{v.versionNumber}</span><span className={`text-[10px] font-bold px-2 py-0.5 rounded border ${v.channel === 'RELEASE' ? 'text-green-500 border-green-500/30 bg-green-500/10' : 'text-orange-500 border-orange-500/30 bg-orange-500/10'}`}>{v.channel}</span></div>
                        <div className={`text-xs ${theme.colors.textMuted} mt-1`}>{v.gameVersions?.join(', ') || 'Unknown'} • {new Date(v.releaseDate).toLocaleDateString()}</div>
                    </div>
                    {!readOnly && (
                        <div className="flex items-center gap-2">
                            {hasProjectPermission(Permission.VERSION_EDIT) && (
                                <button type="button" onClick={() => handleEditVersion(v)} className={`p-2 ${theme.colors.textMuted} hover:${theme.colors.accent} hover:${theme.colors.accentAlpha} rounded-lg transition-colors`} title="Edit Version Metadata"><Edit2 className="w-4 h-4" /></button>
                            )}
                            {handleDeleteVersion && hasProjectPermission(Permission.VERSION_DELETE) && (
                                <button type="button" onClick={() => handleDeleteVersion(v.id)} className={`p-2 ${theme.colors.textMuted} hover:${theme.colors.dangerText} hover:${theme.colors.dangerBg} rounded-lg transition-colors`}><Trash2 className="w-4 h-4" /></button>
                            )}
                        </div>
                    )}
                </div>
            ))}
        </div>
    );
};
