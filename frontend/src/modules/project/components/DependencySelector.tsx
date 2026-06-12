import React, { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, CheckSquare, ChevronDown, ExternalLink, FileText, Loader2, PackagePlus, Plus, RefreshCw, Search, ShieldCheck, ToggleLeft, ToggleRight, X } from 'lucide-react';
import { projectClient } from '@/modules/project/api/projectClient';
import { compareSemVer } from '@/utils/modHelpers';
import { theme } from '@/styles/theme';
import { BACKEND_URL } from '@/utils/api';
import type { DependencySource, DependencyType, ExternalProjectFile, ExternalProjectReference, Project, ProjectDependency, ProjectVersion } from '@/types';
import { VersionRelationKind } from '@/types';
import { useScrollLock } from '@/hooks/useScrollLock';
import { dependencyProjectKey, getDependencyType, isExternalDependency, isOptionalDependency, normalizeDependencyReference } from '../utils/dependencyEntries';
import { useToast } from '@/components/ui/Toast';

type DependencyMeta = { title: string; author: string; icon: string; source?: string; url?: string };
type DropdownOption<T extends string> = { value: T; label: string; detail?: string };

interface DependencySelectorProps {
    selectedDeps: ProjectDependency[] | string[];
    onChange: (deps: any[]) => void;
    targetGameVersion?: string;
    label?: string;
    mode?: VersionRelationKind;
    previousDependencies?: ProjectDependency[];
    currentProjectId?: string;
    isModpack?: boolean;
    disabled?: boolean;
}

