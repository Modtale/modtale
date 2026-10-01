import { defineConfig } from '@playwright/test';

const raw = process.env.MODTALE_FINANCE_TEST_ORIGIN;
if (!raw) throw new Error('Run backend financeFrontendIntegrationTest');
const origin = new URL(raw);
if (origin.protocol !== 'http:' || origin.hostname !== 'localhost' || !origin.port || origin.pathname !== '/'
    || origin.username || origin.password || origin.search || origin.hash) {
    throw new Error('Finance browser tests require an exact loopback HTTP origin');
}

export default defineConfig({
    testDir: './integration/browser',
    testMatch: 'financeBrowser.spec.ts',
    workers: 1,
    retries: 0,
    timeout: 20_000,
    reporter: [['list'], ['junit']],
    outputDir: '../backend/build/finance-browser-artifacts',
    use: {
        baseURL: 'http://localhost:3000',
        browserName: 'chromium',
        launchOptions: { executablePath: process.env.MODTALE_FINANCE_CHROMIUM_PATH },
        serviceWorkers: 'block',
        screenshot: 'only-on-failure',
        trace: 'retain-on-failure'
    }
});
