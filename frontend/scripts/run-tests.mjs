import { spawnSync } from 'node:child_process';
import { realpathSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// Resolve the checkout itself, even when invoked through a symlink. Do not
// modify Vitest's installed executable or derive our root from node_modules.
const frontendRoot = realpathSync(fileURLToPath(new URL('../', import.meta.url)));
const vitestEntry = fileURLToPath(new URL('./vitest.mjs', import.meta.resolve('vitest/package.json')));
const result = spawnSync(process.execPath, [vitestEntry, ...process.argv.slice(2)], {
    cwd: frontendRoot,
    stdio: 'inherit',
});

if (result.error) {
    console.error(result.error.message);
}
process.exitCode = result.status ?? 1;
