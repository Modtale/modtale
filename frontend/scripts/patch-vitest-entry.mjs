import { readFile, writeFile } from 'node:fs/promises';
import { realpathSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const frontendRoot = path.resolve(scriptDir, '..');
const vitestEntryPath = path.resolve(frontendRoot, 'node_modules/vitest/vitest.mjs');

const patchedEntry = `#!/usr/bin/env node
import { realpathSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = realpathSync(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..'))

process.chdir(frontendRoot)
for (let i = 0; i < process.argv.length; i += 1) {
  const arg = process.argv[i]
  const normalizedArg = arg.startsWith('/') ? realpathSync(arg) : null
  if (normalizedArg === frontendRoot) {
    process.argv[i] = '.'
    continue
  }
  if (normalizedArg && normalizedArg.startsWith(\`\${frontendRoot}\${path.sep}\`)) {
    process.argv[i] = \`.\${normalizedArg.slice(frontendRoot.length)}\`
  }
}

await import('./dist/cli.js')
`;

try {
    const current = await readFile(vitestEntryPath, 'utf8');
    if (current !== patchedEntry) {
        await writeFile(vitestEntryPath, patchedEntry, 'utf8');
    }
} catch (error) {
    console.warn(`Skipping Vitest entry patch: ${error instanceof Error ? error.message : String(error)}`);
}
