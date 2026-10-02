import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

scripts=Path(__file__).parents[1]/'scripts'
sys.path.insert(0,str(scripts))
import migrate_secret_bundle as m

class FakeCloud:
    def __init__(self, boundary='production', bad_iam=False, populated=False, changed=False):
        self.boundary=boundary; self.bad_iam=bad_iam;self.populated=populated;self.changed=changed;self.calls=[];self.upload=None
    def __call__(self,*args):
        self.calls.append(args)
        if args[1]=='get-iam-policy':
            return {'bindings':[{'role':'roles/secretmanager.secretAccessor','members':['serviceAccount:unexpected'] if self.bad_iam else m.EXPECTED_MEMBERS[self.boundary]}]}
        if args[2]=='list':
            if args[3].startswith('MODTALE_CONFIG_'):
                return [{'name':'projects/p/secrets/s/versions/1','state':'ENABLED'}] if self.populated else []
            version='999' if self.changed else '1'
            return [{'name':f'projects/p/secrets/s/versions/{version}','state':'ENABLED'}]
        if args[2]=='describe':return {'state':'ENABLED'}
        if args[2]=='access':
            Path(args[args.index('--out-file')+1]).write_text('SYNTHETIC_CREDENTIAL_${LITERAL}')
            return {}
        if args[2]=='add':
            self.upload=Path(args[args.index('--data-file')+1]).read_bytes()
            return {'name':'projects/p/secrets/s/versions/42'}
        raise AssertionError('Unexpected operation')

