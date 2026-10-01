import { spawn } from 'node:child_process';
import path from 'node:path';
import { createServer } from 'vite';

const raw = process.env.MODTALE_FINANCE_TEST_ORIGIN;
if (!raw) throw new Error('Run backend financeFrontendIntegrationTest to start the isolated servlet');
const origin = new URL(raw);
if (origin.protocol !== 'http:' || origin.hostname !== '127.0.0.1' || !origin.port || origin.pathname !== '/'
    || origin.username || origin.password || origin.search || origin.hash) {
    throw new Error('Finance browser tests require an exact loopback HTTP origin');
}

// This Vite entry is a test file, never an Astro route or a deployed fixture account.
const server = await createServer({
    configFile: false,
    root: process.cwd(),
    resolve: { alias: { '@': path.resolve('src') } },
    define: { 'import.meta.env.PUBLIC_API_URL': JSON.stringify(`${origin.origin}/api/v1`) },
    esbuild: { jsx: 'automatic' },
    server: { host: '127.0.0.1', port: 3000, strictPort: true, open: false }
});

try {
    await server.listen();
    const child = spawn(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--config', 'playwright.finance.config.ts'], {
        stdio: 'inherit', env: process.env
    });
    const result = await new Promise((resolve, reject) => {
        child.once('error', reject);
        child.once('exit', (code, signal) => resolve(code ?? (signal ? 1 : 0)));
    });
    process.exitCode = result;
} finally {
    await server.close();
}
