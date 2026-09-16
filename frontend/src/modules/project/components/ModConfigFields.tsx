import { modpackDialog } from './modpackDialogStyles';
import { useDialogFocus } from '@/hooks/useDialogFocus';
import React, { useEffect, useId, useRef, useState } from 'react';
import { FileCode2, Pencil, X } from 'lucide-react';
import { ModalPortal } from '@/components/ui/ModalPortal';
import { useScrollLock } from '@/hooks/useScrollLock';
import { Input } from './FormShared';
import { CONFIG_EXTENSIONS, MAX_CONFIG_FILES, configFileName, configPath, safeConfigSegment, readConfigBytes, validateModConfigs, type ModConfig } from '../utils/modpackConfigs';
import { projectClient } from '../api/projectClient';
import { ModConfigEditor } from './ModConfigEditor';
import { readSettings, type SettingsDocument } from '../utils/configSettings';
import { theme } from '@/styles/theme';

export function ModConfigFields({ projectId, title, source = 'MODTALE', versionNumber, configs, onChange, disabled }: {
    projectId: string; title: string; source?: string; versionNumber?: string; configs: ModConfig[];
    onChange: (configs: ModConfig[]) => void; disabled?: boolean;
}) {
    const [open, setOpen] = useState(false);
    const dialogId = useId();
    const dialogRef = useRef<HTMLDivElement>(null);
    const [editing, setEditing] = useState<{ config: ModConfig; document: SettingsDocument } | null>(null);
    useScrollLock(open);
    useDialogFocus(open, dialogRef);
    useEffect(() => {
        if (!open || editing) return;
        const keydown = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false); };
        document.addEventListener('keydown', keydown);
        return () => document.removeEventListener('keydown', keydown);
    }, [open, editing]);
    const [draft, setDraft] = useState<ModConfig[]>([]);
    const [folder, setFolder] = useState('');
    const [suggestedFolder, setSuggestedFolder] = useState('');
    const [override, setOverride] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
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
            const version = versionNumber
                ? project.versions?.find(item => item.versionNumber === versionNumber)
                : [...(project.versions || [])].sort((a, b) => Date.parse(b.releaseDate) - Date.parse(a.releaseDate))[0];
            const identity = version?.manifestId?.split(':');
            if (identity?.length === 2 && identity.every(part => safeConfigSegment(part) && !part.includes('/'))) {
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
        setOverride(false);
        setError(null);
        setEditing(null);
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
        <button type="button" disabled={disabled} onClick={startEditing} aria-label={`${configs.length ? 'Edit configs' : 'Add config'} for ${title}`} className={`inline-flex w-28 justify-start items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs font-medium transition-colors ${configs.length ? theme.colors.accent : theme.colors.textMuted} ${disabled ? 'cursor-default' : theme.colors.bgSurfaceHover}`}><FileCode2 className="w-3.5 h-3.5" /><span>{configs.length ? `Configs (${configs.length})` : 'Config'}</span></button>
        {open && <ModalPortal><div className={theme.components.modalOverlay} onMouseDown={event => { if (event.target === event.currentTarget && !editing) setOpen(false); }}>
            <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby={dialogId} className={editing ? `${theme.components.modalContent} w-full max-w-5xl h-[min(720px,85dvh)]` : modpackDialog.content}>
                {editing ? <ModConfigEditor headingId={dialogId} title={title} filename={configFileName(editing.config)} document={editing.document} onCancel={() => setEditing(null)} onSave={text => {
                    const file = new File([text], editing.config.file.name, { type: editing.config.file.type || 'text/plain' });
                    const next = draft.map(item => item.id === editing.config.id ? { ...item, file } : item);
                    const validation = validateModConfigs(next.map(item => ({ ...item, destination })));
                    if (validation) throw new Error(validation);
                    setDraft(next); setEditing(null);
                }} /> : <>
                <div className={modpackDialog.header}><div><h3 id={dialogId} className={modpackDialog.title}><FileCode2 className={`w-5 h-5 shrink-0 ${theme.colors.accent}`} /><span className="truncate">{title} configs</span></h3></div><button type="button" onClick={() => setOpen(false)} aria-label="Cancel config changes" className={modpackDialog.close}><X className="w-5 h-5" /></button></div>
                <div className={`${modpackDialog.body} !space-y-5`}>
                    <div>
                        <div className="flex items-center justify-between mb-2"><span className={`text-xs font-bold ${theme.colors.textSecondary}`}>Files</span><div className="flex gap-3"><button type="button" onClick={() => fileInput.current?.click()} className={`text-xs ${theme.colors.accent} hover:underline`}>Add files</button><button type="button" onClick={() => folderInput.current?.click()} className={`text-xs ${theme.colors.textMuted} hover:underline`}>Add folder</button></div></div>

                        <div onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); void addFiles(Array.from(event.dataTransfer.files)); }}>
                            {draft.length ? <div className="space-y-2">{draft.map(config => <div key={config.id} className={`${modpackDialog.row} flex items-center gap-3 min-w-0`}><FileCode2 className={`w-4 h-4 shrink-0 ${theme.colors.textMuted}`} /><button type="button" aria-label={`Edit ${configFileName(config)}`} title="Edit settings" className={`min-w-0 flex-1 flex items-center gap-2 text-left text-sm ${theme.colors.textPrimary} hover:text-modtale-accent transition-colors`} onClick={async () => { try { setEditing({ config, document: readSettings(config.file.name, await config.file.text()) }); setError(null); } catch (e) { setError((e as Error).message); } }}><span className="truncate">{configFileName(config)}</span><Pencil aria-hidden="true" className={`w-3.5 h-3.5 shrink-0 ${theme.colors.accent}`} /></button><span className={`text-[11px] ${theme.colors.textMuted}`}>{config.file.size < 1024 ? `${config.file.size} B` : `${(config.file.size / 1024).toFixed(1)} KiB`}</span><button type="button" aria-label={`Remove ${configFileName(config)}`} onClick={() => { setDraft(items => items.filter(item => item.id !== config.id)); setEditing(null); }} className={theme.components.iconButton}><X className="w-3.5 h-3.5" /></button></div>)}</div> : <button type="button" onClick={() => fileInput.current?.click()} className={`w-full rounded-xl border border-dashed ${theme.colors.border} px-4 py-6 text-sm ${theme.colors.textMuted} hover:border-modtale-accent`}>Choose config files or drop them here</button>}
                        </div>
                        <input ref={fileInput} type="file" multiple accept={CONFIG_EXTENSIONS} className="hidden" onChange={event => { void addFiles(Array.from(event.target.files || [])); event.target.value = ''; }} />
                        <input ref={folderInput} type="file" multiple {...{ webkitdirectory: '', directory: '' }} className="hidden" onChange={event => { void addFiles(Array.from(event.target.files || []), true); event.target.value = ''; }} />
                    </div>
                    <div className={`border-t ${theme.colors.border} pt-4 space-y-2`}>
                        <div className="flex items-center justify-between gap-3"><span className={`text-xs font-bold ${theme.colors.textSecondary}`}>Install location</span><button type="button" onClick={() => setOverride(value => !value)} className={`text-xs ${theme.colors.accent} hover:underline`}>{override ? 'Hide override' : 'Change'}</button></div>
                        {loading && !folder && <p className={`text-sm ${theme.colors.textMuted}`}>Finding install location…</p>}
                        {folder && <p className={`text-xs break-all ${theme.colors.textMuted}`}>{folder}</p>}
                        {!loading && !folder && <button type="button" onClick={() => folderInput.current?.click()} className={`text-xs ${theme.colors.accent} hover:underline`}>Import config folder</button>}
                        {override && <label className={`block text-xs ${theme.colors.textSecondary}`}>Custom mod folder<Input value={folder} onChange={event => { folderEdited.current = true; setFolder(event.target.value); }} placeholder="Group_PluginName" className="mt-2 !font-normal" />{suggestedFolder && folder !== suggestedFolder && <button type="button" onClick={() => { folderEdited.current = false; setFolder(suggestedFolder); setOverride(false); }} className={`mt-2 text-xs ${theme.colors.accent} hover:underline`}>Use detected folder</button>}</label>}
                    </div>
                    {(error || pathError) && <p role="alert" className={`text-xs ${theme.colors.dangerText}`}>{error || pathError}</p>}

                </div>
                <div className={`${modpackDialog.footer} !justify-end gap-3`}><button type="button" onClick={() => setOpen(false)} className={theme.components.buttonGhost}>Cancel</button><button type="button" disabled={loading || !!pathError || disabled} onClick={() => { onChange(prepared); setOpen(false); }} className={theme.components.buttonPrimary}>Save configs</button></div>
                </>}
            </div>
        </div></ModalPortal>}
    </>;
}