class MigrationTests(unittest.TestCase):
    def test_fixed_destination_metadata_and_safe_output(self):
        api=FakeCloud();stream=io.StringIO()
        with contextlib.redirect_stdout(stream),contextlib.redirect_stderr(stream):result=m.migrate('production',api)
        self.assertEqual(result['destination'],'MODTALE_CONFIG_PRODUCTION')
        self.assertEqual(result['version'],'42')
        self.assertNotIn('SYNTHETIC',json.dumps(result)+stream.getvalue())
        self.assertEqual(json.loads(api.upload)['secrets']['HYTALE_CLIENT_SECRET'],'SYNTHETIC_CREDENTIAL_${LITERAL}')
        self.assertTrue(all('--project' in c and c[c.index('--project')+1]=='gen-lang-client-0244308719' for c in api.calls))
        self.assertFalse(any(x in c for c in api.calls for x in ('delete','destroy','disable','create','add-iam-policy-binding','deploy')))
    def test_iam_failure_before_payload(self):
        api=FakeCloud(bad_iam=True)
        with self.assertRaises(m.MigrationError):m.migrate('production',api)
        self.assertFalse(any('access' in c for c in api.calls))
    def test_populated_destination_refuses_retry(self):
        api=FakeCloud(populated=True)
        with self.assertRaises(m.MigrationError):m.migrate('production',api)
        self.assertFalse(any('access' in c for c in api.calls))
    def test_source_change_refuses_payload_read(self):
        api=FakeCloud(changed=True)
        with self.assertRaises(m.MigrationError):m.migrate('production',api)
        self.assertFalse(any('access' in c for c in api.calls))
    def test_arbitrary_destination_rejected(self):
        api=FakeCloud()
        with self.assertRaises(m.MigrationError):m.migrate('../../other',api)
        self.assertEqual(api.calls,[])
    def test_no_unapproved_conditions(self):
        policy={'bindings':[{'role':'roles/secretmanager.secretAccessor','members':m.EXPECTED_MEMBERS['production'],'condition':{'expression':'true'}}]}
        self.assertFalse(m.policy_ok(policy,'production'))
    def test_no_production_hytale_in_other_manifests(self):
        manifest=json.loads((scripts/'secret-bundle-source-manifest.json').read_text())
        for boundary in ['shared','branch-preview','pr-preview']:
            self.assertNotIn('HYTALE_CLIENT_SECRET',manifest[boundary]['sourceVersions'])
        self.assertNotIn('MODTALE_PUBLIC_CACHE_PURGE_TOKEN',manifest['shared']['sourceVersions'])
        self.assertNotIn('WARDEN_API_KEY',manifest['shared']['sourceVersions'])
        self.assertEqual(len(manifest['shared']['sourceVersions']),29)
    def test_preview_gate_blocks_before_cloud_access(self):
        with patch.dict('os.environ', {'GITHUB_EVENT_NAME':'workflow_dispatch','GITHUB_REPOSITORY':'Modtale/modtale','GITHUB_REF':'refs/heads/main','BUNDLE_MIGRATION_CONFIRM':'PUBLISH_BUNDLE','BUNDLE_BOUNDARY':'branch-preview','GITHUB_ACTOR':'Villagers654','GITHUB_TRIGGERING_ACTOR':'Villagers654'}, clear=True):
            with patch.object(m,'migrate') as call, contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(m.main(),1)
                call.assert_not_called()
    def test_authentication_never_falls_back_across_projects(self):
        workflow=(scripts.parent/'workflows/migrate-secret-bundle.yml').read_text()
        self.assertIn("if: inputs.boundary == 'pr-preview'",workflow)
        self.assertIn("if: inputs.boundary != 'pr-preview'",workflow)
        self.assertNotIn('secrets.GCP_PREVIEW_CREDENTIALS ||',workflow)
    def test_source_change_after_read_prevents_publish(self):
        base=FakeCloud()
        def race(*args):
            result=base(*args)
            if args[2:3]==('access',):base.changed=True
            return result
        with self.assertRaises(m.MigrationError):m.migrate('production',race)
        self.assertIsNone(base.upload)
    def test_workflow_validates_before_authentication(self):
        workflow=(scripts.parent/'workflows/migrate-secret-bundle.yml').read_text()
        self.assertIn('contains(fromJSON',workflow)
        self.assertLess(workflow.index('Validate migration tooling with synthetic data'),workflow.index('Authenticate main-project migration'))
    def test_only_verified_owner_manual_entrypoint_is_allowed(self):
        env={'GITHUB_EVENT_NAME':'workflow_dispatch','GITHUB_REPOSITORY':'Modtale/modtale','GITHUB_REF':'refs/heads/main','BUNDLE_MIGRATION_CONFIRM':'PUBLISH_BUNDLE','BUNDLE_BOUNDARY':'shared','GITHUB_ACTOR':'Villagers654','GITHUB_TRIGGERING_ACTOR':'Villagers654'}
        with patch.dict('os.environ',env,clear=True),patch.object(m,'migrate',return_value={'version':'1'}) as call,contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(m.main(),0)
            call.assert_called_once_with('shared')
        for key in ('GITHUB_EVENT_NAME','GITHUB_REPOSITORY','GITHUB_REF','BUNDLE_MIGRATION_CONFIRM','GITHUB_ACTOR','GITHUB_TRIGGERING_ACTOR'):
            changed=dict(env);changed[key]='not-authorized'
            with patch.dict('os.environ',changed,clear=True),patch.object(m,'migrate') as call,contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(m.main(),1)
                call.assert_not_called()
        workflow=(scripts.parent/'workflows/migrate-secret-bundle.yml').read_text()
        self.assertIn("github.actor == 'Villagers654'",workflow)
        self.assertIn("github.triggering_actor == 'Villagers654'",workflow)
        self.assertIn('contents: read',workflow)
        self.assertNotIn('actions: write',workflow)
    def test_workflow_manual_only(self):
        workflow=(scripts.parent/'workflows/migrate-secret-bundle.yml').read_text()
        self.assertIn('workflow_dispatch:',workflow)
        self.assertNotIn('  push:',workflow)
        self.assertNotIn('  schedule:',workflow)
        self.assertIn('cancel-in-progress: false',workflow)
        self.assertIn('queue: max',workflow)
        self.assertNotIn('upload-artifact',workflow)

if __name__=='__main__':unittest.main()
