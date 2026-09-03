(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const createMemoryStorage = (): Storage => {
    const values = new Map<string, string>();
    return {
        get length() { return values.size; },
        clear: () => values.clear(),
        getItem: key => values.get(String(key)) ?? null,
        key: index => [...values.keys()][index] ?? null,
        removeItem: key => values.delete(String(key)),
        setItem: (key, value) => values.set(String(key), String(value))
    };
};

const browserWindow = typeof window === 'undefined' ? undefined : window;

for (const name of ['localStorage', 'sessionStorage'] as const) {
    let storage: Storage | undefined;
    if (browserWindow) {
        try {
            storage = browserWindow[name];
        } catch {
            // Node 26 can expose an unavailable experimental storage getter.
        }
    }
    if (!storage || typeof storage.getItem !== 'function') {
        storage = createMemoryStorage();
    }
    if (browserWindow) {
        Object.defineProperty(browserWindow, name, { configurable: true, value: storage });
    }
    Object.defineProperty(globalThis, name, { configurable: true, value: storage });
}
