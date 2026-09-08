import JSZip from 'jszip';
import type { VersionFormData } from '../components/FormShared';

export const CONFIG_MANIFEST = 'modtale.configs.json';
export const CONFIG_EXTENSIONS = '.json,.toml,.yaml,.yml,.properties,.cfg,.conf,.ini';
export type ModConfig = { id: string; projectId: string; source?: string; file: File; destination: string; relativePath?: string };
export type ConfigReference = { projectId: string; source: string; path: string; sha256: string };
const ZIP_DATE = new Date('1980-01-01T00:00:00Z');
const comparePaths = (a: string, b: string) => a < b ? -1 : a > b ? 1 : 0;
export const configOwnerKey = (projectId: string, source = 'MODTALE') => `${source}:${projectId}`;
export const configFileName = (config: ModConfig) => config.relativePath || config.file.name;
export const safeConfigSegment = (part: string) => !!part.trim() && part !== '.' && part !== '..' &&
    !/[\\<>:"|?*\x00-\x1f\x7f]/.test(part) && !/[. ]$/.test(part) &&
    !/^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\.|$)/i.test(part);

export function configPath(config: ModConfig): string {
    const parts = `${config.destination}/${configFileName(config)}`.split('/');
    const validRoot = parts[0] === 'Universe' && parts[1] === 'mods' && parts.length >= 4;
    if (!validRoot || parts.some(part => !safeConfigSegment(part))) {
        throw new Error(`Choose the exact mod folder for ${config.file.name}.`);
    }
    if (!/\.(json|toml|yaml|yml|properties|cfg|conf|ini)$/i.test(config.file.name) || config.file.name.toLowerCase() === 'manifest.json') {
        throw new Error(`${config.file.name} is not a supported config file.`);
    }
    if (config.file.size > 1024 * 1024) throw new Error(`${config.file.name} exceeds the 1 MiB config file limit.`);
    return `overrides/${parts.join('/')}`;
}

export function validateModConfigs(configs: ModConfig[]): string | null {
    try {
        if (configs.length > 100) throw new Error('A modpack can include up to 100 attached config files.');
        if (configs.reduce((total, config) => total + config.file.size, 0) > 32 * 1024 * 1024) throw new Error('Attached configs exceed the 32 MiB total limit.');
        const paths = new Set<string>();
        for (const config of configs) {
            const path = configPath(config);
            if (paths.has(path.normalize('NFC').toLowerCase())) throw new Error(`Two configs use ${path}. Change their destinations or remove one.`);
            paths.add(path.normalize('NFC').toLowerCase());
        }
        return null;
    } catch (error) { return (error as Error).message; }
}

export async function readConfigBytes(file: File): Promise<ArrayBuffer> {
    const bytes = await file.arrayBuffer();
    let content: string;
    try { content = new TextDecoder('utf-8', { fatal: true }).decode(bytes); }
    catch { throw new Error(`${file.name} must contain UTF-8 text.`); }
    if (content.includes('\0')) throw new Error(`${file.name} contains binary data.`);
    if (file.name.toLowerCase().endsWith('.json')) {
        try { JSON.parse(content); } catch { throw new Error(`${file.name} must contain valid JSON.`); }
    }
    return bytes;
}

