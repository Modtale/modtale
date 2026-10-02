import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, test } from 'node:test';

const repository = fileURLToPath(new URL('../../', import.meta.url));
const script = path.join(repository, '.github/scripts/build-container.sh');
const imageId = `sha256:${'a'.repeat(64)}`;
let directory;
let log;
let env;

beforeEach(() => {
  directory = fs.mkdtempSync(path.join(os.tmpdir(), 'modtale-container-'));
  const bin = path.join(directory, 'bin');
  fs.mkdirSync(bin);
  log = path.join(directory, 'commands.jsonl');
  const mock = `#!${process.execPath}
import fs from 'node:fs';
import path from 'node:path';
const command = path.basename(process.argv[1]);
const args = process.argv.slice(2);
fs.appendFileSync(process.env.COMMAND_LOG, JSON.stringify({ command, args }) + '\\n');
if (command === 'docker' && args[0] === 'buildx') process.exit(Number(process.env.FAIL_BUILD || 0));
if (command === 'trivy') process.exit(Number(process.env.FAIL_SCAN || 0));
if (command === 'gcloud') process.exit(Number(process.env.FAIL_AUTH || 0));
if (command === 'docker' && args[0] === 'image' && args[1] === 'inspect') {
  const calls = fs.readFileSync(process.env.COMMAND_LOG, 'utf8').trim().split('\\n').map(JSON.parse);
  const count = calls.filter(call => call.command === 'docker' && call.args[1] === 'inspect').length;
  console.log(process.env.CHANGE_IMAGE && count > 1 ? 'sha256:changed' : '${imageId}');
}
if (command === 'docker' && args[0] === 'push') process.exit(Number(process.env.FAIL_PUSH || 0));
`;
  // .mjs keeps these fixtures independent of the checkout's package settings.
  fs.writeFileSync(path.join(bin, 'mock.mjs'), mock, { mode: 0o755 });
  for (const name of ['docker', 'gcloud', 'trivy']) {
    fs.copyFileSync(path.join(bin, 'mock.mjs'), path.join(bin, name));
  }
  fs.writeFileSync(path.join(bin, 'package.json'), '{"type":"module"}');
  env = {
    ...process.env,
    PATH: `${bin}${path.delimiter}${process.env.PATH}`,
    COMMAND_LOG: log,
    PROJECT_ID: 'test-project',
    ACTIONS_RUNTIME_TOKEN: 'runtime-fixture',
    ACTIONS_RESULTS_URL: 'https://cache.example.test/',
    FAIL_BUILD: '', FAIL_SCAN: '', FAIL_AUTH: '', FAIL_PUSH: '', CHANGE_IMAGE: '',
  };
});
afterEach(() => fs.rmSync(directory, { recursive: true, force: true }));

function run(component = 'backend', tag = 'dev', args = [], overrides = {}) {
  const result = spawnSync('bash', [script, component, tag, ...args], {
    cwd: directory, encoding: 'utf8', env: { ...env, ...overrides },
  });
  const commands = fs.existsSync(log)
    ? fs.readFileSync(log, 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse)
    : [];
  return { ...result, commands };
}

