const RAW_STATUS_URL =
    import.meta.env.PUBLIC_STATUS_URL?.trim() || 'https://status.modtale.net';

export const STATUS_PAGE_URL = RAW_STATUS_URL.endsWith('/')
    ? RAW_STATUS_URL.slice(0, -1)
    : RAW_STATUS_URL;
