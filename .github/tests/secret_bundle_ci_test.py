"""All CI adapter scenarios use synthetic values and fake processes/transports."""
import copy
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parents[1] / 'scripts'))
import secret_bundle_ci as ci
import secret_bundle_ci_deploy as deploy
import secret_bundle_preview_plan as plan
import secret_bundle_rollout as rollout
from secret_bundle import encode
from secret_bundle_store import BundleStore

HEAD = 'a' * 40
IMAGE = 'gcr.io/gen-lang-client-0244308719/modtale-backend@sha256:' + 'b' * 64
PROBE = {'httpGet':{'path':'/actuator/health/readiness','port':8080},'initialDelaySeconds':0,
         'periodSeconds':10,'timeoutSeconds':5,'failureThreshold':24}
ENV = {'MODTALE_SECRET_BUNDLES_ENABLED': 'true', 'MODTALE_SECRET_BUNDLE_LOCK_GROUP': plan.LOCK_GROUP,
       'PROJECT_ID': 'gen-lang-client-0244308719', 'REGION': 'us-central1', 'ENV_TYPE': 'preview',
       'GITHUB_SHA': HEAD, 'GIT_BRANCH_NAME': 'alpha', 'BRANCH_SLUG': 'alpha',
       'R2_BUCKET_NAME': 'modtale-branch-alpha', 'SEEDING_SOURCE_R2_BUCKET_NAME': 'modtale-preview-template',
       'GITHUB_RUN_ID': '1234', 'BACKEND_SERVICE': 'modtale-backend-alpha',
       'MODTALE_SECRET_BUNDLE_VERSION': '2'}


class Backend:
    def __init__(self):
        values = {'BRANCH_PREVIEW_MONGODB_URI': 'synthetic-mongo',
                  'R2_TEMPLATE_READ_ACCESS_KEY': 'synthetic-source-access',
                  'R2_TEMPLATE_READ_SECRET_KEY': 'synthetic-source-secret',
                  'R2_TEMPLATE_READ_ENDPOINT': 'https://synthetic.r2.cloudflarestorage.com',
                  'branch-preview-other-r2-token-id': 'other-token'}
        self.versions = {'1': encode({'schemaVersion': 1, 'boundary': 'branch-preview', 'secrets': values})}
        self.published = []
    def latest_enabled(self): return max(self.versions, key=int)
    def read(self, version): return self.versions[version]
    def publish(self, raw):
        version = str(int(self.latest_enabled()) + 1)
        self.versions[version] = raw
        self.published.append(raw)
        return version


def lifecycle():
    return {'repository': plan.REPOSITORY, 'complete': True, 'items': [{'name': 'alpha', 'head_sha': HEAD}]}


class PrepareTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.backend = Backend()
        self.calls = []
        env = {**ENV, 'MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED': ENV['BACKEND_SERVICE'], 'GITHUB_ENV': str(Path(self.directory.name) / 'env')}
        def runner(argv, env=None):
            self.calls.append((argv, env))
            if argv[:1] == ['node']:
                path = Path(env['R2_RUNTIME_ENV_FILE'])
                path.write_text("R2_RUNTIME_ACCESS_KEY='synthetic-access'\nR2_RUNTIME_SECRET_KEY='synthetic-secret'\nR2_RUNTIME_ENDPOINT='https://synthetic.r2.cloudflarestorage.com'\nR2_RUNTIME_TOKEN_ID='synthetic-token'\n")
            return b''
        self.subject = ci.BundleCI(lambda req: ci.fail(), env, runner=runner,
                                   store_factory=lambda boundary: BundleStore(boundary, self.backend))
        self.subject.inventory.github_lifecycle = lambda boundary: lifecycle()
        self.subject.fanout = lambda version: self.calls.append(('fanout', version))

    def test_complete_four_key_publication_and_metadata_only_output(self):
        self.subject.prepare()
        self.assertEqual(len(self.backend.published), 1)
        values = json.loads(self.backend.published[0])['secrets']
        self.assertEqual(set(key for key in values if key.startswith('branch-preview-alpha-')), set(plan.credential_keys('branch-preview', 'alpha')))
        self.assertEqual(values['branch-preview-other-r2-token-id'], 'other-token')
        output = Path(self.subject.env['GITHUB_ENV']).read_text()
        self.assertEqual(output, 'MODTALE_SECRET_BUNDLE_VERSION=2\nR2_RUNTIME_CREDENTIALS_REPLACED=true\n')
        self.assertNotIn('synthetic', output)
        provision_env = next(env for argv, env in self.calls if isinstance(argv, list) and argv[0] == 'node')
        self.assertEqual(provision_env['GITHUB_ENV'], '')
        self.assertEqual(provision_env['GITHUB_ACTIONS'], 'false')
        self.assertIn(('fanout', '2'), self.calls)

    def test_retry_reuses_bundle_and_still_recovers_fanout(self):
        self.subject.prepare()
        self.calls.clear()
        self.subject.prepare()
        self.assertEqual(len(self.backend.published), 1)
        self.assertFalse(any(isinstance(item[0], list) and item[0][0] == 'node' for item in self.calls))
        self.assertIn(('fanout', '2'), self.calls)

    def test_lifecycle_drift_prevents_publication(self):
        calls = [0]
        def snapshots(boundary):
            calls[0] += 1
            return lifecycle() if calls[0] < 4 else {'repository': plan.REPOSITORY, 'complete': True, 'items': []}
        self.subject.inventory.github_lifecycle = snapshots
        with self.assertRaises(Exception): self.subject.prepare()
        self.assertFalse(self.backend.published)

    def test_missing_provisioning_approval_never_mints_new_credentials(self):
        self.subject.env.pop('MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED')
        with self.assertRaises(ci.CIError): self.subject.prepare()
        self.assertFalse(self.backend.published)
        self.assertFalse(any(argv[0] == 'node' for argv, env in self.calls))

    def test_partial_credential_set_stops_before_provision(self):
        data = json.loads(self.backend.versions['1'])
        data['secrets']['branch-preview-alpha-r2-access-key'] = 'synthetic'
        self.backend.versions['1'] = encode(data)
        with self.assertRaises(ci.CIError): self.subject.prepare()
        self.assertFalse(self.backend.published)
        self.assertFalse(any(argv[0] == 'node' for argv, env in self.calls))

    def test_wrong_project_or_missing_opt_in_is_closed(self):
        for env in ({**ENV, 'PROJECT_ID': 'modtale-pr-preview'}, {**ENV, 'MODTALE_SECRET_BUNDLES_ENABLED': 'false'},
                    {**ENV, 'MODTALE_SECRET_BUNDLE_LOCK_GROUP': 'per-branch'}):
            with self.assertRaises(ci.CIError): ci.BundleCI(lambda req: None, env)

    def test_shared_pin_must_be_numeric_and_never_auto_latest(self):
        env = {**ENV, 'ENV_TYPE': 'prod', 'GIT_BRANCH_NAME': 'main', 'MODTALE_SECRET_BUNDLE_SHARED_VERSION': 'latest'}
        subject = ci.BundleCI(lambda req: None, env, store_factory=lambda boundary: BundleStore('branch-preview', self.backend))
        subject.lifecycle = lambda: None
        with self.assertRaises(ValueError): subject.prepare()
        self.assertFalse(self.backend.published)