test('one Buildx build loads and scans the exact image before one release push', () => {
  const result = run('frontend', 'feature-test', ['--build-arg', 'PUBLIC_API_URL=https://api.example.test/api/v1']);
  assert.equal(result.status, 0, result.stdout + result.stderr);
  const builds = result.commands.filter(call => call.command === 'docker' && call.args[0] === 'buildx');
  assert.equal(builds.length, 1);
  assert.deepEqual(builds[0].args.slice(0, 7), ['buildx', 'build', '--pull', '--load', '--platform', 'linux/amd64', '--cache-from']);
  assert.ok(builds[0].args.includes('type=gha,version=2,scope=modtale-frontend'));
  assert.ok(builds[0].args.includes('type=gha,version=2,scope=modtale-frontend,mode=max,ignore-error=true,timeout=5m'));
  assert.ok(builds[0].args.includes('gcr.io/test-project/modtale-frontend:feature-test'));
  assert.ok(builds[0].args.includes('PUBLIC_API_URL=https://api.example.test/api/v1'));
  assert.ok(!result.commands.some(call => call.command === 'docker' && call.args[0] === 'pull'));
  assert.ok(!builds[0].args.includes('--push'));
  const scan = result.commands.findIndex(call => call.command === 'trivy');
  const push = result.commands.findIndex(call => call.command === 'docker' && call.args[0] === 'push');
  assert.ok(scan > result.commands.indexOf(builds[0]));
  assert.ok(push > scan);
  assert.deepEqual(result.commands[scan].args, [
    'image', '--config', '/dev/null', '--ignorefile', '/dev/null', '--disable-telemetry',
    '--image-src', 'docker', '--scanners', 'vuln', '--severity', 'CRITICAL',
    '--ignore-unfixed', '--exit-code', '1', '--timeout', '10m', imageId,
  ]);
  assert.deepEqual(result.commands.filter(call => call.command === 'docker' && call.args[0] === 'push'), [
    { command: 'docker', args: ['push', 'gcr.io/test-project/modtale-frontend:feature-test'] },
  ]);
});

test('each component has its own cache and keeps its existing deployment image tag', () => {
  for (const [component, tag, args] of [
    ['backend', 'latest', []], ['frontend', 'dev', []], ['status', 'latest', ['-f', 'Dockerfile.status']],
  ]) {
    fs.writeFileSync(log, '');
    const result = run(component, tag, args);
    assert.equal(result.status, 0, result.stderr);
    const build = result.commands.find(call => call.args[0] === 'buildx');
    assert.ok(build.args.includes(`type=gha,version=2,scope=modtale-${component}`));
    const pushes = result.commands.filter(call => call.command === 'docker' && call.args[0] === 'push');
    assert.equal(pushes.length, 1);
    assert.equal(pushes[0].args[1], `gcr.io/test-project/modtale-${component}:${tag}`);
  }
});

test('status alias is the same scanned image and each distinct tag is pushed only once', () => {
  const result = run('status', 'dev', ['--file=Dockerfile.status']);
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.commands.filter(call => call.command === 'docker' && call.args[0] === 'tag'), [
    { command: 'docker', args: ['tag', imageId, 'gcr.io/test-project/modtale-status:latest'] },
  ]);
  assert.deepEqual(result.commands.filter(call => call.command === 'docker' && call.args[0] === 'push').map(call => call.args[1]), [
    'gcr.io/test-project/modtale-status:dev', 'gcr.io/test-project/modtale-status:latest',
  ]);
});

test('build/test failures, scan findings, scanner errors, and changed images never publish', () => {
  for (const overrides of [{ FAIL_BUILD: '1' }, { FAIL_SCAN: '1' }, { FAIL_SCAN: '2' }, { CHANGE_IMAGE: 'true' }]) {
    fs.writeFileSync(log, '');
    const result = run('backend', 'dev', [], overrides);
    assert.notEqual(result.status, 0);
    assert.ok(!result.commands.some(call => call.command === 'docker' && call.args[0] === 'push'));
    if (overrides.FAIL_BUILD) assert.ok(!result.commands.some(call => call.command === 'trivy'));
  }
});

test('authentication and publication errors stay fatal without rebuilding or retrying a push', () => {
  for (const overrides of [{ FAIL_AUTH: '1' }, { FAIL_PUSH: '1' }]) {
    fs.writeFileSync(log, '');
    const result = run('backend', 'dev', [], overrides);
    assert.notEqual(result.status, 0);
    assert.ok(result.commands.filter(call => call.command === 'docker' && call.args[0] === 'buildx').length <= 1);
    assert.ok(result.commands.filter(call => call.command === 'docker' && call.args[0] === 'push').length <= 1);
  }
});

