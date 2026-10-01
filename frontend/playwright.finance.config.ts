import { defineConfig } from '@playwright/test';

if (!process.env.MODTALE_FINANCE_TEST_ORIGIN) throw new Error('Run backend financeFrontendIntegrationTest');

export default defineConfig({
    testDir: './integration/browser',
    testMatch: 'financeBrowser.spec.ts',
    workers: 1,
    retries: 0,
    timeout: 20_000,
    reporter: [['list'], ['junit']],
    outputDir: '../backend/build/finance-browser-artifacts',
    use: {
        baseURL: 'http://127.0.0.1:3000',
        browserName: 'chromium',
        launchOptions: { executablePath: process.env.MODTALE_FINANCE_CHROMIUM_PATH },
        serviceWorkers: 'block',
        screenshot: 'only-on-failure',
        trace: 'retain-on-failure'
    }
});