class DeploymentAPI:
    def __init__(self, exists=True):
        self.full = 'projects/gen-lang-client-0244308719/locations/us-central1/services/modtale-backend-alpha'
        self.secret = 'projects/gen-lang-client-0244308719/secrets/MODTALE_CONFIG_BRANCH_PREVIEW'
        self.annotations, self.etag = {}, 'j1'
        self.requests, self.commands = [], []
        self.revisions = {}
        self.activated = True
        self.crash = None
        self.service = None
        if exists:
            self.stage('modtale-backend-alpha-original', '1')
            self.service['traffic'] = [{'type': rollout.REVISION_TYPE, 'revision': self.service['latestCreatedRevision'], 'percent': 100}]
            self.service['trafficStatuses'] = copy.deepcopy(self.service['traffic'])

    def stage(self, revision, pin):
        old = self.service
        template = {'revision': revision, 'containers': [{'name': 'app', 'image': IMAGE, 'startupProbe': copy.deepcopy(PROBE), 'volumeMounts': [{'name': 'bundle', 'mountPath': '/app/secrets/bundles'}]}],
                    'volumes': [{'name': 'bundle', 'secret': {'secret': 'MODTALE_CONFIG_BRANCH_PREVIEW', 'items': [{'path': 'branch-preview.json', 'version': pin}]}}]}
        generation = str(int(old['generation']) + 1) if old else '1'
        self.service = {'name': self.full, 'uid': 'service-uid', 'etag': 'e' + generation, 'generation': generation,
            'observedGeneration': generation, 'terminalCondition': {'state': 'CONDITION_SUCCEEDED'},
            'latestCreatedRevision': revision, 'latestReadyRevision': revision, 'template': template,
            'traffic': copy.deepcopy(old['traffic']) if old else [{'type': rollout.REVISION_TYPE, 'revision': revision, 'percent': 100}],
            'uri': 'https://synthetic.run.app', 'ingress': old['ingress'] if old else 'INGRESS_TRAFFIC_INTERNAL_ONLY'}
        self.service['trafficStatuses'] = copy.deepcopy(self.service['traffic'])
        self.revisions[revision] = {'name': revision, 'uid': revision+'-uid', 'service': self.full,
            'createTime': '2026-10-02T14:00:00Z', **{k: copy.deepcopy(template[k]) for k in ('containers', 'volumes')}}

    def transport(self, req):
        self.requests.append(copy.deepcopy(req))
        if req['api'] == 'secret-manager-metadata':
            if req['method'] == 'PATCH':
                if req['body']['etag'] != self.etag: return {'status': 412, 'body': {}}
                self.annotations = copy.deepcopy(req['body']['annotations']); self.etag += 'x'
            body = {'name': self.secret, 'etag': self.etag}
            if req['method'] == 'GET': body['annotations'] = copy.deepcopy(self.annotations)
            return {'status': 200, 'body': body}
        if req['api'] == 'cloud-logging':
            return {'status': 200, 'body': {'entries': [{'insertId': 'synthetic-event'}]} if self.activated else {}}
        if req['method'] == 'GET':
            body = self.revisions.get(req['path'].rsplit('/',1)[-1]) if '/revisions/' in req['path'] else self.service
            return {'status': 200, 'body': copy.deepcopy(body)} if body else {'status': 404, 'body': {}}
        if req['method'] == 'PATCH':
            if req['body']['etag'] != self.service['etag']: return {'status': 412, 'body': {}}
            if self.crash == 'before-promote': self.crash = None; raise RuntimeError('synthetic-private')
            self.service['traffic'] = copy.deepcopy(req['body']['traffic'])
            self.service['trafficStatuses'] = copy.deepcopy(self.service['traffic'])
            if 'ingress' in req['body']: self.service['ingress'] = req['body']['ingress']
            self.service['generation'] = self.service['observedGeneration'] = str(int(self.service['generation']) + 1)
            self.service['etag'] += 'p'
            if self.crash == 'after-promote': self.crash = None; raise RuntimeError('synthetic-private')
            return {'status': 200, 'body': {'name': 'projects/gen-lang-client-0244308719/locations/us-central1/operations/synthetic', 'done': True}}
        raise AssertionError(req)

    def runner(self, argv, env=None):
        self.commands.append(argv)
        suffix = argv[argv.index('--revision-suffix') + 1]
        if self.crash == 'before-stage': self.crash = None; raise RuntimeError('synthetic-private')
        self.stage('modtale-backend-alpha-' + suffix, '2')
        if self.crash == 'after-stage': self.crash = None; raise RuntimeError('synthetic-private')
        return b''

    def ci(self, env=None):
        obj = ci.BundleCI(self.transport, {**ENV, **(env or {})}, runner=self.runner, sleeper=lambda seconds: None)
        obj.lifecycle = lambda **kwargs: None
        return obj


