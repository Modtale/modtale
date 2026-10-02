import assert from 'node:assert/strict';
import fs from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const script = fileURLToPath(new URL('../scripts/configure-public-cache-purge.sh', import.meta.url));
function args(environment, enabled) {
  const result = spawnSync('bash', ['-c', 'set -euo pipefail; ARGS=("existing"); source "$1"; printf "%s\\0" "${ARGS[@]}"', 'bash', script], {
    encoding: 'utf8', env: { ...process.env, ENV_TYPE: environment, PUBLIC_CACHE_PURGE_ENABLED: enabled },
  });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout.split('\0').filter(Boolean);
}
test('only explicitly enabled production/development attach the dedicated existing secret', () => {
  for (const environment of ['prod', 'dev']) {
    assert.deepEqual(args(environment, 'true'), ['existing', '--update-env-vars',
      'PUBLIC_CACHE_PURGE_ENABLED=true,CLOUDFLARE_ZONE_ID=ff20a05c676ca023b19d20e177054945',
      '--update-secrets', 'CLOUDFLARE_CACHE_PURGE_TOKEN=MODTALE_PUBLIC_CACHE_PURGE_TOKEN:latest']);
  }
});
test('default/off/malformed values and every preview environment stay disabled with no credential binding', () => {
  for (const environment of ['prod', 'dev', 'branch-preview', 'preview', '', 'prod;false']) {
    for (const enabled of ['', 'false', 'TRUE', 'true', 'true;false']) {
      if (['prod', 'dev'].includes(environment) && enabled === 'true') continue;
      assert.deepEqual(args(environment, enabled), ['existing', '--update-env-vars', 'PUBLIC_CACHE_PURGE_ENABLED=false']);
    }
  }
});
test('workflow uses environment-scoped opt-in and never fetches or copies the credential', () => {
  const workflow = fs.readFileSync(fileURLToPath(new URL('../workflows/ci-cd.yml', import.meta.url)), 'utf8');
  assert.match(workflow, /PUBLIC_CACHE_PURGE_ENABLED: \$\{\{ vars\.PUBLIC_CACHE_PURGE_ENABLED \|\| 'false' \}\}/);
  assert.match(workflow, /source \.\.\/\.github\/scripts\/configure-public-cache-purge\.sh/);
  const source = fs.readFileSync(script, 'utf8');
  assert.doesNotMatch(source, /versions access|secrets\.CLOUDFLARE|add-iam-policy|tokens|curl/);
});
