import { useDialogFocus } from '@/hooks/useDialogFocus';
import React, { useEffect, useId, useRef, useState } from 'react';
import { FileCode2, X } from 'lucide-react';
import { ModalPortal } from '@/components/ui/ModalPortal';
import { useScrollLock } from '@/hooks/useScrollLock';
import { Input } from './FormShared';
import { CONFIG_EXTENSIONS, MAX_CONFIG_FILE_BYTES, MAX_CONFIG_FILES, MAX_CONFIG_TOTAL_BYTES, configFileName, configPath, readConfigBytes, validateModConfigs, type ModConfig } from '../utils/modpackConfigs';
import { projectClient } from '../api/projectClient';
import { theme } from '@/styles/theme';

export function ModConfigFields({ projectId, title, source = 'MODTALE', versionNumber, configs, onChange, disabled }: {
    projectId: string; title: string; source?: string; versionNumber?: string; configs: ModConfig[];
    onChange: (configs: ModConfig[]) => void; disabled?: boolean;
}) {
    const [open, setOpen] = useState(false);
    const dialogId = useId();
    const dialogRef = useRef<HTMLDivElement>(null);
    useScrollLock(open);
    useDialogFocus(open, dialogRef);
    useEffect(() => {
        if (!open) return;
        const keydown = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false); };
        document.addEventListener('keydown', keydown);
        return () => document.removeEventListener('keydown', keydown);
    }, [open]);
    const [draft, setDraft] = useState<ModConfig[]>([]);
    const [folder, setFolder] = useState('');
    const [suggestedFolder, setSuggestedFolder] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [preview, setPreview] = useState<{ name: string; text: string } | null>(null);
    const fileInput = useRef<HTMLInputElement>(null);
    const folderInput = useRef<HTMLInputElement>(null);
    const folderEdited = useRef(false);
    const destination = `Universe/mods/${folder}`;
    const prepared = draft.map(config => ({ ...config, destination }));
    const pathError = draft.length ? validateModConfigs(prepared) : null;

    useEffect(() => {
        if (!open || source !== 'MODTALE') return;
        let active = true;
        setLoading(true);
        projectClient.getProjectFull(projectId).then(project => {
            if (!active) return;
            const version = project.versions?.find(item => item.versionNumber === versionNumber);
            const identity = version?.manifestId?.split(':');
            if (identity?.length === 2 && identity.every(part => part && !/[\\/]/.test(part))) {
                const suggestion = identity.join('_');
                setSuggestedFolder(suggestion);
                if (!folderEdited.current) setFolder(suggestion);
            }
        }).catch(() => {}).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [open, source, projectId, versionNumber]);

    const startEditing = () => {
        const parts = configs[0]?.destination.split('/') || [];
        setDraft(configs.map(config => ({ ...config })));
        setFolder(parts[0] === 'Universe' ? parts[2] : parts[0] === 'Mods' ? parts[1] : parts[3] || '');
        folderEdited.current = configs.length > 0;
        setSuggestedFolder('');
        setError(null);
        setPreview(null);
        setOpen(true);
    };
    const addFiles = async (files: File[], fromFolder = false) => {
        setError(null);
        try {
            const additions: ModConfig[] = [];
            let importedFolder = '';
            for (const file of files) {
                const relative = fromFolder ? file.webkitRelativePath.split('/') : [file.name];
                if (fromFolder) importedFolder = relative.shift() || '';
                const item: ModConfig = { id: crypto.randomUUID(), projectId, source, file, relativePath: relative.join('/'), destination: 'Universe/mods/Validation' };
                configPath(item);
                await readConfigBytes(file);
                if ([...draft, ...additions].some(config => configFileName(config).toLowerCase() === configFileName(item).toLowerCase())) throw new Error(`${configFileName(item)} is already attached. Remove it before replacing it.`);
                additions.push(item);
            }
            if (draft.length + additions.length > MAX_CONFIG_FILES) throw new Error(`You can attach up to ${MAX_CONFIG_FILES} config files.`);
            if (importedFolder && !draft.length) { setFolder(importedFolder); folderEdited.current = true; }
            setDraft(previous => [...previous, ...additions]);
        } catch (e) { setError((e as Error).message); }
    };

    return <>
        <button type="button" disabled={disabled} onClick={startEditing} aria-label={`${configs.length ? 'Edit configs' : 'Add config'} for ${title}`} className={`inline-flex w-28 justify-start items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs font-medium transition-colors ${configs.length ? theme.colors.accent : theme.colors.textMuted} ${theme.colors.bgSurfaceHover}`}><FileCode2 className="w-3.5 h-3.5" /><span>{configs.length ? `Configs (${configs.length})` : 'Config'}</span></button>
        {open && <ModalPortal><div className={theme.components.modalOverlay} onMouseDown={event => { if (event.target === event.currentTarget) setOpen(false); }}>
            <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby={dialogId} className={`${theme.components.modalContent} w-full max-w-md max-h-[90dvh]`}>
                <div className={theme.components.modalHeader}><div><h3 id={dialogId} className={`font-bold ${theme.colors.textPrimary}`}>{title} configs</h3><p className={`mt-1 text-xs ${theme.colors.textMuted}`}>Defaults for each universe using this pack.</p></div><button type="button" onClick={() => setOpen(false)} aria-label="Cancel config changes" className={theme.components.iconButton}><X className="w-4 h-4" /></button></div>
                <div className={`${theme.components.modalBody} !space-y-5`}>
                    <div>
                        <div className="flex items-center justify-between mb-2"><span className={`text-xs font-bold ${theme.colors.textSecondary}`}>Files</span><div className="flex gap-3"><button type="button" onClick={() => fileInput.current?.click()} className={`text-xs ${theme.colors.accent} hover:underline`}>Add files</button><button type="button" onClick={() => folderInput.current?.click()} className={`text-xs ${theme.colors.textMuted} hover:underline`}>Add folder</button></div></div>
                        <p className={`mb-3 text-[11px] leading-relaxed ${theme.colors.textMuted}`}>Supported types: {CONFIG_EXTENSIONS.split(',').join(', ')} · maximum {MAX_CONFIG_FILE_BYTES / (1024 * 1024)} MiB per file, {MAX_CONFIG_TOTAL_BYTES / (1024 * 1024)} MiB total, and {MAX_CONFIG_FILES} files.</p>
                        <div onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); void addFiles(Array.from(event.dataTransfer.files)); }}>
                            {draft.length ? <div className={`divide-y divide-slate-200 dark:divide-white/10`}>{draft.map(config => <div key={config.id} className="flex items-center gap-2 py-2 min-w-0"><FileCode2 className={`w-4 h-4 shrink-0 ${theme.colors.textMuted}`} /><button type="button" className={`min-w-0 flex-1 text-left text-sm truncate ${theme.colors.textPrimary}`} onClick={async () => { try { setPreview(preview?.name === configFileName(config) ? null : { name: configFileName(config), text: await config.file.text() }); } catch { setError('Could not preview this file.'); } }}>{configFileName(config)}</button><span className={`text-[11px] ${theme.colors.textMuted}`}>{config.file.size < 1024 ? `${config.file.size} B` : `${(config.file.size / 1024).toFixed(1)} KiB`}</span><button type="button" aria-label={`Remove ${configFileName(config)}`} onClick={() => { setDraft(items => items.filter(item => item.id !== config.id)); setPreview(null); }} className={theme.components.iconButton}><X className="w-3.5 h-3.5" /></button></div>)}</div> : <button type="button" onClick={() => fileInput.current?.click()} className={`w-full rounded-xl border border-dashed ${theme.colors.border} px-4 py-6 text-sm ${theme.colors.textMuted} hover:border-modtale-accent`}>Choose config files or drop them here</button>}
                        </div>
                        <input ref={fileInput} type="file" multiple accept={CONFIG_EXTENSIONS} className="hidden" onChange={event => { void addFiles(Array.from(event.target.files || [])); event.target.value = ''; }} />
                        <input ref={folderInput} type="file" multiple {...{ webkitdirectory: '', directory: '' }} className="hidden" onChange={event => { void addFiles(Array.from(event.target.files || []), true); event.target.value = ''; }} />
                        {preview && <pre className={`mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-all text-xs ${theme.colors.textSecondary}`}>{preview.text}</pre>}
                    </div>
                    <label className={`block text-xs font-bold ${theme.colors.textSecondary}`}>Mod folder<Input value={folder} onChange={event => { folderEdited.current = true; setFolder(event.target.value); }} placeholder="Group_PluginName" className="mt-2 !font-normal" /><span className={`block mt-1.5 text-[11px] font-normal ${theme.colors.textMuted}`}>{loading ? 'Checking the mod’s manifest…' : suggestedFolder === folder && folder ? 'From the mod’s manifest. Change it only if needed.' : 'Use the exact folder name created by the mod.'}</span></label>
                    <details><summary className={`cursor-pointer text-xs ${theme.colors.textMuted}`}>Installation paths</summary><div className="mt-2 space-y-1">{draft.length ? prepared.map(item => <p key={item.id} className={`text-xs font-mono break-all ${theme.colors.textSecondary}`}>Universe / mods / {folder}/{configFileName(item)}</p>) : <p className={`text-xs ${theme.colors.textMuted}`}>Add files to preview their paths.</p>}</div></details>
                    {(error || pathError) && <p role="alert" className={`text-xs ${theme.colors.dangerText}`}>{error || pathError}</p>}
                    <p className={`text-xs ${theme.colors.textMuted}`}>Applied when the pack is enabled in a universe. Existing settings are kept.</p>
                </div>
                <div className={`${theme.components.modalFooter} !justify-end gap-3`}><button type="button" onClick={() => setOpen(false)} className={theme.components.buttonGhost}>Cancel</button><button type="button" disabled={!!pathError || disabled} onClick={() => { onChange(prepared); setOpen(false); }} className={theme.components.buttonPrimary}>Save configs</button></div>
            </div>
        </div></ModalPortal>}
    </>;
}