class DeploymentTests(unittest.TestCase):
    def test_existing_deploy_stages_then_promotes_exact_revision(self):
        api = DeploymentAPI()
        self.assertEqual(deploy.deploy(api.ci(), ['--image', IMAGE]), 'https://synthetic.run.app')
        self.assertEqual(len(api.commands), 1)
        self.assertIn('--no-traffic', api.commands[0])
        self.assertIn(deploy.READINESS_STARTUP_PROBE,api.commands[0])
        self.assertNotIn('--ingress', api.commands[0])
        promoted = [r for r in api.requests if r['api'] == 'cloud-run-v2' and r['method'] == 'PATCH']
        self.assertEqual(len(promoted), 1)
        self.assertEqual(promoted[0]['params']['updateMask'], 'traffic')
        self.assertNotIn('latest', str(promoted[0]['body']))
        self.assertEqual(deploy.DeploymentJournal(api.ci()).load()[0]['phase'], 'complete')

    def test_crashes_resume_without_duplicate_revision_or_promotion(self):
        for crash in ('before-stage', 'after-stage', 'before-promote', 'after-promote'):
            with self.subTest(crash=crash):
                api = DeploymentAPI(); api.crash = crash
                with self.assertRaises(RuntimeError): deploy.deploy(api.ci(), ['--image', IMAGE])
                self.assertEqual(deploy.deploy(api.ci(), ['--image', IMAGE]), 'https://synthetic.run.app')
                self.assertEqual(len(api.revisions), 2)
                self.assertEqual(api.service['generation'], '3')

    def test_pending_different_head_or_arguments_cannot_overwrite_checkpoint(self):
        api = DeploymentAPI(); api.crash = 'before-stage'
        with self.assertRaises(RuntimeError): deploy.deploy(api.ci(), ['--image', IMAGE])
        with self.assertRaises(ci.CIError): deploy.deploy(api.ci(), ['--image', IMAGE.replace('b', 'c')])
        with self.assertRaises(ci.CIError): deploy.assert_no_pending_deployment(api.ci(), 'branch-preview', 'alpha')

    def test_new_push_can_supersede_a_settled_no_traffic_candidate(self):
        api = DeploymentAPI(); api.crash = 'after-stage'
        original = api.service['latestCreatedRevision']
        with self.assertRaises(RuntimeError): deploy.deploy(api.ci(), ['--image', IMAGE])
        self.assertEqual(api.service['traffic'][0]['revision'], original)
        newer = api.ci({'GITHUB_SHA':'c'*40,'GITHUB_RUN_ID':'1235'})
        self.assertEqual(deploy.deploy(newer, ['--image', IMAGE]), 'https://synthetic.run.app')
        self.assertEqual(len(api.revisions), 3)
        self.assertEqual(api.service['generation'], '4')
        record, _ = deploy.DeploymentJournal(newer).load()
        self.assertEqual(record['phase'], 'complete')
        self.assertEqual(len(record['retained_revisions']), 1)
        self.assertNotEqual(api.service['traffic'][0]['revision'], original)

    def test_wrong_resolved_image_is_never_promoted(self):
        api=DeploymentAPI();original=api.runner
        def wrong_image(argv,env=None):
            result=original(argv,env)
            unexpected=IMAGE.replace('b'*64,'c'*64)
            api.service['template']['containers'][0]['image']=unexpected
            api.revisions[api.service['latestCreatedRevision']]['containers'][0]['image']=unexpected
            return result
        subject=api.ci();subject.runner=wrong_image
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
        self.assertFalse(any(req['api']=='cloud-run-v2' and req['method']=='PATCH' for req in api.requests))
        self.assertEqual(api.service['traffic'][0]['revision'],'modtale-backend-alpha-original')

    def test_terminal_replay_rechecks_uid_generation_traffic_and_candidate(self):
        for field in ('uid','generation','traffic','candidate'):
            with self.subTest(field=field):
                api=DeploymentAPI();deploy.deploy(api.ci(),['--image',IMAGE])
                if field=='uid':api.service['uid']='replacement-uid'
                elif field=='generation':api.service['generation']='4'
                elif field=='traffic':
                    api.service['traffic'][0]['revision']='modtale-backend-alpha-original'
                    api.service['trafficStatuses']=copy.deepcopy(api.service['traffic'])
                else:api.revisions[api.service['latestCreatedRevision']]['uid']='replacement-candidate-uid'
                count=len(api.commands)
                with self.assertRaises(ci.CIError):deploy.deploy(api.ci(),['--image',IMAGE])
                self.assertEqual(len(api.commands),count)

    def test_landed_old_head_promotion_is_observed_before_new_deployment(self):
        api=DeploymentAPI();api.crash='after-promote'
        with self.assertRaises(RuntimeError):deploy.deploy(api.ci(),['--image',IMAGE])
        old=api.service['latestCreatedRevision']
        subject=api.ci({'GITHUB_SHA':'d'*40,'GITHUB_RUN_ID':'5678'})
        self.assertEqual(deploy.deploy(subject,['--image',IMAGE]),'https://synthetic.run.app')
        self.assertEqual(api.service['generation'],'5')
        self.assertEqual(len(api.commands),2)
        self.assertNotEqual(api.service['latestCreatedRevision'],old)
        self.assertEqual(sum(req['api']=='cloud-run-v2' and req['method']=='PATCH' for req in api.requests),2)

    def test_stale_head_can_checkpoint_landed_promotion_without_any_runtime_write(self):
        api=DeploymentAPI();api.crash='after-promote'
        with self.assertRaises(RuntimeError):deploy.deploy(api.ci(),['--image',IMAGE])
        count=len(api.commands);patches=sum(req['api']=='cloud-run-v2' and req['method']=='PATCH' for req in api.requests)
        subject=api.ci()
        subject.lifecycle=lambda **kwargs: (_ for _ in ()).throw(ci.CIError('stale-head'))
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
        self.assertEqual(deploy.DeploymentJournal(subject).load()[0]['phase'],'complete')
        self.assertEqual(len(api.commands),count)
        self.assertEqual(sum(req['api']=='cloud-run-v2' and req['method']=='PATCH' for req in api.requests),patches)

    def test_failed_promotion_lro_is_read_and_can_retry_without_restaging(self):
        from secret_bundle_transport import validate_request
        api=DeploymentAPI();original=api.transport;failed=[False];operation='projects/gen-lang-client-0244308719/locations/us-central1/operations/failed-promotion'
        def transport(req):
            if req['api']=='cloud-run-v2' and '/operations/' in req['path']:
                api.requests.append(copy.deepcopy(req))
                return {'status':200,'body':{'name':operation,'done':True,'error':{'code':13}}}
            if req['api']=='cloud-run-v2' and req['method']=='PATCH' and not failed[0]:
                failed[0]=True;api.requests.append(copy.deepcopy(req))
                return {'status':200,'body':{'name':operation,'done':False}}
            return original(req)
        subject=api.ci();subject.transport=transport
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
        self.assertEqual(deploy.DeploymentJournal(subject).load()[0]['phase'],'stage_wait')
        self.assertEqual(api.service['traffic'][0]['revision'],'modtale-backend-alpha-original')
        self.assertEqual(deploy.deploy(subject,['--image',IMAGE]),'https://synthetic.run.app')
        self.assertEqual(len(api.commands),1)
        self.assertTrue(any('/operations/' in req['path'] for req in api.requests))
        for req in api.requests:validate_request(req)

    def test_reservation_survives_crash_and_full_remaining_annotation_space(self):
        api=DeploymentAPI();api.crash='after-stage'
        manual='modtale-initial-dev-v1';api.annotations[manual]='m'*(4429-len(manual))
        original=api.stage;opaque='"'+'e'*131+'"'
        def stage(revision,pin):
            original(revision,pin);api.service['etag']=opaque
        api.stage=stage
        with self.assertRaises(RuntimeError):deploy.deploy(api.ci(),['--image',IMAGE])
        record,_=deploy.DeploymentJournal(api.ci()).load()
        key=deploy.DeploymentJournal(api.ci()).key
        self.assertEqual(len(api.annotations[key]),record['reservation_bytes'])
        self.assertIn('.',api.annotations[key])
        self.assertLess(sum(len(k)+len(v) for k,v in api.annotations.items()),16384)
        filler='other-metadata';remaining=16383-sum(len(k)+len(v) for k,v in api.annotations.items())-len(filler)
        self.assertGreaterEqual(remaining,0);api.annotations[filler]='x'*remaining
        self.assertEqual(deploy.deploy(api.ci(),['--image',IMAGE]),'https://synthetic.run.app')
        completed,_=deploy.DeploymentJournal(api.ci()).load()
        self.assertEqual(completed['staged']['etag'],opaque)
        self.assertNotIn('.',api.annotations[key])
        self.assertEqual(api.annotations[manual],'m'*(4429-len(manual)))
        self.assertEqual(api.annotations[filler],'x'*remaining)
        self.assertEqual(len(api.commands),1)

    def test_insufficient_recovery_capacity_stops_before_cloud_run_mutation(self):
        api=DeploymentAPI();api.annotations['other']='x'*8000
        with self.assertRaises(ci.CIError):deploy.deploy(api.ci(),['--image',IMAGE])
        self.assertFalse(api.commands)
        self.assertFalse(any(req['api']=='cloud-run-v2' and req['method']=='PATCH' for req in api.requests))
        self.assertEqual(api.annotations,{'other':'x'*8000})

    def test_many_sequential_deployments_keep_only_immediate_rollback_metadata(self):
        api=DeploymentAPI();previous=api.service['latestCreatedRevision']
        sizes=[]
        for number in range(20):
            subject=api.ci({'GITHUB_RUN_ID':str(8000+number)})
            deploy.deploy(subject,['--image',IMAGE])
            record,_=deploy.DeploymentJournal(subject).load()
            self.assertEqual(record['serving_revision'],previous)
            self.assertEqual(record['retained_revisions'],[])
            self.assertEqual(record['expected_image'],IMAGE)
            sizes.append(record['reservation_bytes'])
            previous=api.service['latestCreatedRevision']
        self.assertEqual(len(api.revisions),21)
        self.assertLess(max(sizes)-min(sizes),1000)

    def test_unpromoted_latest_revision_is_rejected_before_journal_or_stage(self):
        api=DeploymentAPI();api.stage('modtale-backend-alpha-canary','2')
        with self.assertRaises(ci.CIError):deploy.deploy(api.ci(),['--image',IMAGE])
        self.assertFalse(api.commands)
        self.assertEqual(api.annotations,{})

    def test_pending_old_head_lro_is_resolved_without_stale_runtime_write(self):
        from secret_bundle_transport import validate_request
        for outcome in ('success','failure'):
            with self.subTest(outcome=outcome):
                api=DeploymentAPI();original=api.transport;saved=[];reads=[0]
                operation='projects/gen-lang-client-0244308719/locations/us-central1/operations/prior-promotion'
                def transport(req):
                    if req['api']=='cloud-run-v2' and req['method']=='PATCH' and not saved:
                        saved.append(copy.deepcopy(req));api.requests.append(copy.deepcopy(req))
                        return {'status':200,'body':{'name':operation,'done':False}}
                    if req['api']=='cloud-run-v2' and '/operations/' in req['path']:
                        api.requests.append(copy.deepcopy(req));reads[0]+=1
                        if outcome=='failure':return {'status':200,'body':{'name':operation,'done':True,'error':{'code':13}}}
                        if reads[0]==1:return {'status':200,'body':{'name':operation,'done':False}}
                        api.service['traffic']=copy.deepcopy(saved[0]['body']['traffic'])
                        api.service['trafficStatuses']=copy.deepcopy(api.service['traffic'])
                        api.service['generation']=api.service['observedGeneration']='3';api.service['etag']='e3'
                        return {'status':200,'body':{'name':operation,'done':True}}
                    return original(req)
                first=api.ci();first.transport=transport
                first.sleep=lambda seconds: (_ for _ in ()).throw(RuntimeError('synthetic-runner-crash'))
                with self.assertRaises(RuntimeError):deploy.deploy(first,['--image',IMAGE])
                self.assertEqual(deploy.DeploymentJournal(first).load()[0]['phase'],'promote_wait')
                newer=api.ci({'GITHUB_SHA':'d'*40,'GITHUB_RUN_ID':'9999'});newer.transport=transport
                self.assertEqual(deploy.deploy(newer,['--image',IMAGE]),'https://synthetic.run.app')
                self.assertGreater(reads[0],0)
                self.assertEqual(len(api.commands),2)
                self.assertEqual(sum(req['api']=='cloud-run-v2' and req['method']=='PATCH' for req in api.requests),2)
                self.assertEqual(api.service['generation'],'5' if outcome=='success' else '4')
                for req in api.requests:validate_request(req)

    def test_no_image_update_binds_to_baseline_immutable_image(self):
        api=DeploymentAPI();subject=api.ci()
        deploy.deploy(subject,['--update-env-vars','FRONTEND_URL=https://example.test'])
        self.assertEqual(deploy.DeploymentJournal(subject).load()[0]['expected_image'],IMAGE)

    def test_maximum_opaque_etag_round_trips_without_reservation_growth(self):
        api=DeploymentAPI();stage=api.stage;opaque='"'*1024
        def large_etag(revision,pin):stage(revision,pin);api.service['etag']=opaque
        api.stage=large_etag
        # Keep the synthetic promotion's next etag within the same supported bound.
        transport=api.transport
        def bounded_transport(req):
            response=transport(req)
            if req['api']=='cloud-run-v2' and req['method']=='PATCH':api.service['etag']='p'*1024
            return response
        subject=api.ci();subject.transport=bounded_transport
        self.assertEqual(deploy.deploy(subject,['--image',IMAGE]),'https://synthetic.run.app')
        self.assertEqual(deploy.DeploymentJournal(subject).load()[0]['staged']['etag'],opaque)

    def test_new_service_requires_specific_network_approval(self):
        api = DeploymentAPI(exists=False)
        with self.assertRaises(ci.CIError): deploy.deploy(api.ci(), ['--image', IMAGE])
        self.assertFalse(api.commands)
        self.assertEqual(deploy.deploy(api.ci({'MODTALE_SECRET_BUNDLE_NEW_SERVICE_APPROVED': ENV['BACKEND_SERVICE']}), ['--image', IMAGE]), 'https://synthetic.run.app')
        self.assertIn('--ingress', api.commands[0])
        self.assertIn('internal', api.commands[0])
        self.assertEqual(api.service['ingress'], 'INGRESS_TRAFFIC_ALL')

    def test_mode_guard_prevents_legacy_reattachment_after_activation(self):
        api = FanoutAPI()
        with self.assertRaises(ci.CIError):
            ci.validate_mode(api.transport, {**ENV, 'MODTALE_SECRET_BUNDLES_ENABLED': 'false'})
        ci.validate_mode(api.transport, ENV)
        self.assertFalse(api.commands)
        api.service['template']['volumes'] = []
        ci.validate_mode(api.transport, {**ENV, 'MODTALE_SECRET_BUNDLES_ENABLED': 'false'})
        with self.assertRaises(ci.CIError): ci.validate_mode(api.transport, ENV)

    def test_legacy_or_dynamic_secret_args_rejected(self):
        for args in (['--update-secrets', 'MONGODB_URI=MONGODB_URI:latest'], ['--set-env-vars', 'PRIVATE=synthetic'],
                     ['--update-env-vars', 'WARDEN_URL=synthetic'], ['--ingress', 'all'], ['--no-traffic']):
            with self.assertRaises(ci.CIError): deploy.deployment_args('branch-preview', 'branch-preview', 'alpha', '2', args)

    def test_every_bundle_profile_gets_http_application_readiness_startup(self):
        for profile,boundary,identifier in [('prod','shared',None),('dev','shared',None),
                                           ('branch-preview','branch-preview','alpha'),('pr-preview','pr-preview','17')]:
            args=deploy.deployment_args(profile,boundary,identifier,'2',[])
            self.assertEqual(args.count('--startup-probe'),1)
            self.assertEqual(args[args.index('--startup-probe')+1],deploy.READINESS_STARTUP_PROBE)
            fields=dict(part.split('=',1) for part in deploy.READINESS_STARTUP_PROBE.split(','))
            self.assertEqual(fields['httpGet.path'],'/actuator/health/readiness')
            self.assertEqual(fields['httpGet.port'],'8080')
            self.assertEqual(int(fields['periodSeconds'])*int(fields['failureThreshold']),240)
            self.assertLess(int(fields['timeoutSeconds']),int(fields['periodSeconds']))

    def test_ordinary_deploy_can_repair_a_tcp_baseline(self):
        api=DeploymentAPI()
        old=api.service['latestCreatedRevision']
        tcp={'tcpSocket':{'port':8080},'periodSeconds':240,'timeoutSeconds':240,'failureThreshold':1}
        api.service['template']['containers'][0]['startupProbe']=copy.deepcopy(tcp)
        api.revisions[old]['containers'][0]['startupProbe']=copy.deepcopy(tcp)
        self.assertEqual(deploy.deploy(api.ci(),['--image',IMAGE]),'https://synthetic.run.app')
        self.assertEqual(api.service['template']['containers'][0]['startupProbe'],PROBE)

    def test_tcp_candidate_is_never_promoted_even_if_platform_and_logs_are_ready(self):
        api=DeploymentAPI();runner=api.runner
        def wrong_probe(argv,env=None):
            result=runner(argv,env)
            tcp={'tcpSocket':{'port':8080},'periodSeconds':240,'timeoutSeconds':240,'failureThreshold':1}
            api.service['template']['containers'][0]['startupProbe']=copy.deepcopy(tcp)
            api.revisions[api.service['latestCreatedRevision']]['containers'][0]['startupProbe']=copy.deepcopy(tcp)
            return result
        subject=api.ci();subject.runner=wrong_probe
        with self.assertRaises(Exception):deploy.deploy(subject,['--image',IMAGE])
        self.assertFalse(any(req['api']=='cloud-run-v2' and req['method']=='PATCH' for req in api.requests))
        self.assertEqual(api.service['traffic'][0]['revision'],'modtale-backend-alpha-original')

    def test_existing_preview_http_probe_is_normalized_without_duplicate_flags(self):
        existing='httpGet.path=/actuator/health/readiness,httpGet.port=8080,periodSeconds=10,timeoutSeconds=10,failureThreshold=60'
        args=deploy.deployment_args('branch-preview','branch-preview','alpha','2',['--startup-probe',existing])
        self.assertEqual(args.count('--startup-probe'),1)
        self.assertNotIn(existing,args)
        self.assertIn(deploy.READINESS_STARTUP_PROBE,args)

    def test_tcp_missing_bad_path_headers_and_over_budget_startup_are_rejected(self):
        for raw in ('tcpSocket.port=8080','',
                    'httpGet.path=/actuator/health,httpGet.port=8080',
                    'httpGet.path=/actuator/health/readiness,httpGet.port=8080,httpGet.httpHeaders.name=Authorization',
                    'httpGet.path=/actuator/health/readiness,httpGet.port=8080,periodSeconds=240,failureThreshold=3',
                    'httpGet.path=/actuator/health/readiness,httpGet.port=8080,periodSeconds=10,timeoutSeconds=11'):
            with self.assertRaises(ci.CIError):deploy.readiness_startup_args(['--startup-probe',raw])
        with self.assertRaises(ci.CIError):deploy.readiness_startup_args(['--startup-probe'])
        with self.assertRaises(ci.CIError):deploy.readiness_startup_args(['--startup-probe',deploy.READINESS_STARTUP_PROBE,'--startup-probe',deploy.READINESS_STARTUP_PROBE])

    def test_shared_original_keys_and_numeric_pin(self):
        for profile in ('prod','dev'):
            args = deploy.deployment_args(profile, 'shared', None, '22', [])
            self.assertIn('/app/secrets/bundles/shared.json=MODTALE_CONFIG_SHARED:22', args)
            self.assertIn('WARDEN_API_KEY=WARDEN_API_KEY:latest', args)
            self.assertEqual('HYTALE_CLIENT_SECRET=HYTALE_CLIENT_SECRET:latest' in args, profile == 'prod')

    def test_no_activation_means_no_promotion(self):
        api = DeploymentAPI(); api.crash = 'after-stage'
        with self.assertRaises(RuntimeError): deploy.deploy(api.ci(), ['--image', IMAGE])
        api.activated = False
        with patch('secret_bundle_ci_deploy.time.monotonic', side_effect=[0, 1, 1000]):
            with self.assertRaises(ci.CIError): deploy.deploy(api.ci(), ['--image', IMAGE])
        self.assertFalse(any(r['api'] == 'cloud-run-v2' and r['method'] == 'PATCH' for r in api.requests))


