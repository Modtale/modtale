export type BrowseViewStyle = 'grid' | 'list' | 'compact';

export const BROWSE_VIEW_STYLE_STORAGE_KEY = 'modtale_view_style';
export const BROWSE_ITEMS_PER_PAGE_STORAGE_KEY = 'modtale_items_per_page';
export const BROWSE_ITEMS_PER_PAGE_OPTIONS = [6, 12, 24, 48, 96] as const;

export type BrowseItemsPerPage = typeof BROWSE_ITEMS_PER_PAGE_OPTIONS[number];

export const isBrowseViewStyle = (value: string | null): value is BrowseViewStyle =>
    value === 'grid' || value === 'list' || value === 'compact';

export const isBrowseItemsPerPage = (value: number): value is BrowseItemsPerPage =>
    BROWSE_ITEMS_PER_PAGE_OPTIONS.includes(value as BrowseItemsPerPage);
