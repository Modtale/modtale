"""Completed operational cache eviction never deletes runtime history or active recovery."""
import copy
import re
import unittest
from unittest.mock import patch
from secret_bundle_ci_test import FanoutAPI, ENV, IMAGE, HEAD, ci, deploy, rollout, plan
from secret_bundle_journal_capacity_test import Model

class FleetModel(Model,FanoutAPI):
    def runner(self,argv,env=None):
        match=re.search(r'MODTALE_CONFIG_BRANCH_PREVIEW:([0-9]+)',','.join(argv))
        self.next_pin=match.group(1) if match else '2'
        return super().runner(argv,env=env)
    def stage(self,revision,pin):
        super().stage(revision,getattr(self,'next_pin',pin) if '-bd-' in revision else pin)

class Fleet:
    def __init__(self,count=6):
        self.models=[FleetModel('branch-preview',letter*20) for letter in 'abcdef'[:count]]
        self.annotations={'manual':'m'*690};self.etag='journal1';self.requests=[];self.reject=False
        self.max_size=0;self.expired=set();self.log_status=200
    def transport(self,req):
        self.requests.append(copy.deepcopy(req))
        if req['api']=='secret-manager-metadata':
            if req['method']=='PATCH':
                if self.reject or req['body']['etag']!=self.etag:return {'status':412,'body':{}}
                self.annotations=copy.deepcopy(req['body']['annotations']);self.etag+='x'
                size=sum(len(k)+len(v) for k,v in self.annotations.items());self.max_size=max(size,self.max_size)
                if size>=16384:raise AssertionError('Secret annotation limit exceeded')
            body={'name':self.models[0].secret,'etag':self.etag}
            if req['method']=='GET':body['annotations']=copy.deepcopy(self.annotations)
            return {'status':200,'body':body}
        if req['api']=='github':
            return {'status':200,'body':{'data':{'repository':{'nameWithOwner':plan.REPOSITORY,
                'refs':{'nodes':[{'name':m.identifier,'target':{'oid':HEAD}} for m in self.models],
                        'pageInfo':{'hasNextPage':False,'endCursor':None}}}}}}
        if req['api']=='cloud-run-v1-ownership':
            selector=req['params']['labelSelector'];identifier=selector.rsplit('branch=',1)[-1]
            return {'status':200,'body':{'items':[{'metadata':{'name':m.short,'uid':m.service['uid']}} for m in self.models if m.identifier==identifier]}}
        if req['api']=='cloud-run-v2' and req['path'].endswith('/services'):
            return {'status':200,'body':{'services':[{'name':m.full} for m in self.models]}}
        if req['api']=='cloud-logging':
            old=any('resource.labels.revision_name="'+name+'"' in req['body']['filter'] for name in self.expired)
            return {'status':self.log_status,'body':{} if old else {'entries':[{'insertId':'synthetic-event'}]}}
        if req['api']=='cloud-run-baseline-readiness':return {'status':200,'body':{}}
        if req['api']=='cloud-run-readiness':
            if any(len(label)>63 for label in req['origin'].removeprefix('https://').split('.')):
                raise AssertionError('Unroutable synthetic DNS label')
            if len(req['tag'])!=10:raise AssertionError('New tag must be bounded')
        identity=req.get('service',req.get('path',''))
        model=next((m for m in self.models if m.full in identity),None)
        if model is None:raise AssertionError(req)
        return model.transport(req)
    def subject(self,model,**env):
        subject=model.ci(env);subject.transport=self.transport
        subject.inventory._transport=self.transport
        def runner(argv,env=None):
            target=next(m for m in self.models if m.short==argv[3])
            return target.runner(argv,env=env)
        subject.runner=runner
        return subject