class WorkflowContractTests(unittest.TestCase):
    def test_all_mutators_share_non_canceling_max_queue_and_retain_originals(self):
        root = Path(__file__).parents[1]
        for name in ('ci-cd.yml', 'pr-preview.yml', 'mock-db-refresh.yml'):
            text = (root/'workflows'/name).read_text()
            self.assertNotIn('cancel-in-progress: true', text)
            self.assertEqual(text.count('concurrency:'), text.count('group: modtale-secret-bundle-mutations-v1'))
            self.assertEqual(text.count('concurrency:'), text.count('queue: max'))
            self.assertNotIn('gcloud secrets versions destroy', text)
            self.assertNotIn('gcloud secrets delete', text)
        cd = (root/'workflows/ci-cd.yml').read_text()
        pr = (root/'workflows/pr-preview.yml').read_text()
        self.assertIn('secret_bundle_ci.py cleanup-legacy', cd)
        self.assertIn('secret_bundle_ci.py cleanup-legacy', pr)
        self.assertIn('MODTALE_SECRET_BUNDLES_ENABLED != \'true\'', cd)
        self.assertIn('MODTALE_SECRET_BUNDLES_ENABLED != \'true\'', pr)
        self.assertNotIn('migrate-secret', cd + pr)

    def test_mock_refresh_keeps_dedicated_source_and_template_identities(self):
        text = (Path(__file__).parents[1]/'workflows/mock-db-refresh.yml').read_text()
        for name in ('MOCK_SOURCE_MONGODB_URI','MOCK_TEMPLATE_MONGODB_URI','MOCK_SOURCE_R2_ACCESS_KEY','MOCK_TEMPLATE_R2_ACCESS_KEY'):
            self.assertIn('${{ secrets.' + name + ' }}', text)
        self.assertNotIn('secret_bundle_ci.py', text)


