/// <reference path="../.astro/types.d.ts" />
interface Window {
    INITIAL_DATA?: any;
}

interface ImportMetaEnv {
    readonly PUBLIC_API_URL: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}