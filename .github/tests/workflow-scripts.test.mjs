import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, test } from 'node:test';

const scripts = fileURLToPath(new URL('../scripts/', import.meta.url));
let directory;
beforeEach(() => {
  directory = fs.mkdtempSync(path.join(os.tmpdir(), 'modtale-workflow-'));
});
afterEach(() => fs.rmSync(directory, { recursive: true, force: true }));

function run(script, overrides = {}) {
  const output = path.join(directory, 'output');
  fs.writeFileSync(output, '');
  const result = spawnSync('bash', [path.join(scripts, script)], {
    cwd: directory,
    encoding: 'utf8',
    env: {
      ...process.env,
      GITHUB_OUTPUT: output,
      GITHUB_REPOSITORY: 'Modtale/modtale',
      GITHUB_REPOSITORY_OWNER: 'Modtale',
      GITHUB_REF_NAME: 'audit',
      GH_TOKEN: 'test-token',
      ...overrides,
    },
  });
  assert.equal(result.status, 0, result.stdout + result.stderr);
  return fs.readFileSync(output, 'utf8');
}

test('PR creation and synchronization always retain their own test coverage', () => {
  for (const action of ['opened', 'reopened', 'synchronize']) {
    const output = run('should-run-tests-workflow.sh', {
      GITHUB_EVENT_NAME: 'pull_request', PR_ACTION: action,
    });
    assert.match(output, /^should_run=true$/m);
  }
});

test('push skips only when the GitHub API confirms an open PR', () => {
  const bin = path.join(directory, 'bin');
  fs.mkdirSync(bin);
  const gh = path.join(bin, 'gh');
  fs.writeFileSync(gh, '#!/bin/sh\nprintf "1\\n"\n', { mode: 0o755 });
  const env = { GITHUB_EVENT_NAME: 'push', PATH: `${bin}${path.delimiter}${process.env.PATH}` };
  assert.match(run('should-run-tests-workflow.sh', env), /^should_run=false$/m);
  fs.writeFileSync(gh, '#!/bin/sh\nexit 1\n', { mode: 0o755 });
  assert.match(run('should-run-tests-workflow.sh', env), /^should_run=true$/m);
});

test('test-workflow changes select all components without requesting launcher packaging', () => {
  const git = (...args) => {
    const result = spawnSync('git', args, { cwd: directory, encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    return result.stdout.trim();
  };
  git('init', '--quiet');
  git('config', 'user.name', 'Audit Test');
  git('config', 'user.email', 'audit@example.test');
  git('config', 'commit.gpgsign', 'false');
  fs.writeFileSync(path.join(directory, 'README.md'), 'test');
  git('add', '.');
  git('commit', '--quiet', '-m', 'Initial fixture');
  const base = git('rev-parse', 'HEAD');
  fs.mkdirSync(path.join(directory, '.github/workflows'), { recursive: true });
  fs.writeFileSync(path.join(directory, '.github/workflows/tests.yml'), 'name: tests');
  git('add', '.');
  git('commit', '--quiet', '-m', 'Change test workflow');
  const output = run('detect-component-changes.sh', {
    GITHUB_EVENT_NAME: 'push', GITHUB_SHA: git('rev-parse', 'HEAD'), PUSH_BEFORE_SHA: base,
  });
  for (const component of ['frontend', 'backend', 'launcher']) {
    assert.match(output, new RegExp(`^${component}=true$`, 'm'));
  }
  assert.match(output, /^launcher_build=false$/m);
});

test('launcher channel and version follow the source branch', () => {
  for (const [branch, channel] of [['main', 'stable'], ['develop', 'develop']]) {
    const output = run('launcher-release-metadata.sh', {
      GITHUB_REF_NAME: branch, GITHUB_RUN_NUMBER: '42', GITHUB_RUN_ATTEMPT: '2', INPUT_VERSION: '',
    });
    assert.match(output, new RegExp(`^channel=${channel}$`, 'm'));
    assert.match(output, new RegExp(`^tag=launcher-${channel === 'develop' ? 'develop-' : ''}v0\\.2\\.42${channel === 'develop' ? '-develop.42.2' : ''}$`, 'm'));
  }
});

test('manual launcher versions retain their branch channel', () => {
  const output = run('launcher-release-metadata.sh', {
    GITHUB_REF_NAME: 'develop', GITHUB_RUN_NUMBER: '50', INPUT_VERSION: '1.2.3',
  });
  assert.match(output, /^version=1\.2\.3-develop\.50\.1$/m);
});

test('launcher publishing rejects other refs and unsafe or unsupported versions', () => {
  for (const overrides of [
    { GITHUB_REF_NAME: 'feature' },
    { GITHUB_REF_NAME: 'launcher-v1.0.0' },
    { INPUT_VERSION: '1.2.3; echo injected' },
    { INPUT_VERSION: '1.2.3-beta' },
    { INPUT_VERSION: '1.256.0' },
    { INPUT_VERSION: '1.2.65536' },
  ]) {
    const result = spawnSync('bash', [path.join(scripts, 'launcher-release-metadata.sh')], {
      encoding: 'utf8',
      env: { ...process.env, GITHUB_REF_NAME: 'main', GITHUB_RUN_NUMBER: '42', ...overrides },
    });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /::error::/);
  }
});
