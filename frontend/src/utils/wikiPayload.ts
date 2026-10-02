const isRecord = (value: any): value is Record<string, any> => Boolean(value && typeof value === 'object' && !Array.isArray(value));
const isSlug = (value: any) => typeof value === 'string' && value.trim().length > 0;

const isWikiNode = (value: any, depth = 0): boolean => {
    if (!isRecord(value) || 'error' in value || depth > 32) return false;
    if (value.title != null && typeof value.title !== 'string') return false;
    if (value.name != null && typeof value.name !== 'string') return false;
    if (value.children != null && (!Array.isArray(value.children) || !value.children.every((node: any) => isWikiNode(node, depth + 1)))) return false;
    // Navigation may include named groups with children instead of a page slug.
    return isSlug(value.slug) || ((typeof value.title === 'string' || typeof value.name === 'string') && Array.isArray(value.children));
};

export const isWikiMetadata = (value: any): boolean => (
    isRecord(value) && !('error' in value)
    && Array.isArray(value.pages) && value.pages.every((node: any) => isWikiNode(node))
    && (value.index == null || (isRecord(value.index) && isSlug(value.index.slug)))
);

export const isWikiPage = (value: any): boolean => (
    isRecord(value) && !('error' in value) && typeof value.content === 'string'
    && (value.title == null || typeof value.title === 'string')
);