export async function buildModpackOverrides(data: VersionFormData): Promise<File | null> {
    const configs = (data.modConfigs || []).filter(config => data.dependencies.some(dep =>
        configOwnerKey(dep.projectId, dep.source) === configOwnerKey(config.projectId, config.source)));
    const error = validateModConfigs(configs);
    if (error) throw new Error(error);
    if (data.file) throw new Error('Shared files and saves are not supported. Attach configs to their mods.');
    if (!configs.length) return null;
    const entries = new Map<string, ArrayBuffer | Uint8Array>();
    const paths = new Set<string>();
    const references: ConfigReference[] = [];
    for (const config of [...configs].sort((a, b) => comparePaths(configPath(a), configPath(b)))) {
        const path = configPath(config);
        if (paths.has(path.normalize('NFC').toLowerCase())) throw new Error(`More than one config uses ${path}. Choose a different destination or remove the duplicate from the ZIP.`);
        paths.add(path.normalize('NFC').toLowerCase());
        const bytes = await readConfigBytes(config.file);
        const hash = await crypto.subtle.digest('SHA-256', bytes);
        references.push({ projectId: config.projectId, source: config.source || 'MODTALE', path,
            sha256: Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join('') });
        entries.set(path, bytes);
    }
    const zip = new JSZip();
    entries.set(CONFIG_MANIFEST, new TextEncoder().encode(JSON.stringify({ format: 'modtale-configs', formatVersion: 1, configs: references }, null, 2) + '\n'));
    for (const path of [...entries.keys()].sort(comparePaths)) {
        zip.file(path, entries.get(path)!, { date: ZIP_DATE, createFolders: false });
    }
    const blob = await zip.generateAsync({ type: 'blob', compression: 'STORE', platform: 'DOS' });
    if (blob.size > 100 * 1024 * 1024) throw new Error('Combined configs and overrides exceed the 100MB upload limit.');
    return new File([blob], 'modpack-configs.zip', { type: 'application/zip', lastModified: ZIP_DATE.getTime() });
}

export async function importModpackConfigs(file: File, dependencies: VersionFormData['dependencies']): Promise<{ configs: ModConfig[]; sharedFile: File | null }> {
    if (file.size > 100 * 1024 * 1024) throw new Error('Config bundle exceeds the 100MB upload limit.');
    const zip = await JSZip.loadAsync(await file.arrayBuffer());
    const manifestFile = zip.file(CONFIG_MANIFEST);
    if (!manifestFile) throw new Error('This ZIP has no mod associations. Use the shared overrides ZIP option for legacy bundles.');
    const manifest = JSON.parse(await manifestFile.async('string'));
    if (manifest.format !== 'modtale-configs' || manifest.formatVersion !== 1 || !Array.isArray(manifest.configs) || manifest.configs.length > 100) throw new Error('Unsupported config manifest.');
    const configs: ModConfig[] = [];
    const consumed = new Set<string>([CONFIG_MANIFEST]);
    for (const ref of manifest.configs as ConfigReference[]) {
        if (!dependencies.some(dep => configOwnerKey(dep.projectId, dep.source) === configOwnerKey(ref.projectId, ref.source))) throw new Error(`Add the mod ${ref.projectId} before importing its configs.`);
        if (typeof ref.path !== 'string' || !ref.path.startsWith('overrides/')) throw new Error('Invalid config path in manifest.');
        const entry = zip.file(ref.path);
        if (!entry || consumed.has(ref.path)) throw new Error(`Missing or duplicate config: ${ref.path}`);
        const bytes = await entry.async('arraybuffer');
        if (bytes.byteLength > 1024 * 1024) throw new Error('An imported config exceeds 1 MiB.');
        const hash = await crypto.subtle.digest('SHA-256', bytes);
        const actual = Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join('');
        if (actual !== ref.sha256) throw new Error(`Config checksum mismatch: ${ref.path}`);
        const segments = ref.path.slice('overrides/'.length).split('/');
        const folderLength = segments[0] === 'Universe' ? 3 : segments[0] === 'Mods' ? 2 : 4;
        const config = { id: crypto.randomUUID(), projectId: ref.projectId, source: ref.source,
            destination: segments.slice(0, folderLength).join('/'), relativePath: segments.slice(folderLength).join('/'),
            file: new File([bytes], segments.at(-1)!, { type: 'text/plain' }) };
        configPath(config);
        await readConfigBytes(config.file);
        configs.push(config);
        consumed.add(ref.path);
    }
    const error = validateModConfigs(configs);
    if (error) throw new Error(error);
    const shared = new JSZip();
    let sharedCount = 0;
    for (const entry of Object.values(zip.files).filter(entry => !entry.dir && !consumed.has(entry.name))) {
        // Installable modpack metadata/binaries are not config defaults.
        if (!entry.name.startsWith('overrides/')) continue;
        throw new Error('Unassociated configs, shared files and saves are not supported.');
    }
    return { configs, sharedFile: sharedCount ? new File([await shared.generateAsync({ type: 'blob', compression: 'STORE' })], 'shared-overrides.zip') : null };
}
