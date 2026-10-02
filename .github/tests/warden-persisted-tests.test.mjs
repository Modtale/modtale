import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, test } from 'node:test';

const script = fileURLToPath(new URL('../scripts/warden-persisted-tests.py', import.meta.url));
let directory;
beforeEach(() => { directory = fs.mkdtempSync(path.join(os.tmpdir(), 'warden-persisted-')); });
afterEach(() => fs.rmSync(directory, { recursive: true, force: true }));
function source(name, gate) {
  fs.writeFileSync(path.join(directory, `${name}.java`), `package net.modtale.test;\n@EnabledIfEnvironmentVariable(named="${gate}", matches="true")\nclass ${name} {}\n`);
}
function run(command) {
  return spawnSync('python3', [script, command, '--source', directory, '--reports', directory], { encoding: 'utf8' });
}
function report(name, attributes = 'tests="1" skipped="0" failures="0" errors="0"', children = '<testcase name="runs"/>') {
  fs.writeFileSync(path.join(directory, `TEST-net.modtale.test.${name}.xml`), `<testsuite name="net.modtale.test.${name}" ${attributes}>${children}</testsuite>`);
}
test('selects both database gates without activating unrelated live suites', () => {
  source('ReviewTest', 'WARDEN_REVIEW_DB_TEST');
  source('TransactionTest', 'WARDEN_REPAIR_TX_DB_TEST');
  source('LiveTest', 'NYOCF_LIVE_TESTS');
  const result = run('list');
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, 'net.modtale.test.ReviewTest\nnet.modtale.test.TransactionTest\n');
});
test('rejects empty suite selection and missing reports', () => {
  assert.notEqual(run('list').status, 0);
  source('ReviewTest', 'WARDEN_REVIEW_DB_TEST');
  assert.notEqual(run('verify').status, 0);
});
test('requires every selected suite to execute successfully', () => {
  source('ReviewTest', 'WARDEN_REVIEW_DB_TEST');
  source('TransactionTest', 'WARDEN_REPAIR_TX_DB_TEST');
  report('ReviewTest');
  assert.notEqual(run('verify').status, 0);
  report('TransactionTest');
  const result = run('verify');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /2 executed tests across 2 persisted Warden suites/);
});
test('rejects skipped, failed, empty, malformed and miscounted results', () => {
  source('ReviewTest', 'WARDEN_REVIEW_DB_TEST');
  for (const attributes of [
    'tests="1" skipped="1" failures="0" errors="0"',
    'tests="1" skipped="0" failures="1" errors="0"',
    'tests="1" skipped="0" failures="0" errors="1"',
    'tests="0" skipped="0" failures="0" errors="0"',
    'tests="2" skipped="0" failures="0" errors="0"',
    'tests="unknown" skipped="0" failures="0" errors="0"',
  ]) {
    report('ReviewTest', attributes);
    assert.notEqual(run('verify').status, 0, attributes);
  }
  report('ReviewTest', undefined, '<testcase name="hidden"><skipped/></testcase>');
  assert.notEqual(run('verify').status, 0);
});

test('fixture commands use supported mongosh results rather than legacy shell assertions', async () => {
  const { runInNewContext } = await import('node:vm');
  const workflow = fs.readFileSync(new URL('../workflows/warden-persisted.yml', import.meta.url), 'utf8');
  const expressions = [...workflow.matchAll(/^[ \t]+(?:--eval )?'(quit\(.+\))';?[ \t]*(?:then)?$/gm)].map(match => match[1]);
  assert.equal(expressions.length, 3);
  for (const expression of expressions) {
    for (const valid of [true, false]) {
      let status;
      runInNewContext(expression, {
        rs: { initiate: () => ({ ok: valid ? 1 : 0 }) },
        db: { adminCommand: () => ({ ok: valid ? 1 : 0 }), hello: () => ({ isWritablePrimary: valid }) },
        quit: code => { status = code; },
      });
      assert.equal(status, valid ? 0 : 1, expression);
    }
  }
  assert.doesNotMatch(workflow, /assert\.(?:commandWorked|soon)/);
});

test('large persisted fixtures receive bounded independent test workers', () => {
  const workflow = fs.readFileSync(new URL('../workflows/warden-persisted.yml', import.meta.url), 'utf8');
  const resources = fs.readFileSync(new URL('../scripts/warden-persisted-test-resources.gradle', import.meta.url), 'utf8');
  assert.match(workflow, /-I "\$PWD\/\.github\/scripts\/warden-persisted-test-resources\.gradle"/);
  assert.match(resources, /maxHeapSize = '2g'/);
  assert.match(resources, /maxParallelForks = 1/);
  assert.match(resources, /forkEvery = 1/);
  assert.match(workflow, /timeout-minutes: 35/);
  assert.match(workflow, /name: Run every persisted Warden suite\n        timeout-minutes: 30/);
});
