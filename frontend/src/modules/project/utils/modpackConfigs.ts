import JSZip from 'jszip';
import type { VersionFormData } from '../components/FormShared';

export type ModConfig = { id: string; projectId: string; file: File; destination: string };

export function configPath(config: ModConfig): string {
    const path = `${config.destination}/${config.file.name}`;
    const parts = path.split('/');
    if (!['Mods', 'Saves'].includes(parts[0]) || parts.length < 3 || parts.some(part =>
        !part.trim() || part === '.' || part === '..' || /[\\<>:"|?*\x00-\x1f]/.test(part) || /[. ]$/.test(part) ||
        /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\.|$)/i.test(part))) {
        throw new Error(`Choose a valid destination folder under Mods/ or Saves/ for ${config.file.name}.`);
    }
    if (/\.(exe|dll|so|dylib|sh|bat|cmd|ps1|vbs|js|jsp|php|py|pl|html|htm|svg|hta|jar|zip|rar|7z|tar|gz)$/i.test(config.file.name)) {
        throw new Error(`${config.file.name} is not a supported config file.`);
    }
    if (config.file.size > 32 * 1024 * 1024) throw new Error(`${config.file.name} exceeds the 32MB config file limit.`);
    return `overrides/${path}`;
}

export async function buildModpackOverrides(data: VersionFormData): Promise<File | null> {
    const configs = (data.modConfigs || []).filter(config => data.dependencies.some(dep => dep.projectId === config.projectId));
    if (!configs.length) return data.file;
    const zip = data.file ? await JSZip.loadAsync(await data.file.arrayBuffer()) : new JSZip();
    const paths = new Set(Object.values(zip.files).filter(entry => !entry.dir).map(entry => entry.name.replace(/\\/g, '/').toLowerCase()));
    for (const config of configs) {
        const path = configPath(config);
        if (paths.has(path.toLowerCase())) throw new Error(`More than one config uses ${path}. Choose a different destination or remove the duplicate from the ZIP.`);
        paths.add(path.toLowerCase());
        zip.file(path, await config.file.arrayBuffer());
    }
    const blob = await zip.generateAsync({ type: 'blob', compression: 'DEFLATE' });
    if (blob.size > 100 * 1024 * 1024) throw new Error('Combined configs and overrides exceed the 100MB upload limit.');
    return new File([blob], 'modpack-configs.zip', { type: 'application/zip' });
}
