/// <reference path="../.astro/types.d.ts" />
interface Window {
    INITIAL_DATA?: any;
    __MODTALE_PROJECT_BOOTSTRAP?: Promise<any | null>;
    __MODTALE_PROJECT_BOOTSTRAP_URL?: string;
}

interface ImportMetaEnv {
    readonly PUBLIC_API_URL: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