const createUuid = () => {
    if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
        return crypto.randomUUID();
    }
    return `dep-${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const getIconUrl = (path?: string) => {
    if (!path) return '/assets/favicon.svg';
    return path.startsWith('http') ? path : `${BACKEND_URL}${path}`;
};

const normalizeLookup = (value?: string) => (value || '').toLowerCase().replace(/[^a-z0-9]+/g, '');

const getCurseForgeProjectSlug = (value: string): string | null => {
    try {
        const parsed = new URL(value);
        const host = parsed.hostname.toLowerCase();
        const path = parsed.pathname.toLowerCase();
        if (!(host === 'curseforge.com' || host.endsWith('.curseforge.com'))) return null;
        if (!path.startsWith('/hytale/') || !path.includes('/mods/')) return null;
        const segments = parsed.pathname.split('/').filter(Boolean);
        const modsIndex = segments.findIndex(segment => segment.toLowerCase() === 'mods');
        const slug = modsIndex >= 0 ? segments[modsIndex + 1] : '';
        return slug || null;
    } catch {
        return null;
    }
};

const getSourceLabel = (source?: string) => {
    switch (source) {
        case 'CURSEFORGE': return 'CurseForge';
        case 'GITHUB': return 'GitHub';
        case 'WEBSITE': return 'Website';
        case 'OTHER': return 'External';
        case 'MODTALE': return 'Modtale';
        default: return 'External';
    }
};

const EXTERNAL_SOURCE_OPTIONS: DropdownOption<DependencySource | ''>[] = [
    { value: '', label: 'Auto-detect source', detail: 'Let Modtale infer the service from the URL' },
    { value: 'CURSEFORGE', label: 'CurseForge', detail: 'Hytale project or file page' },
    { value: 'GITHUB', label: 'GitHub', detail: 'Repository, release, or raw file URL' },
    { value: 'WEBSITE', label: 'Website', detail: 'Public Hytale project page' },
    { value: 'OTHER', label: 'Other', detail: 'Another public Hytale source' }
];

const DEPENDENCY_TYPE_OPTIONS: DropdownOption<DependencyType>[] = [
    { value: 'REQUIRED', label: 'Required' },
    { value: 'OPTIONAL', label: 'Optional' },
    { value: 'EMBEDDED', label: 'Embedded' }
];

const CustomDropdown = <T extends string>({
    value,
    options,
    onChange,
    disabled,
    className = '',
    buttonClassName = ''
}: {
    value: T;
    options: DropdownOption<T>[];
    onChange: (value: T) => void;
    disabled?: boolean;
    className?: string;
    buttonClassName?: string;
}) => {
    const [open, setOpen] = useState(false);
    const selected = options.find(option => option.value === value) || options[0];

    return (
        <div className={`relative ${className}`} onBlur={event => {
            if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
                setOpen(false);
            }
        }}>
            <button
                type="button"
                disabled={disabled}
                onClick={() => setOpen(current => !current)}
                className={`w-full border ${theme.colors.border} ${theme.colors.bgBase} ${theme.colors.textPrimary} rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-modtale-accent flex items-center justify-between gap-3 disabled:opacity-60 disabled:cursor-not-allowed ${buttonClassName}`}
                aria-haspopup="listbox"
                aria-expanded={open}
            >
                <span className="min-w-0 text-left">
                    <span className="block truncate font-bold">{selected?.label || 'Select'}</span>
                    {selected?.detail && <span className={`block truncate text-xs font-normal ${theme.colors.textMuted}`}>{selected.detail}</span>}
                </span>
                <ChevronDown className={`w-4 h-4 shrink-0 ${theme.colors.textMuted} transition-transform ${open ? 'rotate-180' : ''}`} />
            </button>
            {open && (
                <div
                    role="listbox"
                    className={`absolute z-[120] mt-2 max-h-56 w-full overflow-y-auto rounded-xl border ${theme.colors.border} ${theme.colors.bgSurface} shadow-xl p-1`}
                >
                    {options.map(option => {
                        const selectedOption = option.value === value;
                        return (
                            <button
                                key={option.value || option.label}
                                type="button"
                                role="option"
                                aria-selected={selectedOption}
                                onClick={() => {
                                    onChange(option.value);
                                    setOpen(false);
                                }}
                                className={`w-full rounded-lg px-3 py-2 text-left text-sm transition-colors ${selectedOption ? 'bg-modtale-accent text-white' : `${theme.colors.textPrimary} ${theme.colors.bgSurfaceHover}`}`}
                            >
                                <span className="block truncate font-bold">{option.label}</span>
                                {option.detail && <span className={`block truncate text-xs ${selectedOption ? 'text-white/75' : theme.colors.textMuted}`}>{option.detail}</span>}
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
};

const buildModtaleDependency = (project: Project, versionNumber: string, dependencyType: DependencyType): ProjectDependency => ({
    id: createUuid(),
    projectId: project.id,
    projectTitle: project.title,
    versionNumber,
    dependencyType,
    source: 'MODTALE'
});

const cloneDependencyForForm = (dependency: ProjectDependency, forceRequired = false): ProjectDependency => ({
    id: dependency.id || createUuid(),
    projectId: dependency.projectId,
    projectTitle: dependency.projectTitle,
    versionNumber: dependency.versionNumber,
    dependencyType: forceRequired ? 'REQUIRED' : getDependencyType(dependency),
    source: dependency.source || 'MODTALE',
    externalId: dependency.externalId,
    externalUrl: dependency.externalUrl,
    externalFileUrl: dependency.externalFileUrl,
    externalFileName: dependency.externalFileName,
    cachedFileUrl: dependency.cachedFileUrl,
    hytaleProjectConfirmed: dependency.hytaleProjectConfirmed
});

const DependencyPrompt = ({
    projectTitle,
    dependencies,
    onAdd,
    onClose
}: {
    projectTitle: string;
    dependencies: ProjectDependency[];
    onAdd: () => void;
    onClose: () => void;
}) => {
    useScrollLock(true);
    return (
        <div className={theme.components.modalOverlay}>
            <div className="fixed top-[50%] left-[50%] translate-x-[-50%] translate-y-[-50%] w-[min(92vw,34rem)] max-h-[85dvh] flex flex-col z-[100] bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 shadow-xl rounded-3xl overflow-hidden">
                <div className="p-5 flex items-start justify-between gap-4 border-b border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-slate-800/95">
                    <div>
                        <h3 className={`text-lg font-black ${theme.colors.textPrimary}`}>Add Dependencies</h3>
                        <p className={`text-sm ${theme.colors.textMuted} mt-1`}>{projectTitle} needs {dependencies.length} project{dependencies.length === 1 ? '' : 's'} that are not in this pack yet.</p>
                    </div>
                    <button type="button" onClick={onClose} className="p-2 rounded-full hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
                </div>
                <div className="p-4 space-y-2 overflow-y-auto">
                    {dependencies.map(dep => (
                        <div key={`${dep.projectId}:${dep.versionNumber}`} className={`p-3 rounded-xl border ${theme.colors.border} ${theme.colors.bgBase} flex items-center justify-between gap-3`}>
                            <div className="min-w-0">
                                <div className={`font-bold ${theme.colors.textPrimary} truncate`}>{dep.projectTitle || dep.projectId}</div>
                                <div className={`text-xs ${theme.colors.textMuted} font-mono`}>v{dep.versionNumber}</div>
                            </div>
                            <span className={`text-[10px] font-black px-2 py-1 rounded-lg ${isOptionalDependency(dep) ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300' : 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300'}`}>
                                {getDependencyType(dep)}
                            </span>
                        </div>
                    ))}
                </div>
                <div className="p-4 flex justify-end gap-3 border-t border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-slate-800/95">
                    <button type="button" onClick={onClose} className={`px-4 py-2 font-bold rounded-lg ${theme.colors.textMuted} ${theme.colors.bgSurfaceHover}`}>Skip</button>
                    <button type="button" onClick={onAdd} className={`px-5 py-2 font-bold rounded-lg ${theme.components.buttonPrimary} flex items-center gap-2`}><PackagePlus className="w-4 h-4" /> Add All</button>
                </div>
            </div>
        </div>
    );
};

export const DependencySelector: React.FC<DependencySelectorProps> = ({
    selectedDeps,
    onChange,
    targetGameVersion,
    label,
    mode = VersionRelationKind.DEPENDENCY,
    previousDependencies,
    currentProjectId,
    isModpack = false,
    disabled
}) => {
    const [search, setSearch] = useState('');
    const [results, setResults] = useState<Project[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedProject, setSelectedProject] = useState<Project | null>(null);
    const [loadingProjectVersions, setLoadingProjectVersions] = useState(false);
    const [showIncompatibleVersions, setShowIncompatibleVersions] = useState(false);
    const [showAlphaBeta, setShowAlphaBeta] = useState(false);
    const [dependencyType, setDependencyType] = useState<DependencyType>('REQUIRED');
    const [showPreviousImport, setShowPreviousImport] = useState(false);
    const [pendingPrompt, setPendingPrompt] = useState<{ projectTitle: string; dependencies: ProjectDependency[]; baseDeps: ProjectDependency[] } | null>(null);
    const [projectCache, setProjectCache] = useState<Record<string, Project>>({});
    const [metaCache, setMetaCache] = useState<Record<string, DependencyMeta>>({});
    const [showExternalModal, setShowExternalModal] = useState(false);
    const [externalTitle, setExternalTitle] = useState('');
    const [externalVersion, setExternalVersion] = useState('');
    const [externalUrl, setExternalUrl] = useState('');
    const [externalSource, setExternalSource] = useState<DependencySource | ''>('');
    const [externalResolved, setExternalResolved] = useState<ExternalProjectReference | null>(null);
    const [externalSelectedFileId, setExternalSelectedFileId] = useState('');
    const [externalConfirmed, setExternalConfirmed] = useState(false);
    const [resolvingExternal, setResolvingExternal] = useState(false);
    const [externalError, setExternalError] = useState<string | null>(null);
    const [externalSuggestions, setExternalSuggestions] = useState<Project[]>([]);
    const [loadingExternalSuggestions, setLoadingExternalSuggestions] = useState(false);
    const { showToast } = useToast();

    const isIncompatibilityMode = mode === VersionRelationKind.INCOMPATIBILITY;
    const dependencies = useMemo(() => (isIncompatibilityMode ? [] : (selectedDeps as ProjectDependency[]).map(normalizeDependencyReference)), [isIncompatibilityMode, selectedDeps]);
    const incompatibleIds = useMemo(() => (isIncompatibilityMode ? (selectedDeps as string[]) : []), [isIncompatibilityMode, selectedDeps]);
    const effectiveLabel = label ?? (isIncompatibilityMode ? 'Incompatible Mods' : 'Dependencies');
    const selectedProjectIds = useMemo(() => new Set(isIncompatibilityMode ? incompatibleIds : dependencies.map(dep => dep.projectId)), [dependencies, incompatibleIds, isIncompatibilityMode]);

    useScrollLock(Boolean(selectedProject || pendingPrompt || showExternalModal));

    useEffect(() => {
        if (isIncompatibilityMode) return;

        const missing = dependencies
            .filter(dep => dep.source === 'MODTALE' && !metaCache[dep.projectId])
            .map(dep => dep.projectId);
        const externalCache: Record<string, DependencyMeta> = {};
        dependencies
            .filter(dep => isExternalDependency(dep) && !metaCache[dep.projectId])
            .forEach(dep => {
                externalCache[dep.projectId] = { title: dep.projectTitle, author: getSourceLabel(dep.source), icon: '', source: dep.source, url: dep.externalUrl };
            });

        if (Object.keys(externalCache).length) {
            setMetaCache(prev => ({ ...prev, ...externalCache }));
        }
        if (!missing.length) return;

        let cancelled = false;
        Promise.all([...new Set(missing)].map(async id => {
            try {
                const data = await projectClient.getDependencyMeta(id);
                return [id, { title: data.title, author: data.author, icon: data.icon }] as const;
            } catch {
                return [id, { title: id, author: 'Unknown', icon: '' }] as const;
            }
        })).then(entries => {
            if (!cancelled) setMetaCache(prev => ({ ...prev, ...Object.fromEntries(entries) }));
        });
        return () => { cancelled = true; };
    }, [dependencies, isIncompatibilityMode, metaCache]);

    useEffect(() => {
        if (isIncompatibilityMode) return;
        const missing = dependencies
            .filter(dep => dep.source === 'MODTALE' && !projectCache[dep.projectId])
            .map(dep => dep.projectId);
        if (!missing.length) return;

        let cancelled = false;
        Promise.all([...new Set(missing)].map(async id => {
            try {
                return [id, await projectClient.getProject(id)] as const;
            } catch {
                return null;
            }
        })).then(entries => {
            if (cancelled) return;
            const next = Object.fromEntries(entries.filter((entry): entry is readonly [string, Project] => Boolean(entry)));
            if (Object.keys(next).length) setProjectCache(prev => ({ ...prev, ...next }));
        });
        return () => { cancelled = true; };
    }, [dependencies, isIncompatibilityMode, projectCache]);

    useEffect(() => {
        const timer = setTimeout(async () => {
            if (search.length < 2 || disabled) {
                setResults([]);
                return;
            }
            setLoading(true);
            try {
                const data = await projectClient.searchProjects(search);
                setResults(data.filter((project: Project) => project.classification !== 'MODPACK' && project.classification !== 'SAVE' && project.id !== currentProjectId));
            } catch {
                setResults([]);
            } finally {
                setLoading(false);
            }
        }, 300);
        return () => clearTimeout(timer);
    }, [search, currentProjectId, disabled]);

    useEffect(() => {
        if (!selectedProject || isIncompatibilityMode) return;
        const compatible = (selectedProject.versions || []).filter(v => !targetGameVersion || v.gameVersions?.includes(targetGameVersion));
        setShowAlphaBeta(compatible.length > 0 && !compatible.some(v => !v.channel || v.channel === 'RELEASE'));
    }, [selectedProject, targetGameVersion, isIncompatibilityMode]);

    useEffect(() => {
        if (!showExternalModal || externalTitle.trim().length < 2) {
            setExternalSuggestions([]);
            return;
        }

        let cancelled = false;
        setLoadingExternalSuggestions(true);
        const timer = setTimeout(async () => {
            try {
                const data = await projectClient.searchProjects(externalTitle.trim());
                const titleKey = normalizeLookup(externalTitle);
                const urlSlug = normalizeLookup(getCurseForgeProjectSlug(externalUrl) || '');
                const suggestions = data
                    .filter((project: Project) => project.classification !== 'MODPACK' && project.classification !== 'SAVE')
                    .filter((project: Project) => {
                        const projectTitle = normalizeLookup(project.title);
                        const projectSlug = normalizeLookup(project.slug);
                        return projectTitle === titleKey || projectSlug === titleKey || (urlSlug && projectSlug === urlSlug);
                    })
                    .slice(0, 3);
                if (!cancelled) setExternalSuggestions(suggestions);
            } catch {
                if (!cancelled) setExternalSuggestions([]);
            } finally {
                if (!cancelled) setLoadingExternalSuggestions(false);
            }
        }, 250);
        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [showExternalModal, externalTitle, externalUrl]);

    useEffect(() => {
        setExternalResolved(null);
        setExternalSelectedFileId('');
    }, [externalUrl]);

    const availableVersions = useMemo(() => {
        if (!selectedProject?.versions) return [];
        return [...selectedProject.versions].sort((a, b) => compareSemVer(b.versionNumber, a.versionNumber));
    }, [selectedProject]);

    const filteredVersions = availableVersions.filter(version => {
        const versionMatch = !targetGameVersion || version.gameVersions?.includes(targetGameVersion);
        const channelMatch = showAlphaBeta || !version.channel || version.channel === 'RELEASE';
        return (showIncompatibleVersions || versionMatch) && channelMatch;
    });

    const conflictWarnings = useMemo(() => {
        if (isIncompatibilityMode) return [];
        const internalDeps = dependencies.filter(dep => dep.source === 'MODTALE');
        const selectedIds = new Set(internalDeps.map(dep => dep.projectId));
        const warnings = new Map<string, { from: ProjectDependency; toId: string }>();

        for (const dep of internalDeps) {
            const project = projectCache[dep.projectId];
            const version = project?.versions?.find(candidate => candidate.versionNumber === dep.versionNumber);
            for (const incompatibleId of version?.incompatibleProjectIds || []) {
                if (!selectedIds.has(incompatibleId)) continue;
                const key = [dep.projectId, incompatibleId].sort().join(':');
                if (!warnings.has(key)) warnings.set(key, { from: dep, toId: incompatibleId });
            }
        }

        return [...warnings.values()].map(({ from, toId }) => ({
            from,
            toId,
            toTitle: metaCache[toId]?.title || projectCache[toId]?.title || toId
        }));
    }, [dependencies, isIncompatibilityMode, metaCache, projectCache]);

    const externalDependencyWarnings = useMemo(() => {
        if (isIncompatibilityMode) return [];
        return dependencies.filter(isExternalDependency).map(dep => ({
            dependency: dep,
            sourceLabel: getSourceLabel(dep.source)
        }));
    }, [dependencies, isIncompatibilityMode]);

    const addDependency = (dependency: ProjectDependency, sourceProject?: Project, sourceVersion?: ProjectVersion) => {
        if (disabled) return;
        const normalized = normalizeDependencyReference(dependency);
        if (selectedProjectIds.has(normalized.projectId)) {
            showToast('Project already added.', 'info');
            return;
        }
        const nextDeps = [...dependencies, normalized];

        const missingDependencies = !isIncompatibilityMode && sourceVersion?.dependencies
            ? sourceVersion.dependencies
                .map(dep => cloneDependencyForForm(dep, isModpack))
                .filter(dep => !nextDeps.some(existing => dependencyProjectKey(existing) === dependencyProjectKey(dep)))
                .filter(dep => dep.projectId !== currentProjectId)
            : [];

        onChange(nextDeps);
        setSearch('');
        setResults([]);
        setSelectedProject(null);
        setDependencyType('REQUIRED');

        if (sourceProject) {
            setMetaCache(prev => ({ ...prev, [sourceProject.id]: { title: sourceProject.title, author: sourceProject.author, icon: sourceProject.imageUrl } }));
            setProjectCache(prev => ({ ...prev, [sourceProject.id]: sourceProject }));
        }
        if (missingDependencies.length) {
            setPendingPrompt({ projectTitle: normalized.projectTitle, dependencies: missingDependencies, baseDeps: nextDeps });
        }
    };

    const addIncompatibleProject = (project: Project) => {
        if (selectedProjectIds.has(project.id)) {
            showToast('Project already added.', 'info');
            return;
        }
        onChange([...incompatibleIds, project.id]);
        setSearch('');
        setResults([]);
        setMetaCache(prev => ({ ...prev, [project.id]: { title: project.title, author: project.author, icon: project.imageUrl } }));
    };

    const openVersionPicker = async (project: Project) => {
        if (disabled) return;
        if (isIncompatibilityMode) {
            addIncompatibleProject(project);
            return;
        }
        setLoadingProjectVersions(true);
        try {
            const fullProject = project.versions ? project : await projectClient.getProject(project.id);
            setSelectedProject({ ...fullProject, versions: fullProject.versions || [] });
        } catch {
            setSelectedProject({ ...project, versions: project.versions || [] });
        } finally {
            setLoadingProjectVersions(false);
        }
    };

    const removeSelected = (index: number) => {
        if (disabled) return;
        const next = [...selectedDeps];
        next.splice(index, 1);
        onChange(next);
    };

    const cycleDependencyType = (index: number, nextType: DependencyType) => {
        if (disabled || isModpack || isIncompatibilityMode) return;
        const next = [...dependencies];
        next[index] = { ...next[index], dependencyType: nextType };
        onChange(next);
    };

    const resolveExternalDetails = async () => {
        if (!externalUrl.trim()) {
            setExternalError('Enter an external project URL.');
            return null;
        }

        setResolvingExternal(true);
        setExternalError(null);
        try {
            const resolved = await projectClient.resolveExternalProject(externalUrl.trim(), externalSource || undefined);
            setExternalResolved(resolved);
            setExternalSource(resolved.source);
            setExternalTitle(current => current.trim() || resolved.title || '');
            setExternalVersion(current => current.trim() || resolved.versionNumber || 'latest');
            if (resolved.files?.length) {
                const firstFile = resolved.files[0];
                setExternalSelectedFileId(firstFile.id || '');
                if (firstFile.versionNumber) setExternalVersion(firstFile.versionNumber);
            }
            return resolved;
        } catch (error: any) {
            setExternalError(error?.response?.data?.message || 'Could not resolve that external project.');
            return null;
        } finally {
            setResolvingExternal(false);
        }
    };

    const selectedExternalFile = useMemo(() => {
        if (!externalResolved?.files?.length || !externalSelectedFileId) return null;
        return externalResolved.files.find(file => (file.id || file.downloadUrl || file.displayName || file.fileName || '') === externalSelectedFileId) || null;
    }, [externalResolved, externalSelectedFileId]);

    const externalFileOptions = useMemo<DropdownOption<string>[]>(() => (
        (externalResolved?.files || []).map((file: ExternalProjectFile) => ({
            value: file.id || file.downloadUrl || file.displayName || file.fileName || '',
            label: file.displayName || file.fileName || file.id || 'External file',
            detail: file.versionNumber ? `Version ${file.versionNumber}` : file.fileName || file.releaseType
        }))
    ), [externalResolved]);

    const addExternalReference = async () => {
        const resolved = externalResolved || await resolveExternalDetails();
        if (!resolved) return;

        const hytaleProjectConfirmed = resolved.hytaleProjectConfirmed || externalConfirmed;
        if (!hytaleProjectConfirmed) {
            setExternalError('Confirm this external project is for Hytale before adding it.');
            return;
        }

        const title = externalTitle.trim() || resolved.title;
        const version = selectedExternalFile?.versionNumber || externalVersion.trim() || resolved.versionNumber || 'latest';
        if (!title || !version) {
            setExternalError('Title and version are required.');
            return;
        }

        const source = resolved.source;
        if (source === 'CURSEFORGE' && !selectedExternalFile?.downloadUrl) {
            setExternalError('Choose a CurseForge file so Modtale can cache it.');
            return;
        }

        const projectId = `${source.toLowerCase()}:${resolved.externalId}`;
        if (selectedProjectIds.has(projectId)) {
            setExternalError('That external reference is already added.');
            return;
        }
        let referenceUrl = resolved.externalUrl;
        if (source === 'CURSEFORGE' && selectedExternalFile?.id) {
            const slug = getCurseForgeProjectSlug(resolved.externalUrl || externalUrl);
            if (slug) referenceUrl = `https://www.curseforge.com/hytale/mods/${slug}/files/${selectedExternalFile.id}`;
        }
        const dependency: ProjectDependency = {
            id: createUuid(),
            projectId,
            projectTitle: title,
            versionNumber: version,
            dependencyType: isModpack ? 'REQUIRED' : dependencyType,
            source,
            externalId: resolved.externalId,
            externalUrl: referenceUrl,
            externalFileUrl: selectedExternalFile?.downloadUrl,
            externalFileName: selectedExternalFile?.fileName || selectedExternalFile?.displayName,
            hytaleProjectConfirmed
        };
        onChange([...dependencies, dependency]);
        setMetaCache(prev => ({ ...prev, [projectId]: { title: dependency.projectTitle, author: getSourceLabel(source), icon: resolved.iconUrl || '', source, url: dependency.externalUrl } }));
        setShowExternalModal(false);
        setExternalTitle('');
        setExternalVersion('');
        setExternalUrl('');
        setExternalSource('');
        setExternalResolved(null);
        setExternalSelectedFileId('');
        setExternalConfirmed(false);
        setExternalError(null);
    };

    const selectedCount = selectedDeps.length;

    return (
        <div className={`space-y-4 border ${theme.colors.border} rounded-2xl p-6 ${theme.colors.bgSurface} ${disabled ? 'opacity-70' : ''}`}>
            {pendingPrompt && (
                <DependencyPrompt
                    projectTitle={pendingPrompt.projectTitle}
                    dependencies={pendingPrompt.dependencies}
                    onClose={() => setPendingPrompt(null)}
                    onAdd={() => {
                        const existingKeys = new Set(pendingPrompt.baseDeps.map(dependencyProjectKey));
                        const toAdd = pendingPrompt.dependencies.filter(dep => !existingKeys.has(dependencyProjectKey(dep)));
                        onChange([...pendingPrompt.baseDeps, ...toAdd]);
                        setPendingPrompt(null);
                    }}
                />
            )}

            {selectedProject && !disabled && !isIncompatibilityMode && (
                <div className={theme.components.modalOverlay}>
                    <div className="fixed top-[50%] left-[50%] translate-x-[-50%] translate-y-[-50%] w-full max-w-md max-h-[85dvh] flex flex-col z-[100] bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 shadow-xl rounded-3xl overflow-hidden">
                        <div className="p-5 flex justify-between items-start border-b border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-slate-800/95">
                            <div>
                                <h3 className={`text-lg font-black ${theme.colors.textPrimary}`}>Select Version</h3>
                                <p className={`text-xs ${theme.colors.textMuted} mt-1 truncate max-w-[20rem]`}>{selectedProject.title}</p>
                            </div>
                            <button type="button" onClick={() => setSelectedProject(null)} className="p-2 rounded-full hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
                        </div>

                        <div className="p-4 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-white/10 space-y-3">
                            <div className="flex items-center justify-between">
                                <span className={`text-xs font-bold ${theme.colors.textMuted} uppercase tracking-wider`}>Show Alpha/Beta</span>
                                <button type="button" onClick={() => setShowAlphaBeta(!showAlphaBeta)} className={`transition-colors ${showAlphaBeta ? theme.colors.accent : theme.colors.textMuted}`}>
                                    {showAlphaBeta ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}
                                </button>
                            </div>
                            {!isModpack && (
                                <div className="grid grid-cols-3 gap-2">
                                    {(['REQUIRED', 'OPTIONAL', 'EMBEDDED'] as DependencyType[]).map(type => (
                                        <button
                                            key={type}
                                            type="button"
                                            onClick={() => setDependencyType(type)}
                                            className={`px-2 py-2 rounded-lg text-[10px] font-black border transition-colors ${dependencyType === type ? 'bg-modtale-accent text-white border-modtale-accent' : `${theme.colors.bgSurface} ${theme.colors.border} ${theme.colors.textMuted}`}`}
                                        >
                                            {type}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>

                        <div className="p-4 overflow-y-auto flex-1 bg-slate-50/50 dark:bg-slate-900/50 space-y-2">
                            {loadingProjectVersions ? (
                                <div className={`p-4 text-center text-xs ${theme.colors.textMuted} flex items-center justify-center gap-2`}>
                                    <Loader2 className="w-4 h-4 animate-spin" /> Loading versions...
                                </div>
                            ) : filteredVersions.length > 0 ? filteredVersions.map(version => {
                                const isCompatible = !targetGameVersion || version.gameVersions?.includes(targetGameVersion);
                                return (
                                    <button
                                        key={version.id}
                                        type="button"
                                        onClick={() => addDependency(buildModtaleDependency(selectedProject, version.versionNumber, isModpack ? 'REQUIRED' : dependencyType), selectedProject, version)}
                                        className={`w-full text-left px-4 py-3 flex justify-between items-center rounded-xl transition-all shadow-sm border ${!isCompatible ? 'bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-900/30' : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-white/10 hover:border-modtale-accent/40 dark:hover:border-modtale-accent/50'}`}
                                    >
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <span className={`font-bold text-sm ${!isCompatible ? theme.colors.dangerText : theme.colors.textPrimary}`}>{version.versionNumber}</span>
                                                {version.channel && version.channel !== 'RELEASE' && (
                                                    <span className={`text-[9px] font-bold px-1.5 rounded border ${version.channel === 'BETA' ? 'text-blue-500 border-blue-200 bg-blue-50 dark:bg-blue-900/20 dark:border-blue-800' : 'text-orange-500 border-orange-200 bg-orange-50 dark:bg-orange-900/20 dark:border-orange-800'}`}>{version.channel}</span>
                                                )}
                                            </div>
                                            <div className={`text-xs ${theme.colors.textMuted}`}>For {version.gameVersions?.join(', ')}</div>
                                        </div>
                                        {isCompatible ? <Plus className={`w-4 h-4 ${theme.colors.accent}`} /> : <AlertTriangle className="w-4 h-4 text-red-500" />}
                                    </button>
                                );
                            }) : (
                                <div className={`p-4 text-center text-xs ${theme.colors.textMuted} italic`}>No compatible versions found with current filters.</div>
                            )}
                        </div>
                        {targetGameVersion && (
                            <div className="p-4 bg-slate-50 dark:bg-slate-800/95 border-t border-slate-200 dark:border-white/10 text-center">
                                <button type="button" onClick={() => setShowIncompatibleVersions(!showIncompatibleVersions)} className={`text-xs font-bold ${theme.colors.accent} hover:underline`}>
                                    {showIncompatibleVersions ? 'Hide' : 'Show'} incompatible versions
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {showExternalModal && !disabled && !isIncompatibilityMode && (
                <div className={theme.components.modalOverlay}>
                    <div className="fixed top-[50%] left-[50%] translate-x-[-50%] translate-y-[-50%] w-[min(92vw,34rem)] max-h-[85dvh] flex flex-col z-[100] bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 shadow-xl rounded-3xl overflow-hidden">
                        <div className="p-5 flex items-start justify-between gap-4 border-b border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-slate-800/95">
                            <div>
                                <h3 className={`text-lg font-black ${theme.colors.textPrimary}`}>External Reference</h3>
                                <p className={`text-sm ${theme.colors.textMuted} mt-1`}>CurseForge, GitHub, or another Hytale source.</p>
                            </div>
                            <button type="button" onClick={() => setShowExternalModal(false)} className="p-2 rounded-full hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
                        </div>
                        <div className="p-5 space-y-4 overflow-y-auto">
                            <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto] gap-3">
                                <input value={externalUrl} onChange={event => setExternalUrl(event.target.value)} className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-modtale-accent ${theme.colors.textPrimary}`} placeholder="Paste an external project URL" />
                                <button type="button" onClick={resolveExternalDetails} disabled={resolvingExternal || !externalUrl.trim()} className={`px-4 py-3 rounded-xl font-black text-sm ${theme.components.buttonSecondary} flex items-center justify-center gap-2 disabled:opacity-60`}>
                                    {resolvingExternal ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
                                    Fetch
                                </button>
                            </div>
                            <CustomDropdown
                                value={externalSource}
                                options={EXTERNAL_SOURCE_OPTIONS}
                                onChange={setExternalSource}
                            />

                            {externalResolved && (
                                <div className={`rounded-xl border ${theme.colors.border} ${theme.colors.bgBase} p-3 flex items-start gap-3`}>
                                    <div className="w-10 h-10 rounded-lg bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-300 flex items-center justify-center shrink-0 overflow-hidden">
                                        {externalResolved.iconUrl ? <img src={externalResolved.iconUrl} alt="" className="w-full h-full object-cover" /> : <ExternalLink className="w-4 h-4" />}
                                    </div>
                                    <div className="min-w-0 flex-1">
                                        <div className={`font-black ${theme.colors.textPrimary} truncate`}>{externalResolved.title}</div>
                                        <div className={`text-xs ${theme.colors.textMuted}`}>{getSourceLabel(externalResolved.source)} · {externalResolved.externalId}</div>
                                        {externalResolved.summary && <p className={`text-xs ${theme.colors.textMuted} mt-1 line-clamp-2`}>{externalResolved.summary}</p>}
                                    </div>
                                </div>
                            )}

                            {externalFileOptions.length > 0 && (
                                <CustomDropdown
                                    value={externalSelectedFileId || externalFileOptions[0]?.value || ''}
                                    options={externalFileOptions}
                                    onChange={nextFileId => {
                                        const nextFile = externalResolved?.files?.find(file => (file.id || file.downloadUrl || file.displayName || file.fileName || '') === nextFileId);
                                        setExternalSelectedFileId(nextFileId);
                                        if (nextFile?.versionNumber) setExternalVersion(nextFile.versionNumber);
                                    }}
                                />
                            )}

                            {!isModpack && (
                                <div className="grid grid-cols-3 gap-2">
                                    {(['REQUIRED', 'OPTIONAL', 'EMBEDDED'] as DependencyType[]).map(type => (
                                        <button
                                            key={type}
                                            type="button"
                                            onClick={() => setDependencyType(type)}
                                            className={`px-2 py-2 rounded-lg text-[10px] font-black border transition-colors ${dependencyType === type ? 'bg-modtale-accent text-white border-modtale-accent' : `${theme.colors.bgSurface} ${theme.colors.border} ${theme.colors.textMuted}`}`}
                                        >
                                            {type}
                                        </button>
                                    ))}
                                </div>
                            )}

                            <input value={externalTitle} onChange={event => setExternalTitle(event.target.value)} className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-modtale-accent ${theme.colors.textPrimary}`} placeholder="Project title" />
                            <input value={externalVersion} onChange={event => setExternalVersion(event.target.value)} className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-modtale-accent ${theme.colors.textPrimary}`} placeholder="Version" />
                            {externalResolved && !externalResolved.hytaleProjectConfirmed && (
                                <label className={`flex items-start gap-3 rounded-xl border ${theme.colors.border} ${theme.colors.bgBase} p-3 text-sm ${theme.colors.textSecondary}`}>
                                    <input type="checkbox" checked={externalConfirmed} onChange={event => setExternalConfirmed(event.target.checked)} className="mt-1" />
                                    <span>I confirm this external project is for Hytale and is safe to reference from this modpack.</span>
                                </label>
                            )}

                            {(loadingExternalSuggestions || externalSuggestions.length > 0) && (
                                <div className={`rounded-xl border ${theme.colors.border} ${theme.colors.bgBase} overflow-hidden`}>
                                    <div className={`px-3 py-2 text-[10px] font-black uppercase ${theme.colors.textMuted} ${theme.colors.bgSurfaceAlt}`}>Modtale Matches</div>
                                    {loadingExternalSuggestions ? (
                                        <div className={`p-3 text-xs ${theme.colors.textMuted} flex items-center gap-2`}><Loader2 className="w-3 h-3 animate-spin" /> Searching...</div>
                                    ) : externalSuggestions.map(project => (
                                        <button key={project.id} type="button" onClick={() => { setShowExternalModal(false); void openVersionPicker(project); }} className={`w-full p-3 flex items-center justify-between gap-3 text-left ${theme.colors.bgSurfaceHover}`}>
                                            <div className="min-w-0">
                                                <div className={`font-bold text-sm ${theme.colors.textPrimary} truncate`}>{project.title}</div>
                                                <div className={`text-xs ${theme.colors.textMuted}`}>Use the Modtale project instead</div>
                                            </div>
                                            <ChevronDown className={`w-4 h-4 -rotate-90 ${theme.colors.accent}`} />
                                        </button>
                                    ))}
                                </div>
                            )}

                            {externalError && (
                                <div className={`p-3 rounded-xl border ${theme.colors.dangerBorder} ${theme.colors.dangerBg} ${theme.colors.dangerText} text-xs font-bold flex items-start gap-2`}>
                                    <AlertTriangle className="w-4 h-4 shrink-0" /> {externalError}
                                </div>
                            )}
                        </div>
                        <div className="p-4 flex justify-end gap-3 border-t border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-slate-800/95">
                            <button type="button" onClick={() => setShowExternalModal(false)} className={`px-4 py-2 font-bold rounded-lg ${theme.colors.textMuted} ${theme.colors.bgSurfaceHover}`}>Cancel</button>
                            <button type="button" onClick={addExternalReference} className={`px-5 py-2 font-bold rounded-lg ${theme.components.buttonPrimary} flex items-center gap-2`}><ExternalLink className="w-4 h-4" /> Add Reference</button>
                        </div>
                    </div>
                </div>
            )}

            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <h3 className={`font-bold ${theme.colors.textPrimary} flex items-center gap-2 text-sm uppercase tracking-wide`}><Search className="w-4 h-4" /> {effectiveLabel}</h3>
                {!isIncompatibilityMode && (
                    <button type="button" disabled={disabled} onClick={() => setShowExternalModal(true)} className={`text-xs font-bold px-3 py-2 rounded-lg border ${theme.colors.border} ${theme.colors.bgBase} ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} flex items-center gap-2 ${disabled ? 'opacity-60 cursor-not-allowed' : ''}`}>
                        <ExternalLink className="w-3.5 h-3.5" /> Add External
                    </button>
                )}
            </div>

            {previousDependencies && previousDependencies.length > 0 && selectedCount === 0 && !disabled && !isIncompatibilityMode && (
                <div className="bg-blue-50 dark:bg-blue-900/10 border border-blue-100 dark:border-blue-900/20 p-4 rounded-xl flex items-center justify-between gap-4 animate-in fade-in">
                    <div className="flex items-center gap-3 min-w-0">
                        <RefreshCw className="w-5 h-5 text-blue-500 shrink-0" />
                        <div className="min-w-0">
                            <h4 className="text-sm font-bold text-blue-900 dark:text-blue-100">Updates Available</h4>
                            <p className="text-xs text-blue-700 dark:text-blue-300">Found {previousDependencies.length} projects from the previous release.</p>
                        </div>
                    </div>
                    <button type="button" onClick={() => setShowPreviousImport(true)} className="text-xs font-bold bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg transition-colors shadow-sm">Import</button>
                </div>
            )}

            {showPreviousImport && previousDependencies && (
                <div className={`rounded-xl border ${theme.colors.border} ${theme.colors.bgBase} overflow-hidden`}>
                    <div className={`p-3 flex justify-between items-center ${theme.colors.bgSurfaceAlt}`}>
                        <span className={`text-xs font-black uppercase ${theme.colors.textMuted}`}>Previous Dependencies</span>
                        <button type="button" onClick={() => setShowPreviousImport(false)} className={`${theme.colors.textMuted}`}><X className="w-4 h-4" /></button>
                    </div>
                    <div className="p-3 space-y-2">
                        {previousDependencies.map(dep => (
                            <div key={`${dep.projectId}:${dep.versionNumber}`} className={`p-3 rounded-lg border ${theme.colors.border} flex items-center justify-between gap-3`}>
                                <div className="min-w-0">
                                    <div className={`font-bold ${theme.colors.textPrimary} text-sm truncate`}>{dep.projectTitle || dep.projectId}</div>
                                    <div className={`text-xs ${theme.colors.textMuted} font-mono`}>v{dep.versionNumber}</div>
                                </div>
                                <button type="button" onClick={() => {
                                    const dependency = cloneDependencyForForm(dep, isModpack);
                                    if (!dependencies.some(existing => dependencyProjectKey(existing) === dependencyProjectKey(dependency))) {
                                        onChange([...dependencies, dependency]);
                                    }
                                }} className={`text-xs font-bold ${theme.colors.accent} hover:underline`}>Add</button>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            <div className="relative">
                <input type="text" disabled={disabled} value={search} onChange={event => setSearch(event.target.value)} className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl pl-10 pr-4 py-3 text-sm focus:ring-2 focus:ring-modtale-accent outline-none shadow-sm transition-all ${theme.colors.textPrimary} ${disabled ? `cursor-not-allowed ${theme.colors.bgSurfaceAlt}` : ''}`} placeholder="Search for projects..." />
                <Search className={`absolute left-3.5 top-3.5 w-4 h-4 ${theme.colors.textMuted}`} />
                {loading && <div className="absolute right-4 top-3.5"><Loader2 className={`w-4 h-4 animate-spin ${theme.colors.accent}`} /></div>}
            </div>

            {results.length > 0 && !disabled && (
                <div className={`max-h-56 overflow-y-auto ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-lg divide-y ${theme.colors.borderFaint}`}>
                    {results.map(project => (
                        <button key={project.id} type="button" onClick={event => { event.preventDefault(); void openVersionPicker(project); }} className={`w-full text-left px-4 py-3 ${theme.colors.bgSurfaceHover} flex justify-between items-center text-sm transition-colors group`}>
                            <div className="flex items-center gap-3 min-w-0">
                                <img src={getIconUrl(project.imageUrl)} className="w-8 h-8 rounded-md bg-slate-200 object-cover shrink-0" alt="" onError={event => event.currentTarget.src='/assets/favicon.svg'} />
                                <div className="min-w-0">
                                    <div className={`font-bold ${theme.colors.textPrimary} group-hover:${theme.colors.accent} truncate`}>{project.title}</div>
                                    <div className={`text-xs ${theme.colors.textMuted}`}>by {project.author}</div>
                                </div>
                            </div>
                            <Plus className={`w-5 h-5 text-slate-300 group-hover:${theme.colors.accent}`} />
                        </button>
                    ))}
                </div>
            )}

            {conflictWarnings.length > 0 && (
                <div className={`rounded-xl border ${theme.colors.warningBorder} ${theme.colors.warningBg} p-4 space-y-2`}>
                    <div className={`flex items-center gap-2 text-sm font-black ${theme.colors.warningText}`}>
                        <AlertTriangle className="w-4 h-4" /> Incompatible projects in this set
                    </div>
                    {conflictWarnings.map(warning => (
                        <div key={`${warning.from.projectId}:${warning.toId}`} className={`text-xs ${theme.colors.warningText}`}>
                            <strong>{warning.from.projectTitle}</strong> marks <strong>{warning.toTitle}</strong> as incompatible.
                        </div>
                    ))}
                </div>
            )}

            {externalDependencyWarnings.length > 0 && (
                <div className="rounded-xl border border-orange-200 dark:border-orange-900/40 bg-orange-50 dark:bg-orange-950/20 p-4 space-y-2">
                    <div className="flex items-center gap-2 text-sm font-black text-orange-800 dark:text-orange-200">
                        <AlertTriangle className="w-4 h-4" /> External service references
                    </div>
                    {externalDependencyWarnings.map(({ dependency, sourceLabel }) => (
                        <div key={dependency.id || dependency.projectId} className="text-xs text-orange-800 dark:text-orange-200">
                            <strong>{dependency.projectTitle || dependency.projectId}</strong> is an external {isModpack ? 'modpack entry' : 'dependency'} from <strong>{sourceLabel}</strong>
                            {dependency.externalFileUrl ? <>; its file is also sourced from <strong>{sourceLabel}</strong>.</> : <>.</>}
                        </div>
                    ))}
                </div>
            )}

            <div className="mt-6">
                <div className="flex justify-between items-center mb-3">
                    <h4 className={`text-xs font-bold uppercase ${theme.colors.textMuted}`}>Selected ({selectedCount})</h4>
                </div>
                {selectedCount === 0 ? (
                    <div className={`text-center p-8 border-2 border-dashed ${theme.colors.border} rounded-xl ${theme.colors.textMuted} text-sm italic`}>
                        {isIncompatibilityMode ? 'No incompatible mods added.' : 'No dependencies added.'}
                    </div>
                ) : (
                    <div className="grid grid-cols-1 gap-2">
                        {(isIncompatibilityMode ? incompatibleIds : dependencies).map((entry: string | ProjectDependency, index: number) => {
                            const dependency = typeof entry === 'string' ? null : entry;
                            const id = typeof entry === 'string' ? entry : entry.projectId;
                            const meta = metaCache[id];
                            const isExternal = dependency ? isExternalDependency(dependency) : false;
                            const depType = dependency ? getDependencyType(dependency) : 'REQUIRED';
                            return (
                                <div key={dependency?.id || id} className={`flex items-center justify-between ${theme.colors.bgBase} p-3 rounded-xl border ${theme.colors.border} text-sm shadow-sm group gap-3`}>
                                    <div className="flex items-center gap-3 overflow-hidden min-w-0">
                                        {!isIncompatibilityMode && (
                                            <div className={`p-1 shrink-0 rounded-lg ${depType === 'OPTIONAL' ? `${theme.colors.bgSurfaceAlt} ${theme.colors.textMuted}` : depType === 'EMBEDDED' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-400' : 'bg-amber-100 text-amber-600 dark:bg-amber-900/20'}`}>
                                                {depType === 'OPTIONAL' ? <FileText className="w-4 h-4" /> : depType === 'EMBEDDED' ? <CheckSquare className="w-4 h-4" /> : <ShieldCheck className="w-4 h-4" />}
                                            </div>
                                        )}
                                        {isExternal ? (
                                            <div className="w-8 h-8 rounded bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-300 flex items-center justify-center shrink-0">
                                                <ExternalLink className="w-4 h-4" />
                                            </div>
                                        ) : (
                                            <img src={getIconUrl(meta?.icon)} alt="" className={`w-8 h-8 rounded ${theme.colors.bgSurfaceAlt} object-cover shrink-0`} onError={event => event.currentTarget.src='/assets/favicon.svg'} />
                                        )}
                                        <div className="min-w-0">
                                            <div className={`font-bold ${theme.colors.textPrimary} truncate`}>{dependency?.projectTitle || meta?.title || id}</div>
                                            {isIncompatibilityMode ? (
                                                <div className={`text-xs ${theme.colors.textMuted}`}>Marked as incompatible</div>
                                            ) : (
                                                <div className={`text-xs ${theme.colors.textMuted} flex items-center gap-1.5 min-w-0`}>
                                                    <span className="truncate max-w-[110px]">{isExternal ? `External: ${getSourceLabel(dependency?.source)}` : `by ${meta?.author || '...'}`}</span>
                                                    <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-white/20"></span>
                                                    <span className={`font-mono ${theme.colors.bgSurfaceAlt} px-1.5 py-0.5 rounded`}>v{dependency?.versionNumber}</span>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2 shrink-0">
                                        {dependency && !isModpack && !isIncompatibilityMode && (
                                            <CustomDropdown
                                                value={depType}
                                                options={DEPENDENCY_TYPE_OPTIONS}
                                                onChange={nextType => cycleDependencyType(index, nextType)}
                                                disabled={disabled}
                                                className="w-32"
                                                buttonClassName="rounded-lg px-2 py-1.5 text-xs"
                                            />
                                        )}
                                        {dependency && isExternal && dependency.externalUrl && (
                                            <a href={dependency.externalUrl} target="_blank" rel="noreferrer" className={`p-2 rounded-lg ${theme.colors.textMuted} hover:${theme.colors.accent}`}><ExternalLink className="w-4 h-4" /></a>
                                        )}
                                        <button type="button" disabled={disabled} onClick={() => removeSelected(index)} className={`${theme.colors.textMuted} p-2 rounded-lg transition-colors ${disabled ? 'cursor-not-allowed opacity-50' : `hover:${theme.colors.dangerText} hover:${theme.colors.dangerBg}`}`}><X className="w-4 h-4" /></button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
};
