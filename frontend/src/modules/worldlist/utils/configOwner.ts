import type { WorldModListItem } from '@/modules/worldlist/api/worldListClient';

/** Hytale derives its data folder from manifest Group + '_' + Name, preserving case. */
export function configOwnerLabel(path: string, mods: WorldModListItem[]): string {
    const folder = path.split('/')[0];
    const owners = new Map<string, WorldModListItem>();
    for (const mod of mods) {
        const parts = mod.modId?.split(':');
        if (!parts || parts.length !== 2 || parts.some(part => !part.trim() || /[/\\]/.test(part))) continue;
        if (`${parts[0]}_${parts[1]}` === folder) owners.set(mod.modId!, mod);
    }
    if (owners.size > 1) return 'Ambiguous mod';
    const owner = owners.values().next().value;
    return owner ? `${owner.title} (${owner.modId})` : 'Unattributed';
}
