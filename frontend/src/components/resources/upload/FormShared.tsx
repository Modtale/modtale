import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useDropzone, type Accept } from 'react-dropzone';
import { UploadCloud, Check, ChevronDown, AlertCircle, CheckCircle2, Beaker, Zap } from 'lucide-react';
import { DependencySelector } from './DependencySelector';


export interface MetadataFormData {
    title: string;
    slug?: string;
    summary: string;
    description: string;
    category: string;
    tags: string[];
    links: Record<string, string>;
    repositoryUrl: string;
    iconFile: File | null;
    iconPreview: string | null;
    license?: string;
}

export interface VersionFormData {
    modIds: any[];
    channel?: 'RELEASE' | 'BETA' | 'ALPHA';
    versionNumber: string;
    gameVersions: string[];
    changelog: string;
    file: File | null;
    dependencies: string[];
}

export const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36" xmlns="http://www.w3.org/2000/svg">
        <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83A72.37,72.37,0,0,0,45.64,0A105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36A77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19a77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" />
    </svg>
);

export const ScrollStyles = () => (
    <style>{`
        .custom-scrollbar::-webkit-scrollbar { width: 6px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(156, 163, 175, 0.5); border-radius: 20px; }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover { background-color: rgba(156, 163, 175, 0.8); }
    `}</style>
);

export const Label = ({children, required, className}: {
    children: React.ReactNode,
    required?: boolean,
    className?: string
}) => (
    <label className={`block text-xs font-bold uppercase text-slate-500 mb-2 tracking-wide flex items-center gap-1 ${className ?? ''}`}>
        {children} {required && <span className="text-red-500">*</span>}
    </label>
);

export const Input = (props: React.InputHTMLAttributes<HTMLInputElement>) => (
    <input
        {...props}
        className={`w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-modtale-accent focus:border-modtale-accent outline-none transition-all placeholder:text-slate-400 dark:text-white ${props.className ?? ''}`}
    />
);

export const ThemedInput = ({ label, disabled, ...props }: any) => (
    <div className="space-y-1.5">
        <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">{label}</label>
        <input {...props} disabled={disabled} className={`w-full bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-slate-600 outline-none transition-all text-sm ${disabled ? 'opacity-50 cursor-not-allowed' : 'focus:ring-2 focus:ring-modtale-accent'}`} />
    </div>
);

const STRICT_VERSION_REGEX = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$/;

interface VersionFieldsProps {
    data: VersionFormData;
    onChange: (d: VersionFormData) => void;
    isModpack?: boolean;
    projectType?: string;
    existingVersions?: string[];
    disabled?: boolean;
}

