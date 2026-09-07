import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, test } from 'node:test';
import { collectionNames, readFixtureCollections } from '../scripts/fixture-files.mjs';

let directory;
beforeEach(() => {
  directory = fs.mkdtempSync(path.join(os.tmpdir(), 'modtale fixtures '));
  for (const name of collectionNames) {
    fs.writeFileSync(path.join(directory, `${name}.json`), JSON.stringify([{ _id: name }]));
  }
});
afterEach(() => fs.rmSync(directory, { recursive: true, force: true }));

test('loads all collections before a caller can start replacing database contents', () => {
  const collections = readFixtureCollections(directory);
  assert.deepEqual([...collections.keys()], collectionNames);
  assert.deepEqual(collections.get('users'), [{ _id: 'users' }]);
});

test('rejects a missing final collection before returning any fixtures', () => {
  fs.unlinkSync(path.join(directory, `${collectionNames.at(-1)}.json`));
  assert.throws(() => readFixtureCollections(directory), { code: 'ENOENT' });
});

test('rejects malformed JSON and non-array collections', () => {
  const file = path.join(directory, 'users.json');
  fs.writeFileSync(file, '{');
  assert.throws(() => readFixtureCollections(directory), SyntaxError);
  fs.writeFileSync(file, '{}');
  assert.throws(() => readFixtureCollections(directory), /must contain a JSON array/);
});
