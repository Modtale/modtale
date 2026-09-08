import React from 'react';
import { FilePlus2, Trash2 } from 'lucide-react';
import { Input } from './FormShared';
import { configPath, type ModConfig } from '../utils/modpackConfigs';
import { theme } from '@/styles/theme';

export function ModConfigFields({ projectId, title, configs, onChange, disabled }: {
    projectId: string; title: string; configs: ModConfig[]; onChange: (configs: ModConfig[]) => void; disabled?: boolean;
}) {
    return <div className={`w-full border-t ${theme.colors.border} pt-3 space-y-3`}>
        {configs.map(config => {
            let error = '';
            try { configPath(config); } catch (e) { error = (e as Error).message; }
            return <div key={config.id} className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                    <span className={`font-medium break-all ${theme.colors.textPrimary}`}>{config.file.name}</span>
                    <button type="button" disabled={disabled} aria-label={`Remove ${config.file.name}`} onClick={() => onChange(configs.filter(item => item.id !== config.id))} className={`p-2 ${theme.colors.textMuted}`}><Trash2 size={16} /></button>
                </div>
                <label className={`block text-xs ${theme.colors.textSecondary}`}>
                    Destination folder in Hytale UserData
                    <Input value={config.destination} disabled={disabled} placeholder="Saves/MyWorld/mods/ExactPluginFolder" aria-invalid={!!error} onChange={event => onChange(configs.map(item => item.id === config.id ? { ...item, destination: event.target.value } : item))} className="mt-1 font-mono text-xs" />
                </label>
                {error ? <p role="alert" className={`text-xs ${theme.colors.dangerText}`}>{error}</p> : <p className={`text-xs break-all ${theme.colors.textSecondary}`}>Installs to: UserData/{config.destination}/{config.file.name}</p>}
            </div>;
        })}
        <label className={`inline-flex items-center gap-2 text-xs font-bold cursor-pointer ${theme.colors.accent}`}>
            <FilePlus2 size={16} /> Add config for {title}
            <input type="file" multiple disabled={disabled} className="sr-only" onChange={event => {
                const files = Array.from(event.target.files || []);
                onChange([...configs, ...files.map(file => ({ id: crypto.randomUUID(), projectId, file, destination: configs[0]?.destination || '' }))]);
                event.target.value = '';
            }} />
        </label>
        {configs.length > 0 && <p className={`text-xs ${theme.colors.textMuted}`}>Use the exact folder names and capitalization created by this mod: Mods/PluginFolder or Saves/WorldName/mods/PluginFolder. Existing files are preserved by the launcher.</p>}
    </div>;
}
