import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useDropzone, type Accept, type FileRejection } from 'react-dropzone';
import { UploadCloud, CheckCircle2, AlertCircle, ChevronDown, Check, Beaker, Zap, Loader2 } from 'lucide-react';
import { Label, Input } from './FormShared';
import type { VersionFormData } from './FormShared';
import { DependencySelector } from './DependencySelector';
import { projectClient } from '../api/projectClient';
import { theme } from '@/styles/theme';
import { VersionRelationKind, type GameVersionCatalog, type ManifestDependencySuggestion, type ProjectDependency } from '@/types';

const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
const MAX_UPLOAD_ERROR_MESSAGE = 'File exceeds 100MB limit. Cloudflare only supports uploads up to 100MB.';

const STRICT_VERSION_REGEX = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$/;

const uniqueVersions = (versions: string[]) => Array.from(new Set(versions.filter(Boolean)));

const getCatalogVersions = (catalog: GameVersionCatalog | null | undefined) => {
    const allVersions = catalog?.allVersions || [];
    if (allVersions.length > 0) return allVersions;

    const orderedVersions = catalog?.orderedVersions || [];
    if (orderedVersions.length > 0) return orderedVersions;

    const entryVersions = catalog?.versions?.map(entry => entry.version).filter(Boolean) || [];
    if (entryVersions.length > 0) return uniqueVersions(entryVersions);

    return uniqueVersions([...(catalog?.releaseVersions || []), ...(catalog?.preReleaseVersions || [])]);
};

const getDefaultGameVersion = (catalog: GameVersionCatalog | null | undefined, availableVersions: string[]) => {
    const releaseVersions = catalog?.releaseVersions || [];
    if (releaseVersions.length > 0) return releaseVersions[0];

    const releaseEntry = catalog?.versions?.find(entry => !entry.preRelease && entry.version);
    if (releaseEntry?.version) return releaseEntry.version;

    return availableVersions[0] || '';
};

interface VersionFieldsProps {
    data: VersionFormData;
    onChange: (d: VersionFormData) => void;
    isModpack?: boolean;
    projectType?: string;
    existingVersions?: string[];
    previousDependencies?: ProjectDependency[];
    disabled?: boolean;
    hideFilePicker?: boolean;
    currentProjectId?: string;
}

