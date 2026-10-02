"""Synthetic tests for the dormant transitional preview provisioning guards."""
import json
import os
import sys
from pathlib import Path
import unittest
from unittest.mock import patch
from secret_bundle_ci_test import ENV, HEAD, ci, plan, lifecycle


class LegacyProvisionApprovalTests(unittest.TestCase):
    def invoke(self, operation, env, extra=(), response=None):
        import contextlib,io
        calls=[]
        def transport(request):
            calls.append(request)
            if request['api']=='github':
                return {'status':200,'body':{'data':{'repository':{'nameWithOwner':plan.REPOSITORY,
                    'refs':{'nodes':[{'name':'alpha','target':{'oid':HEAD}}],
                            'pageInfo':{'hasNextPage':False,'endCursor':None}}}}}}
            return response if response is not None else {'status':403,'body':{}}
        with patch.dict(os.environ,env,clear=True),patch.object(sys,'argv',['ci',operation,*extra]),patch('secret_bundle_transport.create_transport',return_value=transport),patch('secret_bundle_ci.subprocess.run',return_value=__import__('types').SimpleNamespace(returncode=0,stdout=b'[]')),contextlib.redirect_stderr(io.StringIO()),contextlib.redirect_stdout(io.StringIO()):
            result=ci.main()
        return result,calls

    def test_legacy_new_and_replacement_default_closed_without_any_cloud_request(self):
        env={**ENV,'MODTALE_SECRET_BUNDLES_ENABLED':'false'}
        for kind in ('new','replacement'):
            result,calls=self.invoke('guard-legacy-provision',env,(kind,))
            self.assertEqual(result,1);self.assertEqual(calls,[])
        env['MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED']='modtale-backend-other'
        result,calls=self.invoke('guard-legacy-provision',env,('replacement',))
        self.assertEqual(result,1);self.assertEqual(calls,[])

    def test_exact_environment_service_approval_works_while_bundle_flag_stays_off(self):
        settings=ci.resolve_settings({'BUNDLE_SETTINGS_ENVIRONMENT':'branch-preview',
            'BUNDLE_SETTINGS_BRANCH_PREVIEW_CREDENTIAL_PROVISION_APPROVED':ENV['BACKEND_SERVICE']})
        self.assertEqual(settings['MODTALE_SECRET_BUNDLES_ENABLED'],'false')
        result,calls=self.invoke('guard-legacy-provision',{**ENV,**settings},('replacement',))
        self.assertEqual(result,0);self.assertEqual(len(calls),1);self.assertEqual(calls[0]['api'],'github')
        result,calls=self.invoke('guard-legacy-provision',{**ENV,**settings,'RUNTIME_SERVICE_ACCOUNT':'different@example.test'},('replacement',))
        self.assertEqual(result,1);self.assertEqual(calls,[])

    def test_new_service_approval_cannot_authorize_replacement_or_existing_service(self):
        env={**ENV,'MODTALE_SECRET_BUNDLES_ENABLED':'false','MODTALE_SECRET_BUNDLE_NEW_SERVICE_APPROVED':ENV['BACKEND_SERVICE']}
        result,calls=self.invoke('guard-legacy-provision',env,('replacement',))
        self.assertEqual(result,1);self.assertEqual(calls,[])
        full='projects/'+ENV['PROJECT_ID']+'/locations/us-central1/services/'+ENV['BACKEND_SERVICE']
        result,calls=self.invoke('guard-legacy-provision',env,('new',),{'status':200,'body':{'name':full}})
        self.assertEqual(result,1);self.assertEqual(len(calls),1)
        result,calls=self.invoke('guard-legacy-provision',env,('new',),{'status':404,'body':{}})
        self.assertEqual(result,0);self.assertEqual(len(calls),3)

    def test_new_legacy_approval_requires_authoritative_absence_of_all_four_resources(self):
        env={**ENV,'MODTALE_SECRET_BUNDLE_NEW_SERVICE_APPROVED':ENV['BACKEND_SERVICE']}
        requests=[]
        def transport(req):requests.append(req);return {'status':404,'body':{}}
        for resource in plan.credential_keys('branch-preview','alpha'):
            subject=ci.BundleCI(transport,env,runner=lambda *args,**kwargs:json.dumps([{'name':'projects/145553429208/secrets/'+resource}]).encode())
            subject.inventory.github_lifecycle=lambda boundary:lifecycle()
            with self.assertRaises(ci.CIError):subject.require_legacy_provision_approval()
        subject=ci.BundleCI(transport,env,runner=lambda *args,**kwargs:b'[]')
        subject.inventory.github_lifecycle=lambda boundary:lifecycle()
        subject.require_legacy_provision_approval()
        subject.runner=lambda *args,**kwargs:ci.fail()
        with self.assertRaises(ci.CIError):subject.require_legacy_provision_approval()

    def test_exact_replacement_approval_is_not_overwritten_by_new_service_gate(self):
        subject=ci.BundleCI(lambda req:ci.fail(),{**ENV,
            'MODTALE_SECRET_BUNDLE_NEW_SERVICE_APPROVED':ENV['BACKEND_SERVICE'],
            'MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED':ENV['BACKEND_SERVICE']})
        subject.inventory.github_lifecycle=lambda boundary:lifecycle()
        subject.require_provision_approval()

    def test_template_reader_needs_its_own_exact_target_approval(self):
        env={**ENV,'MODTALE_SECRET_BUNDLES_ENABLED':'false','R2_BUCKET_NAME':'modtale-preview-template',
             'R2_TOKEN_NAME':'modtale-preview-template-reader','RUNTIME_SERVICE_ACCOUNT':ci.RUNTIME_ACCOUNTS['branch-preview']}
        for approval in ('',ENV['BACKEND_SERVICE']):
            result,calls=self.invoke('guard-template-provision',{**env,'MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED':approval})
            self.assertEqual(result,1);self.assertEqual(calls,[])
        settings=ci.resolve_settings({'BUNDLE_SETTINGS_ENVIRONMENT':'branch-preview',
            'BUNDLE_SETTINGS_BRANCH_PREVIEW_CREDENTIAL_PROVISION_APPROVED':'modtale-preview-template-reader'})
        result,calls=self.invoke('guard-template-provision',{**env,**settings})
        self.assertEqual(result,0);self.assertEqual(calls,[])
        result,calls=self.invoke('guard-template-provision',{**env,**settings,'R2_BUCKET_NAME':'modtale-binaries'})
        self.assertEqual(result,1);self.assertEqual(calls,[])

    def test_reused_credentials_do_not_add_an_existing_accessor_binding(self):
        calls=[]
        member='serviceAccount:'+ENV['RUNTIME_SERVICE_ACCOUNT']
        def runner(argv,env=None):
            calls.append(argv)
            return json.dumps({'bindings':[{'role':'roles/secretmanager.secretAccessor','members':[member]}]}).encode()
        subject=ci.BundleCI(lambda req:ci.fail(),ENV,runner=runner)
        subject.ensure_legacy_accessor('branch-preview-alpha-r2-access-key')
        self.assertEqual(len(calls),1)
        self.assertIn('get-iam-policy',calls[0])
        self.assertFalse(any('add-iam-policy-binding' in argv for argv in calls))
        subject.env['RUNTIME_SERVICE_ACCOUNT']='different@example.test';calls.clear()
        with self.assertRaises(ci.CIError):subject.ensure_legacy_accessor('branch-preview-alpha-r2-access-key')
        self.assertEqual(calls,[])

    def test_missing_accessor_requires_exact_approval_and_fresh_ownership(self):
        calls=[]
        def runner(argv,env=None):calls.append(argv);return b'{"bindings":[]}'
        subject=ci.BundleCI(lambda req:ci.fail(),ENV,runner=runner)
        with self.assertRaises(ci.CIError):subject.ensure_legacy_accessor('branch-preview-alpha-r2-access-key')
        self.assertFalse(any('add-iam-policy-binding' in argv for argv in calls))
        subject.env['MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED']=ENV['BACKEND_SERVICE']
        subject.inventory.github_lifecycle=lambda boundary:lifecycle()
        subject.ensure_legacy_accessor('branch-preview-alpha-r2-access-key')
        writes=[argv for argv in calls if 'add-iam-policy-binding' in argv]
        self.assertEqual(len(writes),1)
        self.assertIn('serviceAccount:'+ENV['RUNTIME_SERVICE_ACCOUNT'],writes[0])
        subject.inventory.github_lifecycle=lambda boundary:{'repository':plan.REPOSITORY,'complete':True,'items':[]}
        with self.assertRaises(Exception):subject.ensure_legacy_accessor('branch-preview-alpha-r2-secret-key')
        self.assertEqual(sum('add-iam-policy-binding' in argv for argv in calls),1)

    def test_approved_provision_stops_after_branch_closure_or_head_change(self):
        subject=ci.BundleCI(lambda req:ci.fail(),{**ENV,'MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED':ENV['BACKEND_SERVICE']})
        for snapshot in ({'repository':plan.REPOSITORY,'complete':True,'items':[]},
                         {'repository':plan.REPOSITORY,'complete':True,'items':[{'name':'alpha','head_sha':'c'*40}]}):
            subject.inventory.github_lifecycle=lambda boundary:snapshot
            with self.assertRaises(Exception):subject.require_provision_approval(replacement=True)

    def test_flag_off_lifecycle_callback_is_read_only_and_checks_current_head(self):
        result,calls=self.invoke('check-lifecycle',{**ENV,'MODTALE_SECRET_BUNDLES_ENABLED':'false'})
        self.assertEqual(result,0);self.assertEqual(len(calls),1);self.assertEqual(calls[0]['api'],'github')
        result,calls=self.invoke('check-lifecycle',{**ENV,'MODTALE_SECRET_BUNDLES_ENABLED':'false','GITHUB_SHA':'c'*40})
        self.assertEqual(result,1);self.assertEqual(len(calls),1)

    def test_workflow_guards_precede_every_legacy_token_provisioner(self):
        root=Path(__file__).parents[1]/'workflows'
        cd=(root/'ci-cd.yml').read_text();pr=(root/'pr-preview.yml').read_text()
        guard=cd.index('secret_bundle_ci.py guard-legacy-provision')
        valid_start=cd.index('if [ "$runtime_credentials_valid" = "true" ]')
        replacement_start=cd.index('Existing branch preview R2 runtime credentials are missing or invalid')
        self.assertLess(valid_start,replacement_start);self.assertLess(replacement_start,guard)
        self.assertLess(guard,cd.index('node .github/scripts/cloudflare-r2-preview.mjs provision'))
        self.assertLess(cd.index('secret_bundle_ci.py guard-template-provision'),cd.index('node .github/scripts/cloudflare-r2-preview.mjs provision-read-only'))
        self.assertLess(pr.index('secret_bundle_ci.py" guard-legacy-provision'),pr.index('cloudflare-r2-preview.mjs" provision'))
        self.assertIn('R2_BUNDLE_LIFECYCLE_SCRIPT="$GITHUB_WORKSPACE/.github/scripts/secret_bundle_ci.py"',cd)
        self.assertIn('R2_BUNDLE_LIFECYCLE_SCRIPT="$GITHUB_WORKSPACE/base/.github/scripts/secret_bundle_ci.py"',pr)
        self.assertIn('secret_bundle_ci.py ensure-legacy-accessor "$name"',cd)
        self.assertIn('secret_bundle_ci.py" ensure-legacy-accessor "$name"',pr)



if __name__ == '__main__': unittest.main()
