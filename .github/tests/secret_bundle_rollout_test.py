"""Crash/recovery tests use only in-process fake APIs and temporary metadata files."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).parents[1] / 'scripts'))
import secret_bundle_rollout as r
import secret_bundle_preview_plan as plan

HEAD = 'a' * 40
LOCK = {'lock_held': True, 'lock_group': plan.LOCK_GROUP}
HTTP_PROBE = {'httpGet': {'path': '/actuator/health/readiness', 'port': 8080},
              'initialDelaySeconds': 0, 'periodSeconds': 10, 'timeoutSeconds': 5, 'failureThreshold': 24}


class FakeRun:
    def __init__(self, profile='branch-preview', preview='alpha'):
        self.profile, self.preview = profile, preview
        boundary, secret, self.short, self.full = r.scope(profile, preview)
        self.private = {'image': 'gcr.io/synthetic/backend@sha256:' + 'a'*64, 'env': {'PRIVATE': 'SYNTHETIC-PRIVATE'}}
        cfg = {'revision': self.short + '-00001-abc', 'containers': [{'name': 'app', 'image': self.private['image'],
            'startupProbe': copy.deepcopy(HTTP_PROBE), 'volumeMounts': [
            {'name': 'bundle', 'mountPath': '/app/secrets/bundles'}]}],
            'volumes': [{'name': 'bundle', 'secret': {'secret': secret, 'defaultMode': 292,
                         'items': [{'path': boundary + '.json', 'version': '1', 'mode': 256}]}}]}
        self.service = {'name': self.full, 'uid': 'service-uid', 'etag': 'e1', 'generation': '1',
            'observedGeneration': '1', 'terminalCondition': {'state': 'CONDITION_SUCCEEDED'},
            'latestCreatedRevision': cfg['revision'], 'latestReadyRevision': cfg['revision'], 'template': cfg,
            'traffic': [{'type': r.LATEST_TYPE, 'percent': 100}],
            'trafficStatuses': [{'type': r.LATEST_TYPE, 'revision': cfg['revision'], 'percent': 100}]}
        self.revisions = {}
        self.add_revision(cfg['revision'])
        self.requests, self.patches = [], []
        self.auto_ready = True
        self.activated, self.log_status = True, 200
        self.cloud_crash = None
        self.annotations, self.metadata_etag = {}, 'm1'
        self.metadata_conflict = False
        self.journal_crash = None
        self.secret_name = 'projects/' + self.full.split('/')[1] + '/secrets/' + secret

    def add_revision(self, name):
        self.revisions[name] = {'name': name, 'uid': 'uid-' + name, 'service': self.full,
            'createTime': '2026-10-02T14:00:00.123456789Z',
            **{key: copy.deepcopy(self.service['template'][key]) for key in ('containers', 'volumes')}}

    def settle(self, success=True):
        self.service['reconciling'] = False
        self.service['terminalCondition']['state'] = 'CONDITION_SUCCEEDED' if success else 'CONDITION_FAILED'
        if success:
            self.service['latestReadyRevision'] = self.service['latestCreatedRevision']
            self.service['observedGeneration'] = self.service['generation']
            self.service['trafficStatuses'] = copy.deepcopy(self.service['traffic'])

    def __call__(self, request):
        self.requests.append(copy.deepcopy(request))
        if request['api'] == 'cloud-logging':
            assert request['params']['fields'] == 'entries(insertId),nextPageToken'
            assert 'timestamp>=' in request['body']['filter']
            return {'status': self.log_status, 'body': {'entries': [{'insertId': 'safe-event'}]} if self.activated else {}}
        if request['api'] == 'secret-manager-metadata':
            assert request['origin'] == 'https://secretmanager.googleapis.com'
            assert request['path'] == '/v1/' + self.secret_name
            if request['method'] == 'GET':
                assert request['params'] == {'fields': 'name,etag,annotations'}
                return {'status': 200, 'body': {'name': self.secret_name, 'etag': self.metadata_etag, 'annotations': copy.deepcopy(self.annotations)}}
            assert request['params'] == {'fields': 'name,etag', 'updateMask': 'annotations'}
            if self.metadata_conflict or request['body']['etag'] != self.metadata_etag:
                return {'status': 412, 'body': {}}
            if self.journal_crash == 'before':
                self.journal_crash = None
                raise RuntimeError('SYNTHETIC-PRIVATE')
            self.annotations = copy.deepcopy(request['body']['annotations'])
            self.metadata_etag += 'n'
            if self.journal_crash == 'after':
                self.journal_crash = None
                raise RuntimeError('SYNTHETIC-PRIVATE')
            return {'status': 200, 'body': {'name': self.secret_name, 'etag': self.metadata_etag}}
        assert request['api'] == 'cloud-run-v2' and request['origin'] == r.ORIGIN
        if request['method'] == 'GET':
            if request['path'] == '/v2/' + self.full:
                assert request['params']['fields'] == r.SERVICE_FIELDS
                return {'status': 200, 'body': copy.deepcopy(self.service)}
            assert request['params']['fields'] == r.REVISION_FIELDS
            name = request['path'].rsplit('/', 1)[-1]
            return {'status': 200, 'body': copy.deepcopy(self.revisions[name])} if name in self.revisions else {'status': 404, 'body': {}}
        assert request['method'] == 'PATCH' and request['path'] == '/v2/' + self.full
        assert request['params']['fields'] == 'name'
        assert request['params']['allowMissing'] is False and request['params']['forceNewRevision'] is False
        assert set(request['body']) <= {'name', 'etag', 'template', 'traffic'}
        if request['body']['etag'] != self.service['etag']:
            return {'status': 412, 'body': {}}
        if self.cloud_crash == 'before':
            self.cloud_crash = None
            raise RuntimeError('SYNTHETIC-PRIVATE')
        self.patches.append(copy.deepcopy(request))
        if 'template' in request['body']:
            assert request['params']['updateMask'] == 'template.revision,template.volumes,traffic'
            assert set(request['body']['template']) == {'revision', 'volumes'}
            self.service['template'].update(copy.deepcopy(request['body']['template']))
            name = request['body']['template']['revision']
            assert name not in self.revisions, 'A replay must not create a duplicate revision'
            self.service['latestCreatedRevision'] = name
            self.add_revision(name)
        else:
            assert request['params']['updateMask'] == 'traffic'
        self.service['traffic'] = copy.deepcopy(request['body']['traffic'])
        self.service['generation'] = str(int(self.service['generation']) + 1)
        self.service['etag'] = 'e' + self.service['generation']
        self.service['reconciling'] = True
        self.service['terminalCondition']['state'] = 'CONDITION_PENDING'
        if self.auto_ready:
            self.settle()
        if self.cloud_crash == 'after':
            self.cloud_crash = None
            raise RuntimeError('SYNTHETIC-PRIVATE')
        return {'status': 200, 'body': {'name': self.full.rsplit('/services/', 1)[0] + '/operations/op-' + self.service['generation']}}


class MemoryJournal:
    def __init__(self):
        self.raw = None
        self.calls = 0
        self.crash_call = None
        self.crash_after = False

    def load(self):
        return (json.loads(self.raw), hashlib.sha256(self.raw).hexdigest()) if self.raw is not None else (None, None)

    def save(self, record, expected):
        self.calls += 1
        if self.crash_call == self.calls and not self.crash_after:
            raise RuntimeError('SYNTHETIC-PRIVATE')
        if self.load()[1] != expected:
            raise RuntimeError('CAS conflict')
        self.raw = r.journal_bytes(record)
        if self.crash_call == self.calls:
            raise RuntimeError('SYNTHETIC-PRIVATE')
        return self.load()[1]


class RolloutTests(unittest.TestCase):
    def setUp(self):
        self.api, self.journal = FakeRun(), MemoryJournal()
        self.head = HEAD

    def lifecycle(self, profile):
        return {'repository': plan.REPOSITORY, 'complete': True, 'items': [{'name': 'alpha', 'head_sha': self.head}]}

    def executor(self):
        return r.RolloutExecutor(self.api, self.journal, self.lifecycle)

    def proof(self):
        current = self.api.service['latestCreatedRevision']
        return {'verified_active': True, 'ownership_verified': True, 'profile': self.api.profile, 'preview_id': self.api.preview,
                'service_uid': self.api.service['uid'], 'revision_uid': self.api.revisions[current]['uid'],
                'bundle_version': self.api.service['template']['volumes'][0]['secret']['items'][0]['version']}

    def begin(self, **kwargs):
        args = {'preview_id': 'alpha', 'raw_branch': 'alpha', 'head_sha': HEAD, **LOCK, **kwargs}
        return self.executor().begin('branch-preview', args.pop('target_version', '2'), args.pop('transaction_id', 'a123456789ab'), self.proof(), **args)

    def finish(self):
        for _ in range(15):
            result = self.executor().advance(**LOCK)
            if result['status'] in ('complete', 'rolled_back'):
                return result
        self.fail('Rollout did not finish')

    def test_readiness_probe_projection_never_requests_header_values(self):
        for fields in (r.SERVICE_FIELDS, r.REVISION_FIELDS):
            self.assertIn(r.STARTUP_PROBE_FIELDS, fields)
            self.assertIn('httpHeaders(name)', fields)
            self.assertNotIn('httpHeaders(name,value)', fields)
            self.assertNotIn('env', fields)

    def test_long_opaque_etag_preserves_quotes_and_exact_conditional_token(self):
        for size in (133, r.MAX_ETAG_BYTES):
            with self.subTest(size=size):
                self.setUp()
                token = '"' + 'A' * (size - 2) + '"'
                self.api.service['etag'] = token
                state = r.parse_service(self.api.service, self.api.profile, self.api.preview)
                self.assertEqual(state['etag'], token)
                self.begin()
                self.executor().advance(**LOCK)
                self.assertEqual(self.api.patches[0]['body']['etag'], token)
                self.assertEqual(self.finish()['status'], 'complete')

    def test_opaque_etag_bound_uses_utf8_bytes_and_rejects_controls(self):
        for token in ('A' * (r.MAX_ETAG_BYTES + 1), 'é' * (r.MAX_ETAG_BYTES // 2 + 1),
                      'opaque\nprivate', 'opaque\rprivate', 'opaque\x00private', ''):
            with self.subTest(token_length=len(token)):
                raw = copy.deepcopy(self.api.service)
                raw['etag'] = token
                with self.assertRaises(r.RolloutError) as error:
                    r.parse_service(raw, self.api.profile, self.api.preview)
                self.assertNotIn('private', str(error.exception))

    def test_generic_parsers_allow_legacy_probe_repair_but_begin_blocks_it(self):
        for probe in (None, {'tcpSocket': {'port': 8080}}, {'grpc': {'port': 8080}},
                      {**HTTP_PROBE, 'failureThreshold': 60}):
            with self.subTest(probe=probe):
                self.setUp()
                container = self.api.service['template']['containers'][0]
                if probe is None:
                    container.pop('startupProbe')
                else:
                    container['startupProbe'] = copy.deepcopy(probe)
                current = self.api.service['latestCreatedRevision']
                self.api.add_revision(current)
                r.parse_service(self.api.service, self.api.profile, self.api.preview)
                r.parse_revision(self.api.revisions[current], self.api.profile, self.api.preview, current)
                with self.assertRaises(r.RolloutError):
                    self.begin()
                self.assertEqual(self.api.patches, [])
                self.assertIsNone(self.journal.raw)

    def test_readiness_probe_accepts_documented_default_omissions(self):
        for probe in (HTTP_PROBE, {'httpGet': HTTP_PROBE['httpGet']},
                      {**HTTP_PROBE, 'httpGet': {**HTTP_PROBE['httpGet'], 'httpHeaders': []}}):
            r.require_readiness_startup_probe({'startupProbe': copy.deepcopy(probe)})
        omitted = copy.deepcopy(HTTP_PROBE)
        omitted.pop('initialDelaySeconds')
        current = self.api.service['latestCreatedRevision']
        self.api.revisions[current]['containers'][0]['startupProbe'] = omitted
        self.assertEqual(self.begin()['status'], 'prepared')
        self.assertEqual(self.finish()['status'], 'complete')

    def test_readiness_probe_rejects_unverified_endpoint_port_headers_and_budget(self):
        probes = [
            {**HTTP_PROBE, 'httpGet': {'path': '/actuator/health', 'port': 8080}},
            {**HTTP_PROBE, 'httpGet': {'path': '/actuator/health/readiness', 'port': 80}},
            {**HTTP_PROBE, 'httpGet': {'path': '/actuator/health/readiness'}},
            {**HTTP_PROBE, 'httpGet': {**HTTP_PROBE['httpGet'], 'httpHeaders': [{'name': 'Authorization'}]}},
            {**HTTP_PROBE, 'initialDelaySeconds': 1},
            {**HTTP_PROBE, 'periodSeconds': 241, 'failureThreshold': 1},
            {**HTTP_PROBE, 'failureThreshold': 25},
        ]
        for probe in probes:
            with self.subTest(probe=probe), self.assertRaises(r.RolloutError):
                r.require_readiness_startup_probe({'startupProbe': probe})

    def test_probe_recursive_shapes_reject_malformed_or_unmasked_values_without_leak(self):
        bad = [None, {}, [], {'exec': {'command': ['SYNTHETIC-PRIVATE']}},
               {**HTTP_PROBE, 'tcpSocket': {'port': 8080}},
               {**HTTP_PROBE, 'periodSeconds': True}, {**HTTP_PROBE, 'periodSeconds': '10'},
               {**HTTP_PROBE, 'periodSeconds': 0}, {**HTTP_PROBE, 'failureThreshold': -1},
               {**HTTP_PROBE, 'failureThreshold': 2147483648},
               {**HTTP_PROBE, 'timeoutSeconds': 11},
               {**HTTP_PROBE, 'httpGet': {'path': 3, 'port': 8080}},
               {**HTTP_PROBE, 'httpGet': {'port': True}},
               {**HTTP_PROBE, 'httpGet': {'httpHeaders': [{}]}},
               {**HTTP_PROBE, 'httpGet': {'httpHeaders': [{'name': 'Authorization', 'value': 'SYNTHETIC-PRIVATE'}]}},
               {'grpc': {'port': 8080, 'service': 'SYNTHETIC-PRIVATE'}}]
        for probe in bad:
            with self.subTest(probe=probe):
                malformed = copy.deepcopy(self.api.service)
                malformed['template']['containers'][0]['startupProbe'] = probe
                with self.assertRaises(r.RolloutError) as error:
                    r.parse_service(malformed, self.api.profile, self.api.preview)
                self.assertNotIn('SYNTHETIC-PRIVATE', str(error.exception))

    def test_probe_is_preserved_without_being_in_mutation_body(self):
        self.begin()
        self.finish()
        for revision in self.api.revisions.values():
            self.assertEqual(revision['containers'][0]['startupProbe'], HTTP_PROBE)
        for request in self.api.patches:
            self.assertNotIn('startupProbe', json.dumps(request))

    def test_probe_drift_before_stage_fails_without_cloud_write(self):
        self.begin()
        self.api.service['template']['containers'][0]['startupProbe'] = {'tcpSocket': {'port': 8080}}
        with self.assertRaises(r.RolloutError):
            self.executor().advance(**LOCK)
        self.assertEqual(self.api.patches, [])

    def test_candidate_probe_drift_blocks_promotion(self):
        self.begin()
        self.executor().advance(**LOCK)
        candidate = self.api.service['latestCreatedRevision']
        self.api.revisions[candidate]['containers'][0]['startupProbe'] = {'tcpSocket': {'port': 8080}}
        with self.assertRaises(r.RolloutError):
            self.executor().advance(**LOCK)
        self.assertEqual(len(self.api.patches), 1)
        self.assertNotEqual(self.api.service['traffic'][0]['revision'], candidate)

    def test_older_journal_missing_projected_probe_history_requires_explicit_recovery(self):
        for landed in (False, True):
            with self.subTest(landed=landed):
                self.setUp()
                self.begin()
                if landed:
                    self.executor().advance(**LOCK)
                record, _ = self.journal.load()
                # The OLD projection hid this field; the real service/revision
                # continues to have it. Never manufacture its historical value.
                for field in ('base', 'intent_base'):
                    record[field]['template']['containers'][0].pop('startupProbe')
                record['base_revision']['containers'][0].pop('startupProbe')
                self.journal.raw = r.journal_bytes(record)
                before, count = self.journal.raw, len(self.api.patches)
                with self.assertRaises(r.RolloutError):
                    self.executor().advance(**LOCK)
                with self.assertRaises(r.RolloutError):
                    self.executor().rollback(**LOCK)
                self.assertEqual(len(self.api.patches), count)
                self.assertEqual(self.journal.raw, before)

    def test_successful_staging_has_no_traffic_before_exact_promotion(self):
        old = self.api.service['latestReadyRevision']
        self.assertEqual(self.begin()['status'], 'prepared')
        self.assertEqual(self.api.patches, [])
        self.assertEqual(self.executor().advance(**LOCK)['status'], 'stage_requested')
        self.assertEqual(self.api.service['traffic'], [{'type': r.REVISION_TYPE, 'revision': old, 'percent': 100}])
        self.assertEqual(self.executor().advance(**LOCK)['status'], 'verified_ready_for_promotion')
        self.assertEqual(len(self.api.patches), 1)
        result = self.finish()
        self.assertEqual(result['status'], 'complete')
        self.assertEqual(self.api.service['traffic'][0]['revision'], result['revision'])
        self.assertEqual(len(self.api.patches), 2)

    def test_images_environment_and_original_secrets_are_never_read_or_rewritten(self):
        original = copy.deepcopy(self.api.private)
        self.begin()
        self.finish()
        raw = json.dumps(self.api.requests) + self.journal.raw.decode()
        for sentinel in ('SYNTHETIC-PRIVATE', 'SYNTHETIC-IMAGE', 'WARDEN_API_KEY', 'HYTALE_CLIENT_SECRET', 'annotations'):
            self.assertNotIn(sentinel, raw)
        self.assertEqual(self.api.private, original)
        self.assertNotIn('env', r.SERVICE_FIELDS)
        self.assertIn('image', r.REVISION_FIELDS)
        for request in self.api.patches:
            self.assertNotIn('containers', request['body'].get('template', {}))
        self.assertEqual(self.api.service['template']['volumes'][0]['secret']['defaultMode'], 292)
        self.assertEqual(self.api.service['template']['volumes'][0]['secret']['items'][0]['mode'], 256)

    def test_weighted_traffic_and_existing_tags_are_preserved(self):
        old = self.api.service['latestReadyRevision']
        canary = self.api.short + '-older-abc'
        self.api.add_revision(canary)
        targets = [{'type': r.LATEST_TYPE, 'percent': 80, 'tag': 'stable'},
                   {'type': r.REVISION_TYPE, 'revision': canary, 'percent': 20, 'tag': 'canary'}]
        self.api.service['traffic'] = targets
        self.api.service['trafficStatuses'] = [dict(targets[0], revision=old), targets[1]]
        self.begin()
        result = self.finish()
        rows = self.api.service['traffic']
        self.assertIn({'type': r.REVISION_TYPE, 'revision': result['revision'], 'percent': 80}, rows)
        self.assertIn({'type': r.REVISION_TYPE, 'revision': canary, 'percent': 20}, rows)
        self.assertIn({'type': r.REVISION_TYPE, 'revision': old, 'percent': 0, 'tag': 'stable'}, rows)
        self.assertIn({'type': r.REVISION_TYPE, 'revision': canary, 'percent': 0, 'tag': 'canary'}, rows)

    def test_crash_before_cloud_write_replays_exact_conditional_intent(self):
        self.begin()
        self.api.cloud_crash = 'before'
        with self.assertRaises(r.RolloutError):
            self.executor().advance(**LOCK)
        self.assertEqual(self.journal.load()[0]['phase'], 'stage_intent')
        self.finish()
        self.assertEqual(len(self.api.patches), 2)

    def test_crash_after_cloud_write_is_reconciled_without_duplicate_revision(self):
        self.begin()
        self.api.cloud_crash = 'after'
        with self.assertRaises(r.RolloutError):
            self.executor().advance(**LOCK)
        self.assertEqual(self.journal.load()[0]['phase'], 'stage_intent')
        self.finish()
        self.assertEqual(len(self.api.patches), 2)

    def test_crash_before_or_after_every_checkpoint_is_resumable(self):
        for after in (False, True):
            for checkpoint in range(1, 8):
                with self.subTest(after=after, checkpoint=checkpoint):
                    self.setUp()
                    self.journal.crash_call, self.journal.crash_after = checkpoint, after
                    try:
                        self.begin()
                        self.finish()
                    except r.RolloutError:
                        pass
                    self.journal.crash_call = None
                    if self.journal.raw is None:
                        self.begin()
                    result = self.finish()
                    self.assertEqual(result['status'], 'complete')
                    self.assertEqual(len(self.api.patches), 2)

    def test_readiness_delay_keeps_original_traffic(self):
        self.api.auto_ready = False
        self.begin()
        self.executor().advance(**LOCK)
        self.assertEqual(self.executor().advance(**LOCK)['status'], 'waiting_for_readiness')
        self.assertEqual(len(self.api.patches), 1)
        self.api.auto_ready = True
        self.api.settle()
        self.finish()

    def test_missing_log_evidence_or_permission_never_promotes(self):
        self.begin()
        self.executor().advance(**LOCK)
        self.api.activated = False
        self.assertEqual(self.executor().advance(**LOCK)['status'], 'waiting_for_activation')
        self.api.log_status = 403
        self.assertEqual(self.executor().advance(**LOCK)['reason'], 'log_read_unavailable')
        self.assertEqual(len(self.api.patches), 1)
        self.api.activated, self.api.log_status = True, 200
        self.finish()

    def test_lifecycle_drift_stops_promotion_but_explicit_rollback_still_works(self):
        old = self.api.service['latestReadyRevision']
        self.begin()
        self.executor().advance(**LOCK)
        self.head = 'b' * 40
        with self.assertRaises(r.RolloutError):
            self.executor().advance(**LOCK)
        self.assertEqual(self.api.service['traffic'][0]['revision'], old)
        self.assertEqual(self.executor().rollback(**LOCK)['status'], 'rollback_prepared')
        result = self.finish()
        self.assertEqual(result['status'], 'rolled_back')
        self.assertEqual(self.api.service['template']['volumes'][0]['secret']['items'][0]['version'], '1')
        self.assertEqual(self.api.service['traffic'][0]['revision'], old)

    def test_rollback_after_complete_restores_original_pin_and_distribution(self):
        old = self.api.service['latestReadyRevision']
        self.begin()
        self.finish()
        self.executor().rollback(**LOCK)
        self.assertEqual(self.finish()['status'], 'rolled_back')
        self.assertEqual(self.api.service['traffic'][0]['revision'], old)
        self.assertEqual(len(self.api.revisions), 3)

    def test_crash_during_rollback_is_reconciled(self):
        self.begin()
        self.finish()
        self.executor().rollback(**LOCK)
        self.api.cloud_crash = 'after'
        with self.assertRaises(r.RolloutError):
            self.executor().advance(**LOCK)
        self.assertEqual(self.finish()['status'], 'rolled_back')
        self.assertEqual(len(self.api.patches), 3)

    def test_failed_revision_can_be_rolled_back_without_traffic_cutover(self):
        self.api.auto_ready = False
        self.begin()
        self.executor().advance(**LOCK)
        self.api.settle(False)
        self.assertEqual(self.executor().advance(**LOCK)['status'], 'revision_failed')
        self.executor().rollback(**LOCK)
        self.api.auto_ready = True
        self.assertEqual(self.finish()['status'], 'rolled_back')

    def test_generation_uid_etag_and_traffic_drift_fail_closed(self):
        for mutation in (lambda s: s.update(generation='9'), lambda s: s.update(uid='recreated'),
                         lambda s: s.update(etag='external'), lambda s: s['traffic'].append({'type': r.REVISION_TYPE, 'revision': self.api.short+'-old-abc', 'tag':'unexpected'})):
            self.setUp()
            self.begin()
            mutation(self.api.service)
            with self.assertRaises(r.RolloutError):
                self.executor().advance(**LOCK)
            self.assertEqual(len(self.api.patches), 0)

    def test_new_revision_uid_change_after_gate_blocks_promotion(self):
        self.begin()
        self.executor().advance(**LOCK)
        self.executor().advance(**LOCK)
        self.api.revisions[self.api.service['latestCreatedRevision']]['uid'] = 'recreated'
        with self.assertRaises(r.RolloutError):
            self.executor().advance(**LOCK)
        self.assertEqual(len(self.api.patches), 1)

    def test_noop_does_not_create_journal_or_revision(self):
        self.assertEqual(self.begin(target_version='1')['status'], 'unchanged')
        self.assertIsNone(self.journal.raw)
        self.assertEqual(len(self.api.revisions), 1)

    def test_lock_required_before_any_read_or_write(self):
        with self.assertRaises(r.RolloutError):
            self.begin(lock_held=False)
        self.assertEqual(self.api.requests, [])

    def test_malformed_target_and_transaction_fail_before_cloud_read(self):
        for kwargs in ({'target_version':'latest'}, {'target_version':'01'}, {'transaction_id':'../bad'}):
            with self.assertRaises(r.RolloutError):
                self.begin(**kwargs)
        self.assertEqual(self.api.requests, [])

    def test_spoofed_mode_or_ownership_proof_is_rejected(self):
        for field in ('verified_active', 'ownership_verified'):
            proof = self.proof()
            proof[field] = False
            with self.assertRaises(r.RolloutError):
                self.executor().begin('branch-preview','2','a123456789ab',proof,preview_id='alpha',raw_branch='alpha',head_sha=HEAD,**LOCK)
        self.assertIsNone(self.journal.raw)

    def test_unfinished_journal_cannot_be_replaced(self):
        self.begin()
        with self.assertRaises(r.RolloutError):
            self.begin(transaction_id='b123456789ab', replace_terminal=True)

    def test_sequential_rollouts_preserve_old_revision_provenance(self):
        self.begin()
        self.finish()
        self.begin(target_version='3',transaction_id='b123456789ab',replace_terminal=True)
        self.finish()
        record = self.journal.load()[0]
        self.assertEqual({entry['bundle_version'] for entry in record['retained_revisions']}, {'1','2'})
        self.assertTrue(all(entry['verified_active'] for entry in record['retained_revisions']))
        self.assertEqual(len(self.api.revisions), 3)

    def test_malformed_cloud_response_or_journal_never_leaks(self):
        self.api.service['template']['containers'][0]['env'] = [{'value': 'SYNTHETIC-PRIVATE'}]
        with self.assertRaises(r.RolloutError) as caught:
            self.begin()
        self.assertNotIn('SYNTHETIC', str(caught.exception))
        self.assertIsNone(self.journal.raw)

    def test_atomic_file_journal_survives_new_executor_and_enforces_cas(self):
        with tempfile.TemporaryDirectory() as directory:
            self.journal = r.AtomicFileJournal(Path(directory)/'rollout.json')
            self.begin()
            first, digest = self.journal.load()
            self.journal = r.AtomicFileJournal(Path(directory)/'rollout.json')
            self.finish()
            with self.assertRaises(r.RolloutError):
                self.journal.save(first, digest)
            self.assertLess((Path(directory)/'rollout.json').stat().st_size, r.MAX_BYTES)

    def test_secret_manager_journal_survives_new_worker_and_preserves_other_annotations(self):
        self.api.annotations['unrelated-key'] = 'metadata'
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        self.begin()
        self.executor().advance(**LOCK)
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        self.finish()
        self.assertEqual(self.api.annotations['unrelated-key'], 'metadata')
        self.assertEqual(len(self.api.annotations), 2)
        for request in self.api.requests:
            self.assertNotIn('/versions/', request['path'])
            self.assertNotIn('access', request['path'])

    def test_annotation_capacity_failure_prevents_any_cloud_run_write(self):
        self.api.annotations['other'] = 'x' * 16000
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        with self.assertRaises(r.RolloutError):
            self.begin()
        self.assertEqual(self.api.patches, [])
        self.assertEqual(set(self.api.annotations), {'other'})

    def test_annotation_etag_conflict_prevents_dependent_side_effect(self):
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        self.api.metadata_conflict = True
        with self.assertRaises(r.RolloutError):
            self.begin()
        self.assertEqual(self.api.patches, [])

    def test_uncertain_annotation_ack_is_recovered_before_cloud_write(self):
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        self.api.journal_crash = 'after'
        with self.assertRaises(r.RolloutError):
            self.begin()
        self.assertEqual(self.api.patches, [])
        self.assertEqual(self.finish()['status'], 'complete')

    def test_compression_bomb_and_unknown_journal_fields_are_rejected(self):
        import base64, zlib
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        self.api.annotations[self.journal.key] = 'v1:' + base64.b64encode(zlib.compress(b'x'*(r.MAX_BYTES+1))).decode()
        with self.assertRaises(r.RolloutError):
            self.journal.load()
        self.assertEqual(self.api.patches, [])

    def test_all_runtime_profiles_use_only_the_three_fixed_bundle_containers(self):
        for profile, preview, branch in (('prod', None, 'main'), ('dev', None, 'develop'), ('pr-preview', '42', None)):
            with self.subTest(profile=profile):
                self.api, self.journal = FakeRun(profile, preview), MemoryJournal()
                if profile == 'pr-preview':
                    snapshot = {'repository': plan.REPOSITORY, 'complete': True, 'items': [
                        {'number': '42', 'state': 'open', 'head_sha': HEAD, 'head_repository': 'contributor/modtale'}]}
                else:
                    snapshot = {'repository': plan.REPOSITORY, 'complete': True, 'items': [{'name': branch, 'head_sha': HEAD}]}
                executor = r.RolloutExecutor(self.api, self.journal, lambda p: snapshot)
                executor.begin(profile, '2', 'a123456789ab', self.proof(), preview_id=preview, raw_branch=branch, head_sha=HEAD, **LOCK)
                for _ in range(10):
                    result = executor.advance(**LOCK)
                    if result['status'] == 'complete':
                        break
                self.assertEqual(result['status'], 'complete')
                secret = self.api.service['template']['volumes'][0]['secret']['secret']
                self.assertEqual(secret, 'MODTALE_CONFIG_PR_PREVIEW' if profile == 'pr-preview' else 'MODTALE_CONFIG_SHARED')

    def test_closed_or_changed_pr_blocks_promotion(self):
        self.api, self.journal = FakeRun('pr-preview', '42'), MemoryJournal()
        snapshot = {'repository': plan.REPOSITORY, 'complete': True, 'items': [
            {'number': '42', 'state': 'open', 'head_sha': HEAD, 'head_repository': 'contributor/modtale'}]}
        executor = r.RolloutExecutor(self.api, self.journal, lambda p: snapshot)
        executor.begin('pr-preview', '2', 'a123456789ab', self.proof(), preview_id='42', raw_branch=None, head_sha=HEAD, **LOCK)
        executor.advance(**LOCK)
        snapshot['items'][0]['state'] = 'closed'
        with self.assertRaises(r.RolloutError):
            executor.advance(**LOCK)
        self.assertEqual(len(self.api.patches), 1)

    def test_readiness_evidence_must_still_exist_when_promotion_intent_resumes(self):
        self.begin()
        self.executor().advance(**LOCK)
        self.executor().advance(**LOCK)
        self.api.activated = False
        self.assertEqual(self.executor().advance(**LOCK)['status'], 'waiting_for_activation')
        self.assertEqual(len(self.api.patches), 1)

    def test_missing_candidate_revision_does_not_promote(self):
        self.begin()
        self.executor().advance(**LOCK)
        self.api.revisions.pop(self.api.service['latestCreatedRevision'])
        self.assertEqual(self.executor().advance(**LOCK)['status'], 'waiting_for_revision')
        self.assertEqual(len(self.api.patches), 1)

    def test_external_update_between_read_and_patch_is_rejected_by_etag(self):
        self.begin()
        delegate = self.api
        def concurrent(request):
            if request['api'] == 'cloud-run-v2' and request['method'] == 'PATCH':
                self.api.service['etag'] = 'external-writer'
            return delegate(request)
        executor = r.RolloutExecutor(concurrent, self.journal, self.lifecycle)
        with self.assertRaises(r.RolloutError):
            executor.advance(**LOCK)
        self.assertEqual(self.api.patches, [])

    def test_second_writer_cannot_skip_first_service_journal(self):
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        self.begin()
        other = r.RolloutExecutor(self.api, r.SecretManagerJournal(self.api, self.api.profile, self.api.preview), self.lifecycle)
        with self.assertRaises(r.RolloutError):
            other.begin('branch-preview','3','b123456789ab',self.proof(),preview_id='alpha',raw_branch='alpha',head_sha=HEAD,**LOCK)
        self.assertEqual(self.api.patches, [])
        self.assertEqual(self.journal.load()[0]['target_version'], '2')

    def test_terminal_handoff_accepts_a_newer_verified_ordinary_deployment(self):
        self.begin()
        self.finish()
        newer = self.api.short + '-image-update-abc'
        self.api.service['template']['revision'] = newer
        self.api.service['latestCreatedRevision'] = newer
        self.api.service['generation'] = str(int(self.api.service['generation'])+1)
        self.api.service['etag'] = 'ordinary-deployment'
        self.api.add_revision(newer)
        self.api.service['traffic'] = [{'type': r.REVISION_TYPE, 'revision': newer, 'percent': 100}]
        self.api.settle()
        self.begin(target_version='3', transaction_id='b123456789ab', replace_terminal=True)
        self.assertEqual(self.finish()['status'], 'complete')
        self.assertEqual({entry['bundle_version'] for entry in self.journal.load()[0]['retained_revisions']}, {'1','2'})

    def test_terminal_handoff_rejects_service_recreation(self):
        self.begin()
        self.finish()
        self.api.service['uid'] = 'recreated'
        with self.assertRaises(r.RolloutError):
            self.begin(target_version='3', transaction_id='b123456789ab', replace_terminal=True)

    def test_stale_annotation_checksum_cannot_replace_newer_service_journal(self):
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        self.begin()
        old, checksum = self.journal.load()
        self.executor().advance(**LOCK)
        with self.assertRaises(r.RolloutError):
            self.journal.save(old, checksum)

    def test_nonsecret_volume_or_multiple_mounts_are_refused_without_dropping_them(self):
        self.api.service['template']['volumes'].append({'name': 'unrelated'})
        with self.assertRaises(r.RolloutError):
            self.begin()
        self.assertEqual(self.api.patches, [])

    def test_source_metadata_unknown_fields_are_never_persisted(self):
        current = self.api.service['latestCreatedRevision']
        self.api.revisions[current]['annotations'] = {'SYNTHETIC-PRIVATE': 'value'}
        with self.assertRaises(r.RolloutError) as caught:
            self.begin()
        self.assertNotIn('SYNTHETIC', str(caught.exception))
        self.assertIsNone(self.journal.raw)

    def test_mutable_image_tags_are_not_silently_reresolved(self):
        self.api.service['template']['containers'][0]['image'] = 'gcr.io/synthetic/backend:develop'
        with self.assertRaises(r.RolloutError):
            self.begin()
        self.assertIsNone(self.journal.raw)
        self.assertEqual(self.api.patches, [])

    def test_changed_image_digest_in_created_revision_blocks_promotion(self):
        self.begin()
        self.executor().advance(**LOCK)
        self.api.revisions[self.api.service['latestCreatedRevision']]['containers'][0]['image'] = 'gcr.io/synthetic/backend@sha256:'+'b'*64
        with self.assertRaises(r.RolloutError):
            self.executor().advance(**LOCK)
        self.assertEqual(len(self.api.patches), 1)

    def test_only_verified_fixed_numeric_project_aliases_are_accepted(self):
        delegate = self.api
        alias = 'projects/145553429208/secrets/MODTALE_CONFIG_BRANCH_PREVIEW'
        def canonical(request):
            response = delegate(request)
            if request['api'] == 'secret-manager-metadata' and response['status'] == 200:
                response['body']['name'] = alias
            return response
        self.journal = r.SecretManagerJournal(canonical, self.api.profile, self.api.preview)
        self.begin()
        self.finish()
        alias = 'projects/999999999999/secrets/MODTALE_CONFIG_BRANCH_PREVIEW'
        with self.assertRaises(r.RolloutError):
            self.journal.load()

    def test_completed_transaction_does_not_hide_later_failed_readiness(self):
        self.begin()
        self.finish()
        self.api.service['terminalCondition']['state'] = 'CONDITION_FAILED'
        self.assertEqual(self.executor().advance(**LOCK)['status'], 'revision_failed')

    def test_lifecycle_change_after_gate_can_rollback_unissued_promotion(self):
        self.begin()
        self.executor().advance(**LOCK)
        self.executor().advance(**LOCK)
        self.assertEqual(self.journal.load()[0]['phase'], 'promote_intent')
        self.head = 'b'*40
        with self.assertRaises(r.RolloutError):
            self.executor().advance(**LOCK)
        self.assertEqual(self.executor().rollback(**LOCK)['status'], 'rollback_prepared')
        self.assertEqual(self.finish()['status'], 'rolled_back')
        self.assertEqual(len(self.api.patches), 2)

    def test_late_original_promotion_is_reconciled_once_for_compensating_rollback(self):
        self.begin()
        self.executor().advance(**LOCK)
        self.executor().advance(**LOCK)
        origin = self.journal.load()[0]
        self.executor().rollback(**LOCK)
        # The original in-flight request wins its etag race before rollback writes.
        self.executor()._patch(origin, 'promote', r.parse_service(origin['intent_base'], 'branch-preview', 'alpha'))
        self.assertEqual(self.executor().advance(**LOCK)['status'], 'rollback_rebased_after_origin_write')
        self.assertTrue(self.journal.load()[0]['rollback_origin']['consumed'])
        self.assertEqual(self.finish()['status'], 'rolled_back')

    def test_compensating_rollback_never_rebases_repeated_external_generations(self):
        self.begin()
        self.executor().advance(**LOCK)
        self.executor().advance(**LOCK)
        origin = self.journal.load()[0]
        self.executor().rollback(**LOCK)
        self.executor()._patch(origin, 'promote', r.parse_service(origin['intent_base'], 'branch-preview', 'alpha'))
        self.executor().advance(**LOCK)
        self.api.service['generation'] = str(int(self.api.service['generation'])+1)
        self.api.service['etag'] = 'other-writer'
        with self.assertRaises(r.RolloutError):
            self.executor().advance(**LOCK)

    def test_late_origin_must_finish_reconciling_before_one_shot_rollback_rebase(self):
        for operation in ('stage', 'promote'):
            with self.subTest(operation=operation):
                self.setUp()
                self.begin()
                if operation == 'promote':
                    self.executor().advance(**LOCK)
                    self.executor().advance(**LOCK)
                origin = self.journal.load()[0]
                self.executor().rollback(**LOCK)
                self.api.auto_ready = False
                self.executor()._patch(origin, operation, r.parse_service(origin['intent_base'], 'branch-preview', 'alpha'))
                self.assertEqual(self.executor().advance(**LOCK)['status'], 'waiting_for_origin_reconciliation')
                self.assertFalse(self.journal.load()[0]['rollback_origin']['consumed'])
                self.api.settle()
                self.api.auto_ready = True
                self.assertEqual(self.executor().advance(**LOCK)['status'], 'rollback_rebased_after_origin_write')
                self.assertEqual(self.finish()['status'], 'rolled_back')

    def test_rollback_before_candidate_checkpoint_retains_staged_revision(self):
        self.begin()
        self.executor().advance(**LOCK)
        self.assertIsNone(self.journal.load()[0]['candidate'])
        self.executor().rollback(**LOCK)
        self.finish()
        # A subsequent controlled ordinary deployment/promotion makes the current
        # verified template the serving base; rollback itself left old traffic safe.
        self.api.service['traffic'] = [{'type': r.REVISION_TYPE,
            'revision': self.api.service['latestCreatedRevision'], 'percent': 100}]
        self.api.service['generation'] = str(int(self.api.service['generation'])+1)
        self.api.service['etag'] = 'ordinary-promotion'
        self.api.settle()
        self.begin(target_version='3', transaction_id='b123456789ab', replace_terminal=True)
        refs = self.journal.load()[0]['retained_revisions']
        staged = next(ref for ref in refs if ref['bundle_version'] == '2')
        self.assertEqual(staged['revision'], self.api.short+'-sb-a123456789ab')
        self.assertFalse(staged['verified_active'])

    def test_no_mutation_after_near_capacity_initial_journal_rejection(self):
        self.api.annotations['other'] = 'x'*15442
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        with self.assertRaises(r.RolloutError):
            self.begin()
        self.assertEqual(self.api.patches, [])
        self.assertEqual(set(self.api.annotations), {'other'})

    def test_physical_reservation_covers_all_checkpoints_and_releases_at_terminal(self):
        self.begin()
        record = self.journal.load()[0]
        reservation = r.reservation_bytes(record)
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        remaining = self.journal.LIMIT - reservation - len(self.journal.key) - len('other') - 1
        self.api.annotations['other'] = 'x'*remaining
        self.journal.save(record, None)
        for _ in range(10):
            before = self.journal.load()[0]
            self.assertEqual(len(self.api.annotations[self.journal.key]), reservation)
            result = self.executor().advance(**LOCK)
            if result['status'] == 'complete':
                break
        self.assertEqual(result['status'], 'complete')
        self.assertLess(len(self.api.annotations[self.journal.key]), reservation)
        self.assertEqual(self.api.annotations['other'], 'x'*remaining)

    def test_annotation_count_checked_after_inserting_journal(self):
        self.api.annotations = {f'other-{index}': 'x' for index in range(100)}
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        with self.assertRaises(r.RolloutError):
            self.begin()
        self.assertEqual(len(self.api.annotations), 100)
        self.assertEqual(self.api.patches, [])

    def test_later_rollback_capacity_refusal_leaves_current_traffic_and_journal_intact(self):
        self.journal = r.SecretManagerJournal(self.api, self.api.profile, self.api.preview)
        self.begin()
        self.finish()
        before, _ = self.journal.load()
        self.api.annotations['other'] = 'x'*14000
        patch_count = len(self.api.patches)
        traffic = copy.deepcopy(self.api.service['traffic'])
        with self.assertRaises(r.RolloutError):
            self.executor().rollback(**LOCK)
        self.assertEqual(self.journal.load()[0], before)
        self.assertEqual(len(self.api.patches), patch_count)
        self.assertEqual(self.api.service['traffic'], traffic)

    def test_accepted_operation_failure_is_visible_without_resource_mutation(self):
        self.begin()
        delegate = self.api
        operation = self.api.full.rsplit('/services/',1)[0]+'/operations/failed-operation'
        def failed_operation(request):
            if request['api'] == 'cloud-run-v2' and request['method'] == 'PATCH':
                return {'status':200,'body':{'name':operation}}
            if request['path'] == '/v2/'+operation:
                self.assertEqual(request['params'], {'fields':'name,done,error(code)'})
                return {'status':200,'body':{'name':operation,'done':True,'error':{'code':13}}}
            return delegate(request)
        executor = r.RolloutExecutor(failed_operation,self.journal,self.lifecycle)
        executor.advance(**LOCK)
        self.assertEqual(executor.advance(**LOCK)['status'], 'operation_failed')
        # A compensating conditional restore is still available without guessing
        # that the unobserved original write can never arrive.
        self.assertEqual(self.executor().rollback(**LOCK)['status'], 'rollback_prepared')
        self.assertEqual(self.finish()['status'], 'rolled_back')


if __name__ == '__main__':
    unittest.main()