export const VersionFields: React.FC<VersionFieldsProps> = ({
                                                                data,
                                                                onChange,
                                                                isModpack,
                                                                projectType,
                                                                existingVersions = [],
                                                                disabled
                                                            }) => {
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);

    const gameVersions = ['2026.01.17-4b0f30090', '2026.01.13-dcad8778f'].sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));

    useEffect(() => {
        if (!disabled && (!data.gameVersions || data.gameVersions.length === 0) && gameVersions.length > 0) {
            onChange({ ...data, gameVersions: [gameVersions[0]] });
        }
    }, []);

    const versionNum = data.versionNumber.trim();
    const isFormatValid = STRICT_VERSION_REGEX.test(versionNum);
    const isDuplicate = existingVersions.includes(versionNum);
    const isValid = versionNum.length > 0 && isFormatValid && !isDuplicate;

    const getAcceptTypes = (): Accept => {
        switch (projectType) {
            case 'PLUGIN':
                return { 'application/java-archive': ['.jar'] };
            case 'SAVE':
            case 'ART':
            case 'DATA':
                return { 'application/zip': ['.zip'] };
            default:
                return {
                    'application/java-archive': ['.jar'],
                    'application/zip': ['.zip'],
                    'application/json': ['.json']
                };
        }
    };

    const onFileDrop = useCallback((acceptedFiles: File[]) => {
        if (acceptedFiles[0] && !disabled) onChange({ ...data, file: acceptedFiles[0] });
    }, [data, onChange, disabled]);

    const { getRootProps, getInputProps, isDragActive } = useDropzone({
        onDrop: onFileDrop,
        maxFiles: 1,
        accept: getAcceptTypes(),
        disabled: disabled
    });

    const toggleGameVersion = (ver: string) => {
        if(disabled) return;
        const current = data.gameVersions || [];
        const next = current.includes(ver)
            ? current.filter(v => v !== ver)
            : [...current, ver];
        onChange({ ...data, gameVersions: next });
    };

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
                setDropdownOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    return (
        <div className={`space-y-8 ${disabled ? 'opacity-60 pointer-events-none' : ''}`}>
            {!isModpack && (
                <div>
                    <Label required>
                        Project File
                        <span className="text-slate-400 font-normal normal-case ml-1">
                            {projectType === 'PLUGIN' ? '(.jar)' : '(.zip)'}
                        </span>
                    </Label>
                    <div
                        {...getRootProps()}
                        className={`border-2 border-dashed rounded-2xl p-10 text-center transition-all group ${
                            isDragActive
                                ? 'border-modtale-accent bg-modtale-accent/5'
                                : disabled
                                    ? 'border-slate-200 dark:border-white/5 bg-slate-50 dark:bg-black/10 cursor-not-allowed'
                                    : 'border-slate-300 dark:border-white/10 bg-slate-50 dark:bg-black/20 hover:border-modtale-accent hover:bg-white dark:hover:bg-white/5 cursor-pointer'
                        }`}
                    >
                        <input {...getInputProps()} disabled={disabled} />
                        <div className="mb-4 w-12 h-12 mx-auto bg-white dark:bg-white/10 rounded-full flex items-center justify-center shadow-sm group-hover:scale-110 transition-transform">
                            <UploadCloud className="w-6 h-6 text-modtale-accent" />
                        </div>
                        {data.file ? (
                            <div>
                                <div className="font-bold text-slate-900 dark:text-white text-lg">{data.file.name}</div>
                                <div className="text-xs text-slate-500 font-mono mt-1 mb-2">{(data.file.size / 1024 / 1024).toFixed(2)} MB</div>
                                <div className="inline-flex items-center gap-1 text-xs font-bold text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/20 px-2 py-0.5 rounded-full">
                                    <Check className="w-3 h-3" /> Ready
                                </div>
                            </div>
                        ) : (
                            <div>
                                <div className="font-bold text-slate-900 dark:text-white">Click or drag file here</div>
                                <div className="text-xs text-slate-500 mt-1">
                                    {projectType === 'PLUGIN' ? 'Supports .jar files' : 'Supports .zip archives'}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                <div>
                    <div className="flex justify-between items-center mb-2">
                        <Label required>Version Number</Label>
                        {versionNum.length > 0 && !disabled && (
                            <span className={`text-[10px] font-bold uppercase flex items-center gap-1 px-2 py-0.5 rounded ${
                                isValid
                                    ? 'text-green-600 bg-green-100 dark:text-green-400 dark:bg-green-900/20'
                                    : 'text-red-600 bg-red-100 dark:text-red-400 dark:bg-red-900/20'
                            }`}>
                                {isValid ? (
                                    <><CheckCircle2 className="w-3 h-3" /> Valid</>
                                ) : (
                                    <><AlertCircle className="w-3 h-3" /> {isDuplicate ? 'Duplicate' : 'Invalid Format'}</>
                                )}
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
                    <p className="text-[10px] text-slate-400 mt-1.5 ml-1">
                        Must be unique and follow SemVer format (e.g. 1.0.0, 1.0.0-beta+exp).
                    </p>
                </div>

                <div ref={dropdownRef}>
                    <Label required>Game Versions</Label>
                    <div className="relative">
                        <button
                            type="button"
                            disabled={disabled}
                            onClick={() => !disabled && setDropdownOpen(!dropdownOpen)}
                            className={`w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 text-sm outline-none transition-all text-left flex justify-between items-center ${disabled ? 'cursor-not-allowed opacity-70' : 'focus:ring-2 focus:ring-modtale-accent focus:border-modtale-accent'}`}
                        >
                            <span className={data.gameVersions?.length > 0 ? "text-slate-900 dark:text-white font-medium" : "text-slate-400"}>
                                {data.gameVersions && data.gameVersions.length > 0
                                    ? `${data.gameVersions.length} selected`
                                    : "Select Versions..."}
                            </span>
                            <ChevronDown className="w-4 h-4 text-slate-400" />
                        </button>
                        {dropdownOpen && !disabled && (
                            <div className="absolute top-full mt-1 left-0 w-full bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 overflow-hidden animate-in fade-in zoom-in-95 duration-100">
                                {gameVersions.map(v => (
                                    <button
                                        key={v}
                                        type="button"
                                        onClick={(e) => {
                                            e.stopPropagation(); // Keep dropdown open for multi-select
                                            toggleGameVersion(v);
                                        }}
                                        className="w-full text-left px-4 py-3 hover:bg-slate-100 dark:hover:bg-white/5 text-sm text-slate-700 dark:text-slate-200 transition-colors flex items-center justify-between"
                                    >
                                        <span>{v}</span>
                                        {data.gameVersions?.includes(v) && <Check className="w-4 h-4 text-modtale-accent" />}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                    {data.gameVersions?.length > 0 && (
                        <div className="flex flex-wrap gap-2 mt-2">
                            {data.gameVersions.map(v => (
                                <span key={v} className="text-[10px] font-bold px-2 py-1 bg-slate-100 dark:bg-white/10 rounded text-slate-600 dark:text-slate-300">
                                    {v}
                                </span>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            <div>
                <Label required>Release Channel</Label>
                <div className="flex bg-slate-50 dark:bg-black/20 p-1 rounded-xl border border-slate-200 dark:border-white/10">
                    {(['RELEASE', 'BETA', 'ALPHA'] as const).map(c => (
                        <button
                            key={c}
                            type="button"
                            disabled={disabled}
                            onClick={() => onChange({ ...data, channel: c })}
                            className={`flex-1 py-2 rounded-lg text-xs font-black uppercase transition-all flex items-center justify-center gap-2 ${
                                (data.channel || 'RELEASE') === c
                                    ? (c === 'RELEASE' ? 'bg-green-500 text-white shadow-lg' : c === 'BETA' ? 'bg-blue-500 text-white shadow-lg' : 'bg-orange-500 text-white shadow-lg')
                                    : 'text-slate-500 hover:text-slate-900 dark:hover:text-white'
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
                    <p className="text-xs text-slate-500 mb-2">Does your project require other mods to work?</p>
                    <DependencySelector
                        selectedDeps={data.modIds || []}
                        onChange={(deps) => onChange({ ...data, modIds: deps })}
                        targetGameVersion={data.gameVersions?.[0]}
                        label="Add Dependency"
                        isModpack={false}
                        disabled={disabled}
                    />
                </div>
            )}

            {isModpack && (
                <div className="mt-4">
                    <Label required>Included Mods</Label>
                    <p className="text-xs text-slate-500 mb-2">Select the mods to include in this modpack version.</p>
                    <DependencySelector
                        selectedDeps={data.modIds || []}
                        onChange={(deps) => onChange({ ...data, modIds: deps })}
                        targetGameVersion={data.gameVersions?.[0]}
                        label="Add Mods"
                        isModpack={true}
                        disabled={disabled}
                    />
                </div>
            )}

            <div>
                <div className="flex justify-between items-center mb-2">
                    <Label>Changelog</Label>
                    <span className="text-[10px] uppercase font-bold text-slate-500 bg-slate-100 dark:bg-white/5 px-2 py-0.5 rounded border border-slate-200 dark:border-white/10">
                        Markdown Only
                    </span>
                </div>
                <textarea
                    value={data.changelog}
                    disabled={disabled}
                    onChange={e => onChange({...data, changelog: e.target.value})}
                    rows={6}
                    className={`w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 font-mono text-sm outline-none transition-all placeholder:text-slate-400 dark:text-white ${disabled ? 'cursor-not-allowed opacity-70' : 'focus:ring-2 focus:ring-modtale-accent focus:border-modtale-accent'}`}
                    placeholder="- Fixed bugs&#10;- Added new items"
                />
            </div>
        </div>
    );
};