class CleanupTests(unittest.TestCase):
    def setUp(self):
        self.backend = Backend()
        data = json.loads(self.backend.versions['1'])
        data['secrets'].update({key: 'https://synthetic.r2.cloudflarestorage.com' if key.endswith('endpoint') else 'synthetic-retained' for key in plan.credential_keys('branch-preview', 'alpha')})
        self.backend.versions['1'] = encode(data)
        self.calls = []
        def runner(argv, env=None):
            self.calls.append((argv, env))
            return b'{"exists": true}' if argv[-1] == 'inspect-bucket' else b''
        self.subject = ci.BundleCI(lambda req: {'status': 404, 'body': {}},
            {**ENV, 'DB_NAME': 'modtale-alpha', 'IMAGE_TAG': 'alpha'}, runner=runner,
            store_factory=lambda boundary: BundleStore(boundary, self.backend))
        self.subject.inventory.github_lifecycle = lambda boundary: {'repository': plan.REPOSITORY, 'complete': True, 'items': []}
        self.subject.fanout = lambda version: self.calls.append(('fanout', version))

    def test_only_intended_keys_removed_after_confirmed_service_absence(self):
        self.subject.cleanup()
        values = json.loads(self.backend.published[0])['secrets']
        self.assertFalse(set(values).intersection(plan.credential_keys('branch-preview','alpha')))
        self.assertEqual(values['branch-preview-other-r2-token-id'], 'other-token')
        self.assertEqual(len(self.backend.published), 1)
        self.assertIn(('fanout', '2'), self.calls)
        cleanup_env = next(env for argv, env in self.calls if isinstance(argv,list) and argv[-1] == 'cleanup')
        self.assertEqual(cleanup_env['CLOUDFLARE_API_TOKEN_PROVISIONER'], '')
        self.assertEqual(cleanup_env['R2_RUNTIME_TOKEN_ID'], '')
        self.assertTrue(cleanup_env['R2_BUNDLE_LIFECYCLE_SCRIPT'].endswith('secret_bundle_ci.py'))
        self.assertNotIn('secrets', str([argv for argv,env in self.calls]))

    def test_wrong_cleanup_bucket_or_tag_stops_before_any_cloud_action(self):
        for name, value in [('R2_BUCKET_NAME','modtale-binaries'),('R2_BUCKET_NAME','modtale-preview-template'),('IMAGE_TAG','latest'),('DB_NAME','modtale')]:
            original = self.subject.env[name]
            self.subject.env[name] = value
            with self.assertRaises(ci.CIError): self.subject.cleanup()
            self.assertFalse(self.calls)
            self.assertFalse(self.backend.published)
            self.subject.env[name] = original

    def test_permission_failure_never_counts_as_absence(self):
        self.subject.transport = lambda req: {'status':403,'body':{}}
        with self.assertRaises(ci.CIError): self.subject.cleanup()
        self.assertFalse(self.calls)
        self.assertFalse(self.backend.published)

    def test_recreated_branch_blocks_cleanup(self):
        self.subject.inventory.github_lifecycle = lambda boundary: lifecycle()
        with self.assertRaises(Exception): self.subject.cleanup()
        self.assertFalse(self.calls)
        self.assertFalse(self.backend.published)

    def test_reappearing_service_blocks_four_key_removal(self):
        count = [0]
        def observation(boundary, preview, component):
            count[0] += 1
            return {'service': plan.service_name(boundary,preview,component), 'observation': 'not_found' if count[0] < 10 else 'present'}
        self.subject.inventory.observe_service_absence = observation
        with self.assertRaises(ci.CIError): self.subject.cleanup()
        self.assertFalse(self.backend.published)

    def test_legacy_cleanup_retains_originals_and_never_reads_or_publishes_bundle(self):
        original = self.subject.runner
        def runner(argv, env=None):
            if argv[:2] == ['gcloud','secrets']:
                self.calls.append((argv,env))
                return b'https://synthetic.r2.cloudflarestorage.com' if argv[-1].endswith('endpoint') else b'synthetic-private'
            return original(argv,env)
        self.subject.runner = runner
        self.subject.cleanup(legacy=True)
        self.assertFalse(self.backend.published)
        self.assertFalse(any(argv=='fanout' for argv,env in self.calls))
        reads=[argv for argv,env in self.calls if isinstance(argv,list) and argv[:2]==['gcloud','secrets']]
        self.assertEqual(len(reads),4)
        self.assertFalse(any('MODTALE_CONFIG' in str(argv) for argv in reads))

    def test_already_removed_bucket_can_resume_secret_patch(self):
        original = self.subject.runner
        self.subject.runner = lambda argv, env=None: b'{"exists": false}' if argv[-1] == 'inspect-bucket' else original(argv,env)
        self.subject.cleanup()
        self.assertFalse(any(isinstance(argv,list) and argv[0] == 'aws' for argv,env in self.calls))
        self.assertEqual(len(self.backend.published),1)


