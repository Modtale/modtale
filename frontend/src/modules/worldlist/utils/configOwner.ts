import type { WorldModListItem } from '@/modules/worldlist/api/worldListClient';

/** Hytale derives its data folder from manifest Group + '_' + Name, preserving case. */
export function configOwners(path: string, mods: WorldModListItem[]): WorldModListItem[] {
    const folder = path.split('/')[0];
    const owners = new Map<string, WorldModListItem>();
    for (const mod of mods) {
        const parts = mod.modId?.split(':');
        if (!parts || parts.length !== 2 || parts.some(part => !part.trim() || /[/\\]/.test(part))) continue;
        if (`${parts[0]}_${parts[1]}` === folder) owners.set(mod.modId!, mod);
    }
    return [...owners.values()];
}

export function configOwnerLabel(path: string, mods: WorldModListItem[]): string {
    const owners = configOwners(path, mods);
    if (owners.length > 1) return 'Ambiguous mod';
    const owner = owners[0];
    return owner ? `${owner.title} (${owner.modId})` : 'Unattributed';
}
