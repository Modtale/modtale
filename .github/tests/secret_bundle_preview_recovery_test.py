"""Full fake prepare → ordinary resume/supersession → strict final fanout."""
import copy
import json
from pathlib import Path
import re
import tempfile
import unittest
from secret_bundle_ci_test import FanoutAPI, Backend, ENV, IMAGE, HEAD, ci, deploy, rollout, plan, encode
from secret_bundle_store import BundleStore, preview_updates

class PendingAPI(FanoutAPI):
    def transport(self,req):
        response=super().transport(req)
        if req['api']=='github':
            response['body']['data']['repository']['refs']['nodes'][0]['target']['oid']=getattr(self,'current_head',HEAD)
        return response
    def runner(self,argv,env=None):
        if argv[0]=='aws':return b''
        if argv[0]=='node':raise AssertionError('Existing credentials must not be provisioned')
        match=re.search(r'MODTALE_CONFIG_BRANCH_PREVIEW:([0-9]+)',','.join(argv))
        self.next_pin=match.group(1) if match else '2'
        return super().runner(argv,env=env)
    def stage(self,revision,pin):
        super().stage(revision,getattr(self,'next_pin',pin) if '-bd-' in revision else pin)

class PreviewRecoveryTests(unittest.TestCase):
    def setup_pending(self,*,latest='2',crash='after-stage'):
        api=PendingAPI();backend=Backend()
        values=json.loads(backend.versions['1'])['secrets']
        values.update(preview_updates('branch-preview','alpha',{'access-key':'synthetic-access','secret-key':'synthetic-secret',
                     'endpoint':'https://synthetic.r2.cloudflarestorage.com','token-id':'synthetic-token'}))
        backend.versions[latest]=encode({'schemaVersion':1,'boundary':'branch-preview','secrets':values})
        directory=tempfile.TemporaryDirectory();self.addCleanup(directory.cleanup)
        subject=ci.BundleCI(api.transport,{**ENV,'GITHUB_ENV':str(Path(directory.name)/'env')},runner=api.runner,
                           store_factory=lambda _:BundleStore('branch-preview',backend),sleeper=lambda _:None)
        api.crash=crash
        with self.assertRaises(RuntimeError):deploy.deploy(subject,['--image',IMAGE])
        return api,subject,backend

    def resolve_metadata(self,subject):
        data=dict(line.split('=',1) for line in Path(subject.env['GITHUB_ENV']).read_text().splitlines())
        subject.env.update(data)
        return data

    def test_same_run_prepare_defers_own_observed_stage_then_deploy_resumes_and_finalizes(self):
        api,subject,backend=self.setup_pending();snapshot=copy.deepcopy(api.service);api.requests.clear()
        subject.prepare();metadata=self.resolve_metadata(subject)
        self.assertEqual(metadata['MODTALE_SECRET_BUNDLE_DEPLOYMENT_PENDING'],'true')
        self.assertEqual(metadata['MODTALE_SECRET_BUNDLE_VERSION'],'2')
        self.assertEqual(api.service,snapshot);self.assertFalse(any(r['method']=='PATCH' for r in api.requests))
        self.assertEqual(backend.published,[])
        deploy.deploy(subject,['--image',IMAGE])
        deploy.deploy(subject,['--update-env-vars','FRONTEND_URL=https://synthetic.test,BACKEND_URL=https://api.synthetic.test'])
        subject.finalize_preview()
        self.assertEqual(deploy.DeploymentJournal(subject).load()[0]['phase'],'complete')
        self.assertEqual(api.service['template']['volumes'][0]['secret']['items'][0]['version'],'2')
        self.assertEqual(len(api.commands),2) # Initial stage plus ordinary CORS; resume adds none.

    def test_newer_published_pin_supersedes_pending_pin_then_requires_strict_final_pass(self):
        api,subject,backend=self.setup_pending(latest='3');old=deploy.DeploymentJournal(subject).load()[0]
        subject.env['GITHUB_RUN_ID']='9999';subject.env['GITHUB_SHA']='c'*40;api.current_head='c'*40
        subject.prepare();self.resolve_metadata(subject)
        self.assertEqual(subject.env['MODTALE_SECRET_BUNDLE_VERSION'],'3')
        with self.assertRaises(ci.CIError):subject.finalize_preview()
        deploy.deploy(subject,['--image',IMAGE]);subject.finalize_preview()
        new=deploy.DeploymentJournal(subject).load()[0]
        self.assertEqual(new['pin'],'3');self.assertEqual(new['retained_revisions'][0],api.revisions[old['revision']])
        self.assertIn(old['revision'],api.revisions)

    def test_other_preview_pending_journal_is_never_deferred(self):
        api,subject,_=self.setup_pending();subject.preview_id='beta';subject.env['BRANCH_SLUG']='beta'
        api.requests.clear()
        with self.assertRaises(ci.CIError):subject.fanout('2',allow_own_pending=True)
        self.assertFalse(any(r['method']=='PATCH' for r in api.requests))

    def test_unobserved_stage_intent_does_not_claim_safe_deferral(self):
        api,subject,_=self.setup_pending(crash='before-stage');api.requests.clear()
        with self.assertRaises(ci.CIError):subject.fanout('2',allow_own_pending=True)
        self.assertFalse(any(r['method']=='PATCH' for r in api.requests))

    def test_candidate_or_serving_drift_blocks_deferral_without_mutation(self):
        for mutation in ('image','pin','serving','uid','traffic'):
            with self.subTest(mutation=mutation):
                api,subject,_=self.setup_pending();record=deploy.DeploymentJournal(subject).load()[0]
                if mutation=='image':api.service['template']['containers'][0]['image']=IMAGE.replace('b'*64,'c'*64)
                if mutation=='pin':api.service['template']['volumes'][0]['secret']['items'][0]['version']='3'
                if mutation=='serving':api.revisions[record['serving_revision']]['uid']='other'
                if mutation=='uid':api.service['uid']='other'
                if mutation=='traffic':api.service['traffic'][0]['percent']=99
                api.requests.clear()
                with self.assertRaises((ci.CIError,rollout.RolloutError)):subject.fanout('2',allow_own_pending=True)
                self.assertFalse(any(r['method']=='PATCH' for r in api.requests))

    def test_lifecycle_change_rejects_own_deferral_and_final_latest_drift_is_fatal(self):
        api,subject,backend=self.setup_pending();subject.env['GITHUB_SHA']='c'*40
        with self.assertRaises(Exception):subject.fanout('2',allow_own_pending=True)
        subject.env['GITHUB_SHA']=HEAD
        deploy.deploy(subject,['--image',IMAGE])
        backend.versions['3']=backend.versions['2']
        with self.assertRaises(ci.CIError):subject.finalize_preview()

    def test_workflows_force_pending_backend_and_require_final_pass_after_cors(self):
        root=Path(__file__).parents[1]/'workflows'
        branch=(root/'ci-cd.yml').read_text();pr=(root/'pr-preview.yml').read_text()
        backend=branch[branch.index('id: deploy_backend'):branch.index('id: deploy_backend')+300]
        self.assertIn("env.MODTALE_SECRET_BUNDLE_DEPLOYMENT_PENDING == 'true'",backend)
        self.assertLess(branch.index('Update Backend CORS'),branch.index('Finish pinned preview fanout'))
        self.assertLess(pr.index('Update backend preview URLs'),pr.index('Finish pinned preview fanout'))
        self.assertLess(pr.index('Finish pinned preview fanout'),pr.index('Publish preview comment'))
        self.assertIn('secret_bundle_ci.py finalize-preview',branch)
        self.assertIn('secret_bundle_ci.py" finalize-preview',pr)

if __name__=='__main__':unittest.main()