class TransportContractTests(unittest.TestCase):
    def test_every_actual_deploy_metadata_request_has_supported_mask_and_scope(self):
        from secret_bundle_transport import validate_request
        api = DeploymentAPI()
        deploy.deploy(api.ci(), ['--image', IMAGE])
        for request in api.requests:
            validate_request(request)

    def test_payload_failures_are_not_reflected_in_exception_or_output(self):
        import subprocess
        result = subprocess.CompletedProcess(['fake'],1,b'synthetic-secret',b'synthetic-secret')
        with patch('secret_bundle_ci.subprocess.run', return_value=result):
            with self.assertRaises(ci.CIError) as failure: ci.run(['fake'])
        self.assertNotIn('synthetic-secret',str(failure.exception))

    def test_shared_secret_numeric_canonical_alias_is_exact(self):
        api = DeploymentAPI()
        transport = api.transport
        def canonical(req):
            response = transport(req)
            if req['api'] == 'secret-manager-metadata':
                response['body']['name'] = response['body']['name'].replace('gen-lang-client-0244308719','145553429208')
            return response
        subject=api.ci();subject.transport=canonical
        self.assertIsNone(deploy.DeploymentJournal(subject).load()[0])



class FanoutAPI(DeploymentAPI):
    def transport(self, req):
        import secret_bundle_preview_inventory as inventory
        if req['api'] == 'github':
            self.requests.append(copy.deepcopy(req))
            return {'status':200,'body':{'data':{'repository':{'nameWithOwner':plan.REPOSITORY,
                'refs':{'nodes':[{'name':'alpha','target':{'oid':HEAD}}],
                        'pageInfo':{'hasNextPage':False,'endCursor':None}}}}}}
        if req['api'] == 'cloud-run-v1-ownership':
            self.requests.append(copy.deepcopy(req))
            return {'status':200,'body':{'items':[{'metadata':{'name':ENV['BACKEND_SERVICE'],'uid':self.service['uid']}}]}}
        if req['api'] == 'cloud-run-v2' and req['method'] == 'GET':
            fields=req['params']['fields']
            if req['path'].endswith('/services'):
                self.requests.append(copy.deepcopy(req))
                return {'status':200,'body':{'services':[{'name':self.full}]}}
            response=super().transport(req)
            if response['status'] != 200: return response
            body=response['body']
            if '/revisions/' in req['path']:
                if fields != rollout.REVISION_FIELDS:
                    body['serviceAccount']=inventory.RUNTIME_ACCOUNTS['branch-preview']
                    for container in body['containers']:
                        container.pop('image',None);container.pop('startupProbe',None)
            else:
                body.pop('uri',None);body.pop('ingress',None)
                if fields == inventory.SERVICE_FIELDS:
                    body['template']['serviceAccount']=inventory.RUNTIME_ACCOUNTS['branch-preview']
                    for container in body['template']['containers']:
                        container.pop('image',None);container.pop('startupProbe',None)
            return response
        if req['api'] == 'cloud-run-v2' and req['method'] == 'PATCH' and 'template' in req['body']:
            self.requests.append(copy.deepcopy(req))
            if req['body']['etag'] != self.service['etag']: return {'status':412,'body':{}}
            name=req['body']['template']['revision']
            pin=req['body']['template']['volumes'][0]['secret']['items'][0]['version']
            self.stage(name,pin)
            self.service['traffic']=copy.deepcopy(req['body']['traffic'])
            self.service['trafficStatuses']=copy.deepcopy(self.service['traffic'])
            return {'status':200,'body':{'name':'projects/gen-lang-client-0244308719/locations/us-central1/operations/synthetic'}}
        response = super().transport(req)
        if req['api']=='cloud-run-v2' and req['method']=='PATCH' and req['params']['fields']=='name':
            response['body']={'name':'projects/gen-lang-client-0244308719/locations/us-central1/operations/synthetic'}
        return response


