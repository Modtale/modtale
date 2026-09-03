import { setI18n } from 'react-i18next';
import { createAppI18n } from '@/i18n/i18n';

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const createMemoryStorage = (): Storage => {
    const values = new Map<string, string>();
    return {
        get length() { return values.size; },
        clear: () => values.clear(),
        getItem: key => values.get(key) ?? null,
        key: index => [...values.keys()][index] ?? null,
        removeItem: key => { values.delete(key); },
        setItem: (key, value) => { values.set(key, String(value)); }
    };
};

if (typeof window !== 'undefined') {
    if (!window.localStorage) {
        Object.defineProperty(window, 'localStorage', { configurable: true, value: createMemoryStorage() });
    }
    if (!window.sessionStorage) {
        Object.defineProperty(window, 'sessionStorage', { configurable: true, value: createMemoryStorage() });
    }
}

setI18n(createAppI18n());
