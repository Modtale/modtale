import type { Mod, Modpack, World } from '../types';

export const generateProjectMeta = (item: Mod | Modpack | World | any) => {
    if (!item) return null;

    const title = `${item.title} | Modtale`;
    const author = item.author || 'Unknown';
    const downloads = (item.downloadCount || 0).toLocaleString();

    let typeLabel = 'Project';
    if (item.classification) {
        if (item.classification === 'MODPACK') typeLabel = 'Modpack';
        else if (item.classification === 'SAVE') typeLabel = 'World';
        else typeLabel = item.classification.charAt(0) + item.classification.slice(1).toLowerCase();
    }

    const statsLine = `⬇️ ${downloads}  🏷️ ${typeLabel}  👤 ${author}`;

    let plainText = "";
    if (item.about) {
        plainText = item.about
            .replace(/!\[.*?\]\(.*?\)/g, '')
            .replace(/\[.*?\]\(.*?\)/g, '$1')
            .replace(/#{1,6}\s/g, '')
            .replace(/(\*\*|__)(.*?)\1/g, '$2')
            .replace(/(\*|_)(.*?)\1/g, '$2')
            .replace(/`{1,3}(.*?)`{1,3}/g, '$1')
            .replace(/^\s*-\s/gm, '')
            .replace(/\n/g, ' ')
            .trim();
    } else if (item.description) {
        plainText = item.description.replace(/[#*_\[\]`]/g, '');
    }

    const description = `${plainText.substring(0, 150)}${plainText.length > 150 ? '...' : ''} — ${statsLine}`;

    return { title, description, author };
};