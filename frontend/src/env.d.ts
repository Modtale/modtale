/// <reference path="../.astro/types.d.ts" />
interface Window {
    INITIAL_DATA?: any;
    __MODTALE_PROJECT_BOOTSTRAP?: Promise<any | null>;
    __MODTALE_PROJECT_BOOTSTRAP_URL?: string;
    __MODTALE_WIKI_BOOTSTRAP?: {
        projectId?: string;
        bundleUrl?: string;
        metadataUrl?: string;
        metadataData?: any;
        metadata?: Promise<any | null> | null;
        pages?: Record<string, {
            url?: string;
            data?: any;
            promise?: Promise<any | null> | null;
        }>;
    };
}

interface ImportMetaEnv {
    readonly PUBLIC_API_URL: string;
    readonly PUBLIC_STATUS_URL: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
