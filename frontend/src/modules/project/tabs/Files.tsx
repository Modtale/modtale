import React, { useEffect, useState } from 'react';
import { AlertTriangle, UploadCloud, Edit2, Trash2 } from 'lucide-react';
import { VersionFields } from '../components/VersionFields';
import { Spinner } from '../../../components/ui/Spinner';
import { theme } from '../../../styles/theme';
import type { Project, ProjectVersion } from '@/types';
import type { VersionFormData } from '../components/FormShared';
import { Permission } from '@/modules/permissions/permissions';
import { compareSemVer } from '@/utils/modHelpers';
import { parseDependencyEntry, serializeProjectDependency } from '../utils/dependencyEntries';

const normalizeVersionKey = (value: string) => value.trim().toLowerCase();

const getVersionGameVersions = (version: ProjectVersion) => (
    version.gameVersions || (version.gameVersion ? [version.gameVersion] : [])
);

const gameVersionsOverlap = (existingGameVersions: string[], requestedGameVersions: string[]) => {
    if (!existingGameVersions.length || !requestedGameVersions.length) return true;
    const requested = new Set(requestedGameVersions.map(normalizeVersionKey));
    return existingGameVersions.some(gameVersion => requested.has(normalizeVersionKey(gameVersion)));
};

const getOverlappingGameVersions = (existingGameVersions: string[], requestedGameVersions: string[]) => {
    if (!existingGameVersions.length || !requestedGameVersions.length) return requestedGameVersions;
    const existing = new Set(existingGameVersions.map(normalizeVersionKey));
    return requestedGameVersions.filter(gameVersion => existing.has(normalizeVersionKey(gameVersion)));
};

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
    const [reuseLatestSetupDismissed, setReuseLatestSetupDismissed] = useState(false);
    const hasUploadedDraftVersion = projectData?.status === 'DRAFT' && (projectData.versions?.length || 0) > 0;
    const isPrivateProject = projectData?.status === 'PRIVATE';
    const latestVersion = projectData?.versions?.length
        ? [...projectData.versions].sort((a, b) => compareSemVer(b.versionNumber, a.versionNumber))[0]
        : null;
    const canReuseLatestSetup = Boolean(latestVersion) && !readOnly && hasProjectPermission(Permission.VERSION_CREATE) && !reuseLatestSetupDismissed;
    const hasSelectedDependencies = (versionData.projectIds || []).length > 0;
    const selectedVersionNumber = versionData.versionNumber.trim();
    const selectedGameVersions = versionData.gameVersions || [];
    const overlappingExistingVersions = (projectData?.versions || []).filter(version =>
        gameVersionsOverlap(getVersionGameVersions(version), selectedGameVersions)
    );
    const replacementTargets = overlappingExistingVersions.filter(version =>
        selectedVersionNumber.length > 0 && normalizeVersionKey(version.versionNumber || '') === normalizeVersionKey(selectedVersionNumber)
    );
    const canReplaceExistingVersion = replacementTargets.length > 0;
    const replacementTargetGameVersions = Array.from(new Set(replacementTargets.flatMap(version =>
        getOverlappingGameVersions(getVersionGameVersions(version), selectedGameVersions)
    ))).join(', ');
    const replacementCheckboxId = projectData?.id ? `replace-existing-version-${projectData.id}` : 'replace-existing-version';

    useEffect(() => {
        setReuseLatestSetupDismissed(false);
    }, [projectData?.id, latestVersion?.id]);

    useEffect(() => {
        if (!canReplaceExistingVersion && versionData.replaceExisting) {
            setVersionData(prev => ({ ...prev, replaceExisting: false }));
        }
    }, [canReplaceExistingVersion, setVersionData, versionData.replaceExisting]);

    const handleReuseLatestSetup = () => {
        if (!latestVersion) return;

        const nextDependencyEntries = (latestVersion.dependencies || []).map(serializeProjectDependency);
        const dependencyIds = new Set(nextDependencyEntries.map((entry) => parseDependencyEntry(entry).projectId));
        const preservedEntries = (versionData.projectIds || []).filter((entry) => !dependencyIds.has(parseDependencyEntry(entry).projectId));
        const nextIncompatibleIds = latestVersion.incompatibleProjectIds || [];
        const incompatibleIds = new Set(nextIncompatibleIds);
        const preservedIncompatibles = (versionData.incompatibleProjectIds || []).filter((projectId) => !incompatibleIds.has(projectId));

        setVersionData((prev) => ({
            ...prev,
            gameVersions: latestVersion.gameVersions || (latestVersion.gameVersion ? [latestVersion.gameVersion] : prev.gameVersions),
            channel: latestVersion.channel || prev.channel || 'RELEASE',
            projectIds: [...nextDependencyEntries, ...preservedEntries],
            incompatibleProjectIds: [...nextIncompatibleIds, ...preservedIncompatibles],
            replaceExisting: false
        }));
        setReuseLatestSetupDismissed(true);
    };

    return (
        <div className="space-y-8">
            {!readOnly && hasProjectPermission(Permission.VERSION_CREATE) && !hasUploadedDraftVersion && (
                <div className="p-6 rounded-2xl border border-slate-200 dark:border-white/10 bg-slate-100/80 dark:bg-slate-950/40 shadow-inner">
                    {canReuseLatestSetup && (
                        <div className="mb-6 flex flex-col gap-3 rounded-2xl border border-blue-200 bg-blue-50 p-4 dark:border-blue-900/30 dark:bg-blue-900/10 sm:flex-row sm:items-center sm:justify-between">
                            <div>
                                <p className="text-sm font-bold text-blue-900 dark:text-blue-100">Start from your latest release</p>
                                <p className="text-xs text-blue-700 dark:text-blue-300">
                                    Reuse game versions, channel, and dependency selections from v{latestVersion?.versionNumber}.
                                </p>
                            </div>
                            <button type="button" onClick={handleReuseLatestSetup} className="rounded-lg bg-blue-600 px-4 py-2 text-xs font-bold text-white transition-colors hover:bg-blue-700">
                                Reuse Latest Setup
                            </button>
                        </div>
                    )}
                    <VersionFields
                        data={versionData}
                        onChange={setVersionData}
                        isModpack={classification === 'MODPACK'}
                        projectType={typeof classification === 'string' ? classification : 'PLUGIN'}
                        existingVersions={versionData.replaceExisting ? [] : overlappingExistingVersions.map((version) => version.versionNumber)}
                        previousDependencies={!hasSelectedDependencies ? latestVersion?.dependencies || [] : undefined}
                        currentProjectId={projectData?.id}
                        disabled={readOnly}
                    />
                    {canReplaceExistingVersion && (
                        <div className={`mt-6 flex items-start gap-3 rounded-xl border ${theme.colors.warningBorder} ${theme.colors.warningBg} p-4`}>
                            <input
                                id={replacementCheckboxId}
                                type="checkbox"
                                checked={versionData.replaceExisting === true}
                                onChange={() => setVersionData(prev => ({ ...prev, replaceExisting: prev.replaceExisting !== true }))}
                                className="mt-1 h-4 w-4 rounded border-amber-400 text-modtale-accent focus:ring-modtale-accent"
                            />
                            <label htmlFor={replacementCheckboxId} className="min-w-0 cursor-pointer">
                                <span className={`flex items-center gap-2 text-sm font-bold ${theme.colors.textPrimary}`}>
                                    <AlertTriangle className={`h-4 w-4 ${theme.colors.warningText}`} />
                                    Replace existing version
                                </span>
                                <span className={`mt-1 block text-xs ${theme.colors.textSecondary}`}>
                                    This will replace v{selectedVersionNumber}{replacementTargetGameVersions ? ` for ${replacementTargetGameVersions}` : ''} and send the upload through review again.
                                </span>
                            </label>
                        </div>
                    )}
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
            {isPrivateProject && (
                <div className={`${theme.colors.bgSurface} p-4 rounded-xl border ${theme.colors.border}`}>
                    <p className={`text-sm font-medium ${theme.colors.textSecondary}`}>
                        This project is private. It stays hidden from public discovery, but you can keep uploading versions while you work.
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