test('cache v2 runtime variables are required before any build or registry operation', () => {
  for (const variable of ['ACTIONS_RUNTIME_TOKEN', 'ACTIONS_RESULTS_URL']) {
    const result = run('backend', 'dev', [], { [variable]: '' });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /GitHub Actions cache/);
    assert.deepEqual(result.commands, []);
  }
});

test('release identity and pre-scan publishing cannot be overridden by build arguments', () => {
  for (const args of [['--push'], ['--output=type=registry'], ['-t', 'other:tag'], ['--build-arg']]) {
    const result = run('backend', 'dev', args);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /::error::/);
    assert.deepEqual(result.commands, []);
  }
  for (const [component, tag] of [['other', 'dev'], ['backend', 'bad/tag'], ['backend', '-flag']]) {
    assert.notEqual(run(component, tag).status, 0);
  }
});

test('component Dockerfiles gate tests/typechecks before the runtime image is assembled', () => {
  assert.match(fs.readFileSync(path.join(repository, 'backend/Dockerfile'), 'utf8'), /RUN \.\/gradlew test bootJar --no-daemon/);
  assert.match(fs.readFileSync(path.join(repository, 'backend/Dockerfile.status'), 'utf8'), /RUN \.\/gradlew test statusServiceJar --no-daemon/);
  assert.match(fs.readFileSync(path.join(repository, 'frontend/Dockerfile'), 'utf8'), /RUN npm run check && npm test && npm run build && npm run test:html-cache && npm prune/);
});

test('layer-cache contexts exclude generated artifacts and local credentials', () => {
  for (const component of ['backend', 'frontend']) {
    const ignored = fs.readFileSync(path.join(repository, component, '.dockerignore'), 'utf8').split('\n');
    for (const entry of ['build', '**/.env', '**/.env.*', '**/*.pem', '**/*.key', '**/gha-creds-*.json', '**/secrets']) {
      assert.ok(ignored.includes(entry), `${component} must exclude ${entry}`);
    }
    assert.ok(!ignored.includes('src'));
    assert.ok(!ignored.includes('scripts'));
  }
});

test('deployment uses a container-driver Buildx, explicit cache runtime, and immutable Trivy installer', () => {
  const workflow = fs.readFileSync(path.join(repository, '.github/workflows/ci-cd.yml'), 'utf8');
  assert.match(workflow, /runs-on: ubuntu-latest/);
  assert.match(workflow, /uses: docker\/setup-buildx-action@[a-f0-9]{40} # v4/);
  assert.match(workflow, /driver: docker-container/);
  assert.match(workflow, /uses: crazy-max\/ghaction-github-runtime@[a-f0-9]{40} # v4/);
  assert.match(workflow, /uses: aquasecurity\/setup-trivy@81e514348e19b6112ce2a7e3ecbafe19c1e1f567/);
  assert.match(workflow, /version: v0\.75\.0/);
});

test('build tooling setup covers preview credential rotations while unchanged images are reused', () => {
  const workflow = fs.readFileSync(path.join(repository, '.github/workflows/ci-cd.yml'), 'utf8');
  const setup = [...workflow.matchAll(/      - name: (?:Set up Docker Buildx|Expose GitHub Actions cache runtime|Install pinned Trivy scanner)\n        if: ([^\n]+)/g)];
  assert.equal(setup.length, 3);
  for (const [, condition] of setup) {
    assert.match(condition, /env\.R2_RUNTIME_CREDENTIALS_REPLACED == 'true'/);
  }
  // The outer rollout condition includes credential rotation, but a new image
  // is built only for changed application code or a service's first deployment.
  assert.match(workflow, /if \[ "\$\{\{ steps\.filter\.outputs\.backend \}\}" = "true" \] \|\| \[ "\$BACKEND_EXISTS" = "false" \]; then\n            bash \.\.\/\.github\/scripts\/build-container\.sh backend "\$TAG"/);
  assert.match(workflow, /Reusing the existing backend image for a configuration-only rollout/);
});