export const VersionFields: React.FC<VersionFieldsProps> = ({ data, onChange, isModpack, projectType, existingVersions = [], previousDependencies, disabled, hideFilePicker = false, currentProjectId }) => {
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const [availableGameVersions, setAvailableGameVersions] = useState<string[]>([]);
    const [defaultGameVersion, setDefaultGameVersion] = useState('');
    const [loadingVersions, setLoadingVersions] = useState(false);
    const [manifestSuggestions, setManifestSuggestions] = useState<ManifestDependencySuggestion[]>([]);
    const [loadingManifestSuggestions, setLoadingManifestSuggestions] = useState(false);
    const [manifestSuggestionError, setManifestSuggestionError] = useState<string | null>(null);
    const [fileError, setFileError] = useState<string | null>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const fetchVersions = async () => {
            setLoadingVersions(true);
            try {
                const catalog = await projectClient.getMetaGameVersionCatalog();
                const versions = getCatalogVersions(catalog);

                if (versions.length > 0) {
                    setAvailableGameVersions(versions);
                    setDefaultGameVersion(getDefaultGameVersion(catalog, versions));
                    return;
                }

                const fallbackVersions = await projectClient.getMetaGameVersions();
                setAvailableGameVersions(fallbackVersions);
                setDefaultGameVersion(fallbackVersions[0] || '');
            } catch {
                try {
                    const fallbackVersions = await projectClient.getMetaGameVersions();
                    setAvailableGameVersions(fallbackVersions);
                    setDefaultGameVersion(fallbackVersions[0] || '');
                } catch {}
            } finally { setLoadingVersions(false); }
        };
        fetchVersions();
    }, []);

    useEffect(() => {
        if (!disabled && (!data.gameVersions || data.gameVersions.length === 0) && availableGameVersions.length > 0) {
            onChange({ ...data, gameVersions: [defaultGameVersion || availableGameVersions[0]] });
        }
    }, [disabled, data.gameVersions, onChange, availableGameVersions, defaultGameVersion]);

    const versionNum = data.versionNumber.trim();
    const isFormatValid = STRICT_VERSION_REGEX.test(versionNum);
    const isDuplicate = existingVersions.some(existingVersion => existingVersion.toLowerCase() === versionNum.toLowerCase());
    const isValid = versionNum.length > 0 && isFormatValid && !isDuplicate;
    const allowsAutoSwitch = projectType === 'PLUGIN' || projectType === 'DATA' || projectType === 'ART';

    const getAcceptTypes = (): Accept => {
        switch (projectType) {
            case 'SAVE':
            case 'MODPACK':
                return { 'application/zip': ['.zip'] };
            case 'ART':
            case 'DATA':
                return { 'application/java-archive': ['.jar'], 'application/zip': ['.zip'] };
            case 'PLUGIN': return { 'application/java-archive': ['.jar'], 'application/zip': ['.zip'] };
            default: return { 'application/java-archive': ['.jar'], 'application/zip': ['.zip'], 'application/json': ['.json'] };
        }
    };

    const onFileDrop = useCallback((acceptedFiles: File[]) => {
        const nextFile = acceptedFiles[0];
        if (!nextFile || disabled) return;

        onChange({ ...data, file: nextFile });
        setFileError(null);
        setManifestSuggestions([]);
        setManifestSuggestionError(null);

        const isJar = nextFile.name.toLowerCase().endsWith('.jar');

        if (projectType === 'PLUGIN' && isJar && currentProjectId) {
            setLoadingManifestSuggestions(true);
            projectClient.inspectManifest(currentProjectId, nextFile)
                .then((result) => {
                    const manifestGameVersion = result?.gameVersion?.trim();
                    const manifestModVersion = result?.modVersion?.trim();
                    const canApplyManifestVersion = !!manifestGameVersion && availableGameVersions.includes(manifestGameVersion);
                    onChange({
                        ...data,
                        file: nextFile,
                        versionNumber: manifestModVersion || data.versionNumber,
                        gameVersions: canApplyManifestVersion ? [manifestGameVersion] : data.gameVersions
                    });
                    setManifestSuggestions(result?.suggestions || []);
                })
                .catch((error) => {
                    const msg = typeof error.response?.data === 'string' ? error.response.data : 'Could not inspect manifest.json.';
                    setManifestSuggestionError(msg);
                })
                .finally(() => setLoadingManifestSuggestions(false));
        }
    }, [data, onChange, disabled, projectType, currentProjectId, availableGameVersions]);

    const onFileDropRejected = useCallback((rejections: FileRejection[]) => {
        const tooLarge = rejections.some(r => r.errors.some(e => e.code === 'file-too-large'));
        setFileError(tooLarge ? MAX_UPLOAD_ERROR_MESSAGE : 'Invalid file type. Please upload a supported file.');
    }, []);

    const { getRootProps, getInputProps, isDragActive } = useDropzone({
        onDrop: onFileDrop,
        onDropRejected: onFileDropRejected,
        maxFiles: 1,
        maxSize: MAX_UPLOAD_BYTES,
        accept: getAcceptTypes(),
        disabled: disabled
    });

    const toggleGameVersion = (ver: string) => {
        if(disabled) return;
        const current = data.gameVersions || [];
        const next = current.includes(ver) ? current.filter(v => v !== ver) : [...current, ver];
        onChange({ ...data, gameVersions: next });
    };

    const addManifestSuggestions = (suggestions: ManifestDependencySuggestion[]) => {
        const existingIds = new Set((data.projectIds || []).map(dep => dep.split(':')[0]));
        const nextSuggestions = suggestions
            .filter(suggestion => !existingIds.has(suggestion.projectId))
            .map(suggestion => suggestion.dependencyEntry);
        if (nextSuggestions.length === 0) return;
        onChange({ ...data, projectIds: [...(data.projectIds || []), ...nextSuggestions] });
        setManifestSuggestions(prev => prev.filter(suggestion => !nextSuggestions.includes(suggestion.dependencyEntry)));
    };

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) setDropdownOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    return (
        <div className={`space-y-8 ${disabled ? 'opacity-60 pointer-events-none' : ''}`}>
            {!isModpack && !hideFilePicker && (
                <div>
                    <Label required>Project File <span className={`${theme.colors.textSecondary} font-normal normal-case ml-1`}>{allowsAutoSwitch ? '(.jar or .zip)' : '(.zip)'}</span></Label>
                    <div
                        {...getRootProps()}
                        className={`border-2 border-dashed rounded-2xl p-10 text-center transition-all group shadow-sm ${
                            isDragActive
                                ? `border-modtale-accent bg-modtale-accent/5 dark:bg-modtale-accent/10 shadow-modtale-accent/10`
                                : disabled
                                    ? `${theme.colors.border} ${theme.colors.bgSurface} cursor-not-allowed`
                                    : `border-slate-300 dark:border-white/15 ${theme.colors.bgBase} hover:border-modtale-accent hover:shadow-md hover:shadow-modtale-accent/10 cursor-pointer`
                        }`}
                    >
                        <input {...getInputProps()} disabled={disabled} />
                        <div className={`mb-4 w-12 h-12 mx-auto ${theme.colors.bgSurfaceAlt} rounded-full flex items-center justify-center ring-1 ${theme.colors.border} shadow-sm group-hover:scale-110 transition-transform`}>
                            <UploadCloud className={`w-6 h-6 ${theme.colors.accent}`} />
                        </div>
                        {data.file ? (
                            <div>
                                <div className={`font-bold ${theme.colors.textPrimary} text-lg`}>{data.file.name}</div>
                                <div className={`text-xs ${theme.colors.textSecondary} font-mono mt-1 mb-2`}>{(data.file.size / 1024 / 1024).toFixed(2)} MB</div>
                                <div className={`inline-flex items-center gap-1 text-xs font-bold ${theme.colors.successText} ${theme.colors.successBg} px-2 py-0.5 rounded-full`}>
                                    <Check className="w-3 h-3" /> Ready
                                </div>
                                {loadingManifestSuggestions && (
                                    <div className={`mt-2 text-xs ${theme.colors.textSecondary} flex items-center justify-center gap-1`}>
                                        <Loader2 className="w-3 h-3 animate-spin" /> Reading manifest.json...
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div>
                                <div className={`font-bold ${theme.colors.textPrimary}`}>Click or drag file here</div>
                                <div className={`text-xs ${theme.colors.textSecondary} mt-1`}>
                                    {allowsAutoSwitch ? 'Supports .jar and .zip (type auto-switches when needed)' : 'Supports .zip archives'}
                                </div>
                                <div className={`text-xs ${theme.colors.textSecondary} mt-1`}>Maximum file size: 100MB</div>
                            </div>
                        )}
                    </div>
                    {fileError && (
                        <div className={`mt-3 p-3 rounded-xl border ${theme.colors.dangerBorder} ${theme.colors.dangerBg} ${theme.colors.dangerText} text-xs font-bold flex items-start gap-2`}>
                            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                            <span>{fileError}</span>
                        </div>
                    )}
                    {manifestSuggestionError && (
                        <div className={`mt-3 p-3 rounded-xl border ${theme.colors.dangerBorder} ${theme.colors.dangerBg} ${theme.colors.dangerText} text-xs font-bold flex items-start gap-2`}>
                            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                            <span>{manifestSuggestionError}</span>
                        </div>
                    )}
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                <div>
                    <div className="flex justify-between items-center mb-2">
                        <Label required>Version Number</Label>
                        {versionNum.length > 0 && !disabled && (
                            <span className={`text-[10px] font-bold uppercase flex items-center gap-1 px-2 py-0.5 rounded border ${isValid ? `${theme.colors.successText} ${theme.colors.successBg} border-emerald-200 dark:border-emerald-500/20` : `${theme.colors.dangerText} ${theme.colors.dangerBg} border-red-200 dark:border-red-500/20`}`}>
                                {isValid ? <><CheckCircle2 className="w-3 h-3" /> Valid</> : <><AlertCircle className="w-3 h-3" /> {isDuplicate ? 'Duplicate' : 'Invalid Format'}</>}
                            </span>
                        )}
                    </div>
                    <Input
                        value={data.versionNumber}
                        disabled={disabled}
                        onChange={e => onChange({...data, versionNumber: e.target.value})}
                        placeholder="e.g. 1.0.0"
                        className={`font-mono font-bold ${versionNum.length > 0 && !isValid && !disabled ? 'border-red-500 focus:ring-red-500' : ''}`}
                    />
                    <p className={`text-[10px] ${theme.colors.textSecondary} mt-1.5 ml-1`}>Must be unique and follow SemVer format (e.g. 1.0.0, 1.0.0-beta+exp).</p>
                </div>

                <div ref={dropdownRef}>
                    <Label required>Game Versions</Label>
                    <div className="relative">
                        <button
                            type="button"
                            disabled={disabled}
                            onClick={() => !disabled && setDropdownOpen(!dropdownOpen)}
                            className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl px-4 py-3 text-sm outline-none transition-all text-left flex justify-between items-center shadow-sm ${disabled ? 'cursor-not-allowed opacity-70' : 'hover:border-modtale-accent focus:ring-2 focus:ring-modtale-accent focus:border-modtale-accent'}`}
                        >
                            <span className={data.gameVersions?.length > 0 ? `${theme.colors.textPrimary} font-medium` : theme.colors.textSecondary}>
                                {loadingVersions ? "Loading..." : data.gameVersions && data.gameVersions.length > 0 ? `${data.gameVersions.length} selected` : "Select Versions..."}
                            </span>
                            {loadingVersions ? <Loader2 className={`w-4 h-4 ${theme.colors.textSecondary} animate-spin`} /> : <ChevronDown className={`w-4 h-4 ${theme.colors.textSecondary}`} />}
                        </button>
                        {dropdownOpen && !disabled && !loadingVersions && (
                            <div className={`absolute top-full mt-1 left-0 w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-xl z-50 overflow-hidden animate-in fade-in zoom-in-95 duration-100`}>
                                {availableGameVersions.map(v => (
                                    <button
                                        key={v}
                                        type="button"
                                        onClick={(e) => { e.stopPropagation(); toggleGameVersion(v); }}
                                        className={`w-full text-left px-4 py-3 ${theme.colors.bgSurfaceHover} text-sm ${theme.colors.textSecondary} transition-colors flex items-center justify-between`}
                                    >
                                        <span>{v}</span>
                                        {data.gameVersions?.includes(v) && <Check className={`w-4 h-4 ${theme.colors.accent}`} />}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                    {data.gameVersions?.length > 0 && (
                        <div className="flex flex-wrap gap-2 mt-2">
                            {data.gameVersions.map(v => (
                                <span key={v} className={`text-[10px] font-bold px-2 py-1 ${theme.colors.bgSurfaceAlt} rounded ${theme.colors.textPrimary}`}>{v}</span>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            <div>
                <Label required>Release Channel</Label>
                <div className={`flex ${theme.colors.bgSurface} p-1 rounded-xl border ${theme.colors.border}`}>
                    {(['RELEASE', 'BETA', 'ALPHA'] as const).map(c => (
                        <button
                            key={c}
                            type="button"
                            disabled={disabled}
                            onClick={() => onChange({ ...data, channel: c })}
                            className={`flex-1 py-2 rounded-lg text-xs font-black uppercase transition-all flex items-center justify-center gap-2 ${
                                (data.channel || 'RELEASE') === c
                                    ? (c === 'RELEASE' ? 'bg-green-500 text-white shadow-lg' : c === 'BETA' ? 'bg-blue-500 text-white shadow-lg' : 'bg-orange-500 text-white shadow-lg')
                                    : `${theme.colors.textMuted} hover:${theme.colors.textPrimary}`
                            } ${disabled ? 'cursor-not-allowed opacity-70' : ''}`}
                        >
                            {c === 'RELEASE' && <CheckCircle2 className="w-3 h-3" />}
                            {c === 'BETA' && <Beaker className="w-3 h-3" />}
                            {c === 'ALPHA' && <Zap className="w-3 h-3" />}
                            {c}
                        </button>
                    ))}
                </div>
            </div>

            {projectType !== 'SAVE' && !isModpack && (
                <div className="mt-4">
                    <Label>Dependencies</Label>
                    <p className={`text-xs ${theme.colors.textSecondary} mb-2`}>Does your project require other projects to work?</p>
                    {manifestSuggestions.length > 0 && (
                        <div className="mb-4 p-4 rounded-xl border border-blue-200 dark:border-blue-900/30 bg-blue-50 dark:bg-blue-900/10">
                            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                                <div>
                                    <h4 className="text-sm font-black text-blue-900 dark:text-blue-100">Manifest dependencies found</h4>
                                    <p className="text-xs text-blue-700 dark:text-blue-300">These Modtale projects look like matches for dependencies listed in manifest.json.</p>
                                </div>
                                <button type="button" onClick={() => addManifestSuggestions(manifestSuggestions)} className="text-xs font-bold bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg transition-colors shadow-sm">
                                    Add All
                                </button>
                            </div>
                            <div className="mt-3 space-y-2">
                                {manifestSuggestions.map(suggestion => (
                                    <div key={`${suggestion.manifestKey}:${suggestion.projectId}`} className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-lg bg-white/70 dark:bg-slate-950/40 border border-blue-100 dark:border-blue-900/30 p-3">
                                        <div className="min-w-0">
                                            <div className={`font-bold ${theme.colors.textPrimary} text-sm truncate`}>{suggestion.projectTitle} <span className={`font-mono ${theme.colors.textMuted}`}>v{suggestion.versionNumber}</span></div>
                                            <div className={`text-xs ${theme.colors.textMuted}`}>{suggestion.manifestKey} {suggestion.optional ? '(optional)' : '(required)'}</div>
                                        </div>
                                        <button type="button" onClick={() => addManifestSuggestions([suggestion])} className={`text-xs font-bold ${theme.colors.accent} hover:underline shrink-0`}>
                                            Add
                                        </button>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                    <DependencySelector
                        selectedDeps={data.projectIds || []}
                        onChange={(deps) => onChange({ ...data, projectIds: deps })}
                        targetGameVersion={data.gameVersions?.[0]}
                        label="Add Dependency"
                        previousDependencies={previousDependencies}
                        currentProjectId={currentProjectId}
                        isModpack={false}
                        disabled={disabled}
                    />
                </div>
            )}

            {isModpack && (
                <div className="mt-4">
                    <Label required>Included Projects</Label>
                    <p className={`text-xs ${theme.colors.textSecondary} mb-2`}>Select the projects to include in this modpack version.</p>
                    <DependencySelector
                        selectedDeps={data.projectIds || []}
                        onChange={(deps) => onChange({ ...data, projectIds: deps })}
                        targetGameVersion={data.gameVersions?.[0]}
                        label="Add Projects"
                        isModpack={true}
                        disabled={disabled}
                    />
                </div>
            )}

            {projectType !== 'SAVE' && (
                <div className="mt-4">
                    <Label>Incompatible Mods</Label>
                    <p className={`text-xs ${theme.colors.textSecondary} mb-2`}>Mark mods that should not be used alongside this version.</p>
                    <DependencySelector
                        selectedDeps={data.incompatibleProjectIds || []}
                        onChange={(deps) => onChange({ ...data, incompatibleProjectIds: deps })}
                        label="Add Incompatible Mod"
                        mode={VersionRelationKind.INCOMPATIBILITY}
                        currentProjectId={currentProjectId}
                        disabled={disabled}
                    />
                </div>
            )}

            <div>
                <div className="flex justify-between items-center mb-2">
                    <Label>Changelog</Label>
                    <span className={`text-[10px] uppercase font-bold ${theme.colors.textSecondary} ${theme.colors.bgSurfaceAlt} px-2 py-0.5 rounded border ${theme.colors.border}`}>Markdown Only</span>
                </div>
                <textarea
                    value={data.changelog}
                    disabled={disabled}
                    onChange={e => onChange({...data, changelog: e.target.value})}
                    rows={6}
                    className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl px-4 py-3 font-mono text-sm outline-none transition-all placeholder:text-slate-400 dark:text-white shadow-sm ${disabled ? 'cursor-not-allowed opacity-70' : 'focus:ring-2 focus:ring-modtale-accent focus:border-modtale-accent hover:border-modtale-accent'}`}
                    placeholder="- Fixed bugs&#10;- Added new items"
                />
            </div>
        </div>
    );
};
