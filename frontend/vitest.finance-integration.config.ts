import path from 'node:path';
import { defineConfig } from 'vitest/config';

// Only the disposable Java test server may run these tests. No preview or provider credentials.
const raw = process.env.MODTALE_FINANCE_TEST_ORIGIN;
if (!raw) throw new Error('Run backend financeFrontendIntegrationTest to start the isolated server');
const origin = new URL(raw);
if (origin.protocol !== 'http:' || origin.hostname !== '127.0.0.1' || !origin.port || origin.pathname !== '/'
    || origin.username || origin.password || origin.search || origin.hash) {
    throw new Error('Finance integration requires an exact loopback HTTP origin');
}

export default defineConfig({
    resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
    define: { 'import.meta.env.PUBLIC_API_URL': JSON.stringify(`${origin.origin}/api/v1`) },
    test: {
        include: ['integration/financeSession.test.tsx'],
        environment: 'jsdom',
        environmentOptions: { jsdom: { url: 'http://localhost:3000/' } },
        setupFiles: ['tests/setup.ts'],
        fileParallelism: false,
        testTimeout: 15_000,
        restoreMocks: true
    }
});
