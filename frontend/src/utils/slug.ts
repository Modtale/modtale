export const getProjectIdentifier = (mod: { id: string; slug?: string }) => {
    if (mod.slug && mod.slug.trim().length > 0) {
        return mod.slug;
    }
    return mod.id;
};

export const createSlug = (title: string, id: string) => {
    if (!title) return id;
    const slug = title
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/(^-|-$)+/g, '')
        .substring(0, 30);
    if (!slug) return id;
    return `${slug}-${id}`;
};

export const extractId = (param: string | undefined) => {
    if (!param) return '';
    const match = param.match(/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i);
    return match ? match[0] : param;
};