import type { Project } from '../types';

const collapseWhitespace = (value: string) => value.replace(/\s+/g, ' ').trim();

const stripMarkdown = (value: string) => collapseWhitespace(
    value
        .replace(/!\[.*?\]\(.*?\)/g, '')
        .replace(/\[(.*?)\]\(.*?\)/g, '$1')
        .replace(/#{1,6}\s/g, '')
        .replace(/(\*\*|__)(.*?)\1/g, '$2')
        .replace(/(\*|_)(.*?)\1/g, '$2')
        .replace(/`{1,3}(.*?)`{1,3}/g, '$1')
        .replace(/^\s*-\s/gm, '')
);

const trimSentence = (value: string, limit: number) => {
    if (value.length <= limit) return value;
    return `${value.slice(0, limit).trim().replace(/[.,;:!?-]+$/, '')}...`;
};

const getProjectSeoLabel = (classification?: string) => {
    switch (classification) {
        case 'MODPACK':
            return { titleLabel: 'Hytale Modpack', nounPhrase: 'Hytale modpack', shortLabel: 'Modpack' };
        case 'SAVE':
            return { titleLabel: 'Hytale World', nounPhrase: 'Hytale world save', shortLabel: 'World' };
        case 'ART':
            return { titleLabel: 'Hytale Art Asset', nounPhrase: 'Hytale art asset', shortLabel: 'Art Asset' };
        case 'DATA':
            return { titleLabel: 'Hytale Data Asset', nounPhrase: 'Hytale data asset', shortLabel: 'Data Asset' };
        case 'PLUGIN':
        default:
            return { titleLabel: 'Hytale Plugin', nounPhrase: 'Hytale server plugin', shortLabel: 'Plugin' };
    }
};

export const generateProjectMeta = (item: Project | any) => {
    if (!item) return null;

    const author = item.author || 'Unknown';
    const downloads = (item.downloadCount || 0).toLocaleString();
    const type = getProjectSeoLabel(item.classification);
    const title = `${item.title} | ${type.titleLabel} | Modtale`;

    const rawText = item.about
        ? stripMarkdown(item.about)
        : item.description
            ? collapseWhitespace(String(item.description).replace(/[#*_\[\]`]/g, ''))
            : '';

    const prefix = `Download ${item.title}, a ${type.nounPhrase} by ${author}, on Modtale.`;
    const suffix = `${downloads} downloads.`;
    const summarySource = rawText || `${type.shortLabel} listing on Modtale.`;
    const baseSummaryBudget = Math.max(12, 160 - prefix.length - suffix.length - 5);
    let summary = trimSentence(summarySource, baseSummaryBudget);
    let description = collapseWhitespace(`${prefix} ${summary} ${suffix}`);

    if (description.length > 160) {
        const overflow = description.length - 160;
        summary = trimSentence(summarySource, Math.max(12, baseSummaryBudget - overflow));
        description = collapseWhitespace(`${prefix} ${summary} ${suffix}`);
    }

    return { title, description, author };
};

export const generateUserMeta = (user: any) => {
    if (!user) return null;

    const name = user.displayName || user.username;
    const followerCount = user.followerIds?.length || 0;
    const title = `${name} | Modtale Creator`;
    const description = `Creator profile on Modtale with ${followerCount.toLocaleString()} follower${followerCount === 1 ? '' : 's'}.`;

    return { title, description };
};