class TerminalCacheTests(unittest.TestCase):
    def test_six_ordinary_consumers_evict_verified_completed_cache_and_preserve_revisions(self):
        fleet=Fleet()
        for model in fleet.models:
            deploy.deploy(fleet.subject(model),['--image',IMAGE])
            self.assertEqual(sum(k.startswith('modtale-deploy-') for k in fleet.annotations),1)
            self.assertEqual(len(model.revisions),2)
        self.assertLess(fleet.max_size,16384)
        original=fleet.models[0];first=set(original.revisions)
        # A new attempt after cache eviction cannot reuse the old deterministic revision.
        deploy.deploy(fleet.subject(original,GITHUB_RUN_ATTEMPT='2'),['--image',IMAGE])
        self.assertEqual(len(original.revisions),3);self.assertTrue(first.issubset(original.revisions))
        self.assertEqual(fleet.annotations['manual'],'m'*690)

    def test_mount_fanout_supports_six_existing_consumers_without_quota_regression(self):
        fleet=Fleet()
        # Initial numeric1 baselines, then serial repin across all consumers.
        subject=fleet.subject(fleet.models[0]);subject.fanout('2')
        self.assertEqual(sum(k.startswith('modtale-rollout-') for k in fleet.annotations),1)
        self.assertTrue(all(len(m.revisions)==2 for m in fleet.models))
        self.assertLess(fleet.max_size,16384)
        subject.env['GITHUB_RUN_ATTEMPT']='2';subject.fanout('3')
        self.assertTrue(all(len(m.revisions)==3 for m in fleet.models))
        self.assertEqual(fleet.annotations['manual'],'m'*690)

    def test_expired_evicted_baselines_refresh_ordinary_then_complete_fanout(self):
        fleet=Fleet(4)
        fleet.expired={m.service['latestCreatedRevision'].rsplit('/',1)[-1] for m in fleet.models}
        subject=fleet.subject(fleet.models[0]);subject.fanout('2')
        self.assertTrue(all(len(m.commands)==1 and len(m.revisions)==2 for m in fleet.models))
        self.assertTrue(all(m.service['template']['volumes'][0]['secret']['items'][0]['version']=='2' for m in fleet.models))
        self.assertEqual(sum(r['api']=='cloud-run-baseline-readiness' for r in fleet.requests),4)
        self.assertFalse(any(r['api']=='cloud-run-v2' and 'template' in r.get('body',{}) for r in fleet.requests))
        self.assertTrue(all('--image' in m.commands[0] and IMAGE in m.commands[0] for m in fleet.models))
        self.assertEqual(fleet.annotations['manual'],'m'*690)

    def test_interrupted_foreign_ordinary_refresh_is_resumed_before_new_fanout(self):
        fleet=Fleet(3);second=fleet.models[1]
        fleet.expired={m.service['latestCreatedRevision'].rsplit('/',1)[-1] for m in fleet.models}
        second.crash='after-stage';subject=fleet.subject(fleet.models[0])
        with self.assertRaises(RuntimeError):subject.fanout('2')
        pending=deploy.DeploymentJournal(subject,'branch-preview',second.identifier).load()[0]
        self.assertEqual(pending['intent_kind'],'fanout_refresh')
        self.assertNotEqual(pending['phase'],'complete')
        subject.env['GITHUB_RUN_ATTEMPT']='2';subject.fanout('2')
        self.assertTrue(all(m.service['template']['volumes'][0]['secret']['items'][0]['version']=='2' for m in fleet.models))
        self.assertEqual(len(second.commands),2)
        self.assertTrue(any(m['name'].rsplit('/',1)[-1]==pending['revision'] for m in second.revisions.values()))

    def test_log_denial_never_falls_back_to_ordinary_fanout_refresh(self):
        fleet=Fleet(2);fleet.log_status=403
        with self.assertRaises(ci.CIError):fleet.subject(fleet.models[0]).fanout('2')
        self.assertTrue(all(m.commands==[] for m in fleet.models))
        self.assertEqual(set(fleet.annotations),{'manual'})

    def test_active_foreign_journal_is_never_evicted_for_capacity(self):
        fleet=Fleet(2);first,second=fleet.models;first.crash='after-stage'
        with self.assertRaises(RuntimeError):deploy.deploy(fleet.subject(first),['--image',IMAGE])
        before=copy.deepcopy(fleet.annotations)
        with self.assertRaises(ci.CIError):deploy.deploy(fleet.subject(second),['--image',IMAGE])
        self.assertEqual(fleet.annotations,before);self.assertEqual(second.commands,[])

    def test_eviction_cas_rejection_preserves_completed_source_and_stops_before_cli(self):
        fleet=Fleet(2);first,second=fleet.models;deploy.deploy(fleet.subject(first),['--image',IMAGE])
        original=copy.deepcopy(fleet.annotations);fleet.reject=True
        with self.assertRaises(ci.CIError):deploy.deploy(fleet.subject(second),['--image',IMAGE])
        self.assertEqual(fleet.annotations,original);self.assertEqual(second.commands,[])

    def test_drifted_completed_foreign_state_is_not_evicted(self):
        fleet=Fleet(2);first,second=fleet.models;deploy.deploy(fleet.subject(first),['--image',IMAGE])
        original=copy.deepcopy(fleet.annotations);first.service['uid']='changed'
        with self.assertRaises(ci.CIError):deploy.deploy(fleet.subject(second),['--image',IMAGE])
        self.assertEqual(fleet.annotations,original);self.assertEqual(second.commands,[])

    def test_same_attempt_after_eviction_is_rejected_before_new_journal_or_cli(self):
        fleet=Fleet(2);first,second=fleet.models
        deploy.deploy(fleet.subject(first),['--image',IMAGE]);deploy.deploy(fleet.subject(second),['--image',IMAGE])
        original=copy.deepcopy(fleet.annotations);first.commands.clear()
        with self.assertRaises(ci.CIError):deploy.deploy(fleet.subject(first),['--image',IMAGE])
        self.assertEqual(fleet.annotations,original);self.assertEqual(first.commands,[])

    def test_rolled_back_foreign_witness_is_retained_even_when_another_intent_fits(self):
        fleet=Fleet(2);first,second=fleet.models;subject=fleet.subject(first)
        subject.fanout('2')
        # Only the final service retains its completed mount cache.
        source=rollout.SecretManagerJournal(fleet.transport,'branch-preview',second.identifier)
        executor=rollout.RolloutExecutor(fleet.transport,source,subject.inventory.github_lifecycle)
        executor.rollback(**ci.LOCK)
        for _ in range(10):
            if executor.advance(**ci.LOCK)['status']=='rolled_back':break
        else:self.fail('Rollback did not settle')
        original=fleet.annotations[source.key]
        deploy.deploy(fleet.subject(first),['--image',IMAGE])
        self.assertEqual(fleet.annotations[source.key],original)
        self.assertEqual(source.load()[0]['phase'],'rolled_back')

    def test_invalid_attempt_identity_fails_before_cloud_reads(self):
        fleet=Fleet(1)
        for value in ('0','-1','latest','1.1','1'*20):
            fleet.requests.clear()
            with self.assertRaises(ci.CIError):deploy.deploy(fleet.subject(fleet.models[0],GITHUB_RUN_ATTEMPT=value),['--image',IMAGE])
            self.assertEqual(fleet.requests,[])

    def test_evicted_source_and_durable_new_intent_survive_stage_crash(self):
        fleet=Fleet(2);first,second=fleet.models;deploy.deploy(fleet.subject(first),['--image',IMAGE])
        firstkey=deploy.DeploymentJournal(fleet.subject(first)).key;second.crash='before-stage'
        with self.assertRaises(RuntimeError):deploy.deploy(fleet.subject(second),['--image',IMAGE])
        self.assertNotIn(firstkey,fleet.annotations)
        self.assertEqual(deploy.DeploymentJournal(fleet.subject(second)).load()[0]['phase'],'stage_intent')
        deploy.deploy(fleet.subject(second),['--image',IMAGE])
        self.assertEqual(len(first.revisions),2);self.assertEqual(len(second.revisions),2)

if __name__=='__main__':unittest.main()