class FanoutTests(unittest.TestCase):
    def test_full_fanout_uses_live_ownership_evidence_and_conditional_rollout(self):
        from secret_bundle_transport import validate_request
        api=FanoutAPI()
        subject=ci.BundleCI(api.transport,ENV,runner=api.runner,sleeper=lambda seconds:None)
        subject.fanout('2')
        self.assertEqual(api.service['template']['volumes'][0]['secret']['items'][0]['version'],'2')
        self.assertEqual(len(api.revisions),2)
        self.assertEqual(api.service['generation'],'3')
        record,_=rollout.SecretManagerJournal(api.transport,'branch-preview','alpha').load()
        self.assertEqual(record['phase'],'complete')
        for request in api.requests:validate_request(request)
        subject.fanout('2')
        self.assertEqual(len(api.revisions),2)
        subject.fanout('3')
        self.assertEqual(len(api.revisions),3)
        self.assertEqual(api.service['generation'],'5')

    def test_rollback_is_reported_as_failed_fanout_instead_of_success(self):
        class Executor:
            def __init__(self): self.count=0; self.recovered=False
            def advance(self, **kwargs):
                self.count+=1
                return {'status':'revision_failed' if self.count==1 else 'rolled_back'}
            def rollback(self, **kwargs): self.recovered=True
        api=FanoutAPI();subject=ci.BundleCI(api.transport,ENV,sleeper=lambda seconds:None)
        executor=Executor()
        with self.assertRaises(ci.CIError): subject.finish_rollout(executor)
        self.assertTrue(executor.recovered)

    def test_failed_operation_enters_rollback_instead_of_endless_polling(self):
        class Executor:
            def __init__(self): self.recovered=False
            def advance(self, **kwargs): return {'status':'rolled_back' if self.recovered else 'operation_failed'}
            def rollback(self, **kwargs): self.recovered=True
        api=FanoutAPI();subject=ci.BundleCI(api.transport,ENV,sleeper=lambda seconds:None)
        executor=Executor()
        with self.assertRaises(ci.CIError):subject.finish_rollout(executor)
        self.assertTrue(executor.recovered)

    def test_pending_service_is_resumed_before_new_alphabetically_earlier_work(self):
        api=FanoutAPI();subject=ci.BundleCI(api.transport,ENV,sleeper=lambda seconds:None)
        root=api.full.rsplit('/services/',1)[0]
        subject.inventory._service_names=lambda *args: {root+'/services/modtale-backend-alpha',root+'/services/modtale-backend-beta'}
        subject.inventory.github_lifecycle=lambda *args: {'repository':plan.REPOSITORY,'complete':True,'items':[
            {'name':'alpha','head_sha':HEAD},{'name':'beta','head_sha':HEAD}]}
        events=[]
        class Journal:
            def __init__(self,transport,profile,preview):self.preview=preview
            def load(self):
                events.append('load-'+self.preview)
                return ({'phase':'stage_wait','head_sha':HEAD},'checksum') if self.preview=='beta' else (None,None)
        class Executor:
            def __init__(self,transport,journal,lifecycle):self.preview=journal.preview
        def stop_after_resume(executor):
            events.append('resume-'+executor.preview)
            raise ci.CIError('synthetic-stop')
        subject.finish_rollout=stop_after_resume
        with patch('secret_bundle_rollout.SecretManagerJournal',Journal),patch('secret_bundle_rollout.RolloutExecutor',Executor):
            with self.assertRaises(ci.CIError):subject.fanout('2')
        self.assertEqual(events,['load-alpha','load-beta','resume-beta'])
        self.assertFalse(any(req['method']=='PATCH' for req in api.requests))

    def test_ownership_mismatch_stops_without_patching(self):
        api=FanoutAPI();original=api.transport
        def no_owner(req):
            return {'status':200,'body':{'items':[]}} if req['api']=='cloud-run-v1-ownership' else original(req)
        subject=ci.BundleCI(no_owner,ENV,runner=api.runner,sleeper=lambda seconds:None)
        with self.assertRaises(ci.CIError):subject.fanout('2')
        self.assertEqual(api.service['generation'],'1')



