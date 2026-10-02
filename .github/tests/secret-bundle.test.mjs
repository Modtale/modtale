import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
test('secret bundle tooling passes synthetic boundary, migration, pin, and rollback contracts', () => {
  const result = spawnSync('python3', ['-m', 'unittest', 'discover', '-s', '.github/tests', '-p', '*bundle*test.py'], {
    cwd: root, encoding: 'utf8', timeout: 30000,
  });
  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
});
