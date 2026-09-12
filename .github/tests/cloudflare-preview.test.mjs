import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';

for (const readOnly of [true, false]) {
    test(`preview token policy preserves bucket isolation (readOnly=${readOnly})`, () => {
        const source = `
            import assert from 'node:assert/strict';
            process.argv[2] = ${JSON.stringify(readOnly ? 'provision-read-only' : 'provision')};
            Object.assign(process.env, {CLOUDFLARE_ACCOUNT_ID:'account', CLOUDFLARE_R2_PROVISIONER:'test', CLOUDFLARE_API_TOKEN_PROVISIONER:'test', R2_BUCKET_NAME:'fixture-bucket'});
            delete process.env.GITHUB_ENV;
            delete process.env.R2_RUNTIME_ENV_FILE;
            let policyChecked = false;
            globalThis.fetch = async (url, init = {}) => {
                let result = {};
                if (url.includes('/permission_groups')) result = [{id:'read',name:'Workers R2 Storage Bucket Item Read'}, {id:'write',name:'Workers R2 Storage Bucket Item Write'}];
                if (url.endsWith('/tokens') && init.method === 'POST') {
                    const body = JSON.parse(init.body);
                    assert.deepEqual(body.policies[0].permission_groups, ${JSON.stringify(readOnly ? [{id:'read'}] : [{id:'read'},{id:'write'}])});
                    assert.deepEqual(body.policies[0].resources, {'com.cloudflare.edge.r2.bucket.account_default_fixture-bucket':'*'});
                    assert.equal(body.policies.length, 1);
                    policyChecked = true;
                    result = {id:'test-key',value:'test-value'};
                }
                return {ok:true,status:200,text:async()=>JSON.stringify({success:true,result})};
            };
            await import(${JSON.stringify(new URL('../scripts/cloudflare-r2-preview.mjs', import.meta.url).href)});
            assert.equal(policyChecked, true);
        `;
        const result = spawnSync(process.execPath, ['--input-type=module', '-e', source], {encoding:'utf8'});
        assert.equal(result.status, 0, result.stderr);
    });
}