class EnvironmentSettingsTests(unittest.TestCase):
    def test_develop_activation_cannot_enable_any_other_environment(self):
        source = {'BUNDLE_SETTINGS_DEVELOP_ENABLED':'true', 'BUNDLE_SETTINGS_DEVELOP_VERSION':'19',
                  'MODTALE_SECRET_BUNDLES_ENABLED':'true', 'MODTALE_SECRET_BUNDLE_SHARED_VERSION':'777'}
        for environment in ('production','develop','branch-preview','pr-preview'):
            result = ci.resolve_settings({**source,'BUNDLE_SETTINGS_ENVIRONMENT':environment})
            self.assertEqual(result['MODTALE_SECRET_BUNDLES_ENABLED'],'true' if environment=='develop' else 'false')
            self.assertEqual(result['MODTALE_SECRET_BUNDLE_SHARED_VERSION'],'19' if environment=='develop' else '')
            self.assertEqual(result['MODTALE_SECRET_BUNDLE_CI_ENVIRONMENT'],environment)

    def test_each_environment_reads_only_its_distinct_flag_and_numeric_pin(self):
        prefixes={'production':'PRODUCTION','develop':'DEVELOP','branch-preview':'BRANCH_PREVIEW','pr-preview':'PR_PREVIEW'}
        for active,prefix in prefixes.items():
            source={'BUNDLE_SETTINGS_'+prefix+'_ENABLED':'true','BUNDLE_SETTINGS_'+prefix+'_VERSION':'23'}
            for selected in prefixes:
                result=ci.resolve_settings({**source,'BUNDLE_SETTINGS_ENVIRONMENT':selected})
                self.assertEqual(result['MODTALE_SECRET_BUNDLES_ENABLED'],'true' if selected==active else 'false')
                self.assertEqual(result['MODTALE_SECRET_BUNDLE_SHARED_VERSION'],'23' if selected==active and selected in ('production','develop') else '')

    def test_shared_opt_in_requires_exact_numeric_pin(self):
        for pin in ('','latest','0','1\nINJECT=true'):
            with self.assertRaises(ValueError):
                ci.resolve_settings({'BUNDLE_SETTINGS_ENVIRONMENT':'develop','BUNDLE_SETTINGS_DEVELOP_ENABLED':'true','BUNDLE_SETTINGS_DEVELOP_VERSION':pin})
        with self.assertRaises(ci.CIError):
            ci.resolve_settings({'BUNDLE_SETTINGS_ENVIRONMENT':'develop','BUNDLE_SETTINGS_DEVELOP_ENABLED':'TRUE'})

    def test_preview_credential_approval_cannot_cross_boundaries(self):
        with self.assertRaises(ci.CIError):
            ci.resolve_settings({'BUNDLE_SETTINGS_ENVIRONMENT':'pr-preview','BUNDLE_SETTINGS_PR_PREVIEW_ENABLED':'true',
                                 'BUNDLE_SETTINGS_PR_PREVIEW_CREDENTIAL_PROVISION_APPROVED':'modtale-backend-alpha'})
        result=ci.resolve_settings({'BUNDLE_SETTINGS_ENVIRONMENT':'pr-preview','BUNDLE_SETTINGS_PR_PREVIEW_ENABLED':'true',
             'BUNDLE_SETTINGS_BRANCH_PREVIEW_CREDENTIAL_PROVISION_APPROVED':'modtale-backend-alpha'})
        self.assertEqual(result['MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED'],'')

    def test_resolved_environment_mismatch_is_rejected(self):
        with self.assertRaises(ci.CIError):
            ci.profile_from_env({**ENV,'MODTALE_SECRET_BUNDLE_CI_ENVIRONMENT':'develop'})

    def test_runtime_resolver_needs_no_cloud_credentials_and_overwrites_generic_values(self):
        import subprocess
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory)/'env'
            result=subprocess.run([sys.executable,str(Path(ci.__file__)),'resolve-settings'],env={
                'PATH':'','GITHUB_ENV':str(target),'BUNDLE_SETTINGS_ENVIRONMENT':'develop',
                'BUNDLE_SETTINGS_DEVELOP_ENABLED':'true','BUNDLE_SETTINGS_DEVELOP_VERSION':'31',
                'MODTALE_SECRET_BUNDLES_ENABLED':'false'},capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            text=target.read_text()
            self.assertIn('MODTALE_SECRET_BUNDLES_ENABLED=true\n',text)
            self.assertIn('MODTALE_SECRET_BUNDLE_SHARED_VERSION=31\n',text)
            self.assertEqual(result.stdout,'')

    def test_workflows_resolve_environment_vars_only_in_trusted_runtime_steps(self):
        root=Path(__file__).parents[1]/'workflows'
        for filename,count in [('ci-cd.yml',3),('pr-preview.yml',2)]:
            text=(root/filename).read_text()
            self.assertEqual(text.count('name: Resolve environment-specific bundle settings'),count)
            self.assertEqual(text.count(' resolve-settings'),count)
            self.assertNotIn('vars.MODTALE_SECRET_BUNDLES_ENABLED',text)
            self.assertNotIn('vars.MODTALE_SECRET_BUNDLE_SHARED_VERSION',text)
            for line in text.splitlines():
                if 'vars.MODTALE_' in line and ('SECRET_BUNDLE' in line or '_APPROVED' in line):
                    self.assertTrue(line.startswith('          BUNDLE_SETTINGS_'),line)
            self.assertNotIn("MODTALE_SECRET_BUNDLES_ENABLED: ${{",text)
        text=(root/'pr-preview.yml').read_text()
        self.assertLess(text.index('base/.github/scripts/secret_bundle_ci.py" resolve-settings'),text.index('name: Check out pull request code'))



if __name__ == '__main__': unittest.main()
