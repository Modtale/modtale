"""An expired old event allows only explicitly unverified ordinary baseline recovery."""
import copy
import unittest
from secret_bundle_ci_test import FanoutAPI, ENV, IMAGE, ci, deploy, rollout

class BaselineReconstructionTests(unittest.TestCase):
    def setup(self,*,old_status=200,head_status=200,new_active=True):
        api=FanoutAPI();old=api.service['latestCreatedRevision'];base=copy.deepcopy(api.service)
        original=api.transport;requests=[]
        def transport(req):
            requests.append(copy.deepcopy(req))
            if req['api']=='cloud-run-baseline-readiness':return {'status':head_status,'body':{}}
            if req['api']=='cloud-logging':
                if 'resource.labels.revision_name="'+old+'"' in req['body']['filter']:
                    return {'status':old_status,'body':{}}
                return {'status':200,'body':{'entries':[{'insertId':'fresh-candidate'}]} if new_active else {}}
            return original(req)
        subject=ci.BundleCI(transport,ENV,runner=api.runner,sleeper=lambda _:None)
        return api,subject,requests,base

    def test_expired_baseline_event_is_typed_unverified_and_new_activation_is_still_required(self):
        api,subject,requests,base=self.setup()
        deploy.deploy(subject,['--image',IMAGE])
        record,_=deploy.DeploymentJournal(subject).load()
        self.assertEqual(record['baseline_provenance'],'healthy_fallback_unverified')
        self.assertEqual(record['serving_revision'],base['latestCreatedRevision'])
        head=next(r for r in requests if r['api']=='cloud-run-baseline-readiness')
        self.assertEqual(head['origin'],base['uri']);self.assertEqual(head['revision'],base['latestCreatedRevision'])
        self.assertNotIn('tag',head);self.assertEqual(head['base_uri'],base['uri'])
        self.assertEqual(head['revision_uid'],record['serving_metadata']['uid'])
        self.assertTrue(any(r['api']=='cloud-logging' and record['revision'] in r['body']['filter'] for r in requests))
        self.assertEqual(record['phase'],'complete')
        from secret_bundle_transport import validate_request
        for request in requests:validate_request(request)

    def test_missing_new_candidate_event_never_promotes_despite_healthy_fallback(self):
        api,subject,requests,base=self.setup(new_active=False)
        def stop(_):raise RuntimeError('synthetic wait')
        subject.sleep=stop
        with self.assertRaises(RuntimeError):deploy.deploy(subject,['--image',IMAGE])
        self.assertFalse(any(r['api']=='cloud-run-v2' and r['method']=='PATCH' for r in requests))
        self.assertEqual(api.service['traffic'][0],base['traffic'][0])
        record,_=deploy.DeploymentJournal(subject).load()
        self.assertEqual(record['baseline_provenance'],'healthy_fallback_unverified')
        self.assertNotEqual(record['phase'],'complete')

    def test_baseline_identity_drift_after_head_never_stages(self):
        api,subject,requests,_=self.setup();original=subject.transport
        def drift(req):
            result=original(req)
            if req['api']=='cloud-run-baseline-readiness':api.service['etag']='changed-after-head'
            return result
        subject.transport=drift
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
        self.assertEqual(api.commands,[])

    def test_typed_fanout_refresh_cannot_change_the_existing_image(self):
        api=FanoutAPI();subject=ci.BundleCI(api.transport,{**ENV,'MODTALE_SECRET_BUNDLE_FANOUT_REFRESH':'true'},runner=api.runner,sleeper=lambda _:None)
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE.replace('b'*64,'c'*64)])
        self.assertEqual(api.commands,[]);self.assertEqual(api.annotations,{})
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE,'--update-env-vars','ANY=value'])
        self.assertEqual(api.commands,[])

    def test_log_access_denial_does_not_trigger_fallback_or_stage(self):
        api,subject,requests,_=self.setup(old_status=403)
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
        self.assertEqual(api.commands,[])
        self.assertFalse(any(r['api']=='cloud-run-baseline-readiness' for r in requests))

    def test_fallback_requires_one_exact_100_percent_revision(self):
        api,subject,requests,_=self.setup()
        old=api.service['latestCreatedRevision']
        api.service['traffic']=[{'type':rollout.REVISION_TYPE,'revision':old,'percent':50},
                                {'type':rollout.REVISION_TYPE,'revision':'modtale-backend-alpha-other','percent':50}]
        api.service['trafficStatuses']=copy.deepcopy(api.service['traffic'])
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
        self.assertEqual(api.commands,[])
        self.assertFalse(any(r['api']=='cloud-run-baseline-readiness' for r in requests))

    def test_baseline_http_denial_and_private_ingress_stop_before_staging(self):
        for status in (401,403,302,500):
            api,subject,_,_=self.setup(head_status=status)
            with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
            self.assertEqual(api.commands,[])
        api,subject,requests,_=self.setup();api.service['ingress']='INGRESS_TRAFFIC_INTERNAL_ONLY'
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
        self.assertFalse(any(r['api']=='cloud-run-baseline-readiness' for r in requests))

    def test_adopted_baseline_requires_fixed_runtime_and_preview_ownership(self):
        for mode in ('service-account','revision-account','owner'):
            api=FanoutAPI();original=api.transport
            def transport(req):
                result=original(req)
                if req['api']=='cloud-run-v1-ownership' and mode=='owner':result['body']['items'][0]['metadata']['uid']='other'
                if req['api']=='cloud-run-v2' and req['method']=='GET' and 'serviceAccount' in req['params']['fields']:
                    if '/revisions/' in req['path'] and mode=='revision-account':result['body']['serviceAccount']='other@example.test'
                    elif '/revisions/' not in req['path'] and mode=='service-account':result['body']['template']['serviceAccount']='other@example.test'
                return result
            subject=ci.BundleCI(transport,ENV,runner=api.runner,sleeper=lambda _:None)
            with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
            self.assertEqual(api.commands,[])

    def test_retained_completed_checkpoint_uses_its_bound_activation_provenance(self):
        api=FanoutAPI();subject=ci.BundleCI(api.transport,ENV,runner=api.runner,sleeper=lambda _:None)
        deploy.deploy(subject,['--image',IMAGE]);old=api.service['latestCreatedRevision'];original=api.transport;queries=[]
        def transport(req):
            if req['api']=='cloud-logging':
                queries.append(req)
                if old in req['body']['filter']:return {'status':403,'body':{}}
            return original(req)
        subject.transport=transport;subject.env['GITHUB_RUN_ATTEMPT']='2'
        deploy.deploy(subject,['--image',IMAGE])
        self.assertEqual(deploy.DeploymentJournal(subject).load()[0]['baseline_provenance'],'completed_checkpoint')
        self.assertFalse(any(old in r['body']['filter'] for r in queries))

if __name__=='__main__':unittest.main()
