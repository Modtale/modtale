import test from 'node:test';
import { readFileSync, existsSync } from 'node:fs';
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

test('backend Docker context retains every registered secret bundle processor and its tests', () => {
  const factories = readFileSync(resolve(root, 'backend/src/main/resources/META-INF/spring.factories'), 'utf8');
  const implementation = factories.trim().split('=')[1];
  const relative = implementation.replaceAll('.', '/') + '.java';
  const source = 'backend/src/main/java/' + relative;
  const testSource = source.replace('/main/', '/test/').replace('.java', 'Test.java');
  assert.ok(existsSync(resolve(root, source)), 'registered processor source must exist');
  assert.ok(existsSync(resolve(root, testSource)), 'registered processor test must exist');
  const ignored = readFileSync(resolve(root, 'backend/.dockerignore'), 'utf8').split(/\r?\n/).map(x => x.trim());
  assert.ok(ignored.includes('**/secrets'), 'keep protective secrets-directory exclusion');
  for (const path of [source, testSource]) {
    assert.ok(!path.split('/').includes('secrets'), 'registered Java package must not be excluded as credential material');
  }
});
