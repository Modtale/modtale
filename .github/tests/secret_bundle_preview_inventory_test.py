"""Synthetic transport tests only. These never create HTTP/auth clients."""
import copy
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parents[1] / 'scripts'))
import secret_bundle_preview_inventory as inv
import secret_bundle_preview_plan as plan

HEAD = 'a' * 40
BOUNDARY = 'branch-preview'


def config(boundary=BOUNDARY, component='backend', version='1'):
    container = {'name': 'app', 'env': [{'name': 'NONSECRET_CONFIG'}]}
    result = {'serviceAccount': inv.RUNTIME_ACCOUNTS[boundary], 'containers': [container]}
    if component == 'backend':
        container['volumeMounts'] = [{'name': 'bundle', 'mountPath': '/app/secrets/bundles'}]
        result['volumes'] = [{'name': 'bundle', 'secret': {'secret': inv.TARGETS[boundary][1],
                             'items': [{'path': boundary + '.json', 'version': version}]}}]
    return result


class FakeAPI:
    def __init__(self, boundary=BOUNDARY):
        self.boundary = boundary
        self.root = f'projects/{inv.TARGETS[boundary][0]}/locations/us-central1'
        self.services = {}
        self.revisions = {}
        self.requests = []
        self.calls = {}
        self.hook = lambda request, body, call: body
        self.status = 200
        self.github_pages = [[]]
        self.service_pages = None
        self.revision_pages = None
        self.ownership_pages = None

    def add(self, preview='alpha', component='backend', version='1'):
        short = plan.service_name(self.boundary, preview, component)
        full = self.root + '/services/' + short
        revision = short + '-00001-abc'
        labels = {'app': 'modtale', 'environment': self.boundary, 'component': component}
        if self.boundary == BOUNDARY:
            labels['branch'] = preview
        cfg = config(self.boundary, component, version)
        service = {'name': full, 'uid': short + '-uid', 'etag': 'etag-1', 'generation': '1',
                   'observedGeneration': '1', 'terminalCondition': {'state': 'CONDITION_SUCCEEDED'},
                   'latestCreatedRevision': revision, 'latestReadyRevision': revision,
                   'labels': labels, 'template': copy.deepcopy(cfg)}
        self.services[full] = service
        self.revisions[full] = [dict(copy.deepcopy(cfg), name=revision, uid=revision + '-uid', service=full)]
        return service

    def proof(self):
        result = []
        for full, revisions in self.revisions.items():
            short = full.rsplit('/', 1)[-1]
            identity = inv._identity(self.boundary, short)
            if identity is None:
                continue
            preview, component = identity
            if component != 'backend':
                continue
            for revision in revisions:
                volumes = revision.get('volumes', [])
                if not volumes:
                    continue
                version = volumes[0]['secret']['items'][0]['version']
                result.append({'service': short, 'service_uid': self.services[full]['uid'],
                               'revision': revision['name'], 'revision_uid': revision['uid'],
                               'profile': self.boundary, 'preview_id': preview, 'bundle_version': version,
                               'verified_active': True})
        return result

    def __call__(self, request):
        self.requests.append(copy.deepcopy(request))
        key = (request['api'], request['path'], str(request.get('params', {}).get('pageToken')),
               str(request.get('params', {}).get('continue')), str(request.get('params', {}).get('labelSelector')))
        self.calls[key] = self.calls.get(key, 0) + 1
        if self.status != 200:
            return {'status': self.status, 'body': {}}
        if request['api'] == 'github':
            assert request['origin'] == 'https://api.github.com'
            assert request['method'] == 'POST' and request['path'] == '/graphql'
            cursor = request['variables']['cursor']
            index = 0 if cursor is None else int(cursor[1:])
            connection = 'refs' if request['query'] == inv.BRANCH_QUERY else 'pullRequests'
            body = {'data': {'repository': {'nameWithOwner': plan.REPOSITORY,
                    connection: {'nodes': self.github_pages[index], 'pageInfo': {
                        'hasNextPage': index + 1 < len(self.github_pages),
                        'endCursor': 'g' + str(index + 1)}}}}}
        elif request['api'] == 'cloud-run-v1-ownership':
            assert request['origin'] == inv.OWNERSHIP_ORIGIN
            assert request['params']['fields'] == inv.OWNERSHIP_FIELDS
            assert request['path'] == '/apis/serving.knative.dev/v1/namespaces/' + inv.TARGETS[self.boundary][0] + '/services'
            required = dict(pair.split('=', 1) for pair in request['params']['labelSelector'].split(','))
            matches = [{'metadata': {'name': full.rsplit('/', 1)[-1], 'uid': service['uid']}}
                       for full, service in self.services.items()
                       if all(service.get('labels', {}).get(key) == value for key, value in required.items())]
            pages = self.ownership_pages if self.ownership_pages is not None else [matches]
            index = int(request['params'].get('continue', '0'))
            body = {'items': pages[index], 'metadata': {}}
            if index + 1 < len(pages):
                body['metadata']['continue'] = str(index + 1)
        else:
            assert request['api'] == 'cloud-run-v2' and request['method'] == 'GET'
            assert request['origin'] == inv.CLOUD_RUN_ORIGIN
            assert request['params']['fields'] in (inv.SERVICE_FIELDS, inv.SERVICE_LIST_FIELDS, inv.REVISION_LIST_FIELDS, 'name')
            path = request['path']
            index = int(request['params'].get('pageToken', '0'))
            if path == '/v2/' + self.root + '/services':
                pages = self.service_pages if self.service_pages is not None else [[{'name': name} for name in self.services]]
                body = {'services': pages[index]}
                if index + 1 < len(pages):
                    body['nextPageToken'] = str(index + 1)
            elif path.endswith('/revisions'):
                full = path[len('/v2/'):-len('/revisions')]
                pages = self.revision_pages if self.revision_pages is not None else [self.revisions[full]]
                body = {'revisions': pages[index]}
                if index + 1 < len(pages):
                    body['nextPageToken'] = str(index + 1)
            elif path[len('/v2/'):] in self.services:
                service = self.services[path[len('/v2/'):]]
                body = {'name': service['name']} if request['params']['fields'] == 'name' else {k: v for k, v in service.items() if k != 'labels'}
            else:
                return {'status': 404, 'body': {}}
        return {'status': 200, 'body': self.hook(request, copy.deepcopy(body), self.calls[key])}


class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.api = FakeAPI()
        self.service = self.api.add()
        self.adapter = inv.PreviewInventory(self.api)

    def inventory(self, **kwargs):
        if 'bundle_mode_provenance' not in kwargs:
            kwargs['bundle_mode_provenance'] = self.api.proof()
        kwargs.setdefault('rollback_manifest', {'complete': True, 'services': {}})
        return self.adapter.cloud_run_inventory(self.api.boundary, **kwargs)

    def assert_blocked(self, output, reason=None):
        self.assertEqual(output['status'], 'initial_migration_required')
        self.assertFalse(output['services']['complete'])
        self.assertFalse(output['references']['complete'])
        if reason:
            self.assertIn(reason, [row['reason'] for row in output['initial_migration_required']])

    def add_unrelated(self):
        full = self.api.root + '/services/other-app'
        revision = 'other-app-00001-abc'
        plain = {'containers': [{'name': 'plain', 'env': [{'name': 'PUBLIC_CONFIG'}]}]}
        self.api.services[full] = {'name': full, 'uid': 'other-service-uid', 'etag': 'other-etag',
            'generation': '1', 'observedGeneration': '1', 'terminalCondition': {'state': 'CONDITION_SUCCEEDED'},
            'latestCreatedRevision': revision, 'latestReadyRevision': revision, 'template': copy.deepcopy(plain)}
        self.api.revisions[full] = [dict(copy.deepcopy(plain), name=revision, uid='other-revision-uid', service=full)]
        return self.api.services[full], self.api.revisions[full][0]

    def test_ready_output_matches_pure_planner_contract(self):
        output = self.inventory()
        self.assertEqual(output['status'], 'ready')
        plan._services(BOUNDARY, output['services'])
        self.assertEqual(plan._references(BOUNDARY, output['references']), {'1'})
        action = plan.plan_repin_after_publication(BOUNDARY, {'version': '2', 'changed': True, 'previousVersion': '1'},
            lifecycle={'repository': plan.REPOSITORY, 'complete': True, 'items': [{'name': 'alpha', 'head_sha': HEAD}]},
            services=output['services'], references=output['references'], enabled_versions=['1', '2'], rollback_versions=[],
            lock_held=True, lock_group=plan.LOCK_GROUP)
        self.assertEqual(action['actions'][0]['action'], 'configuration_only_repin')
        self.assertFalse(action['automatic_retirement'])
        self.assertEqual(output['integration_status'], 'synthetic_contract_requires_full_transport_verification')

    def test_verified_bundle_mode_can_keep_legacy_serving_and_rollback_revision(self):
        old=copy.deepcopy(self.api.revisions[self.service['name']][0])
        old['name']='modtale-backend-alpha-00000-old'
        old['uid']='legacy-revision-uid'
        old.pop('volumes')
        old['containers'][0].pop('volumeMounts')
        old['containers'][0]['env']=[{'name':'R2_SECRET_KEY','valueSource':{'secretKeyRef':{'secret':'legacy','version':'latest'}}}]
        self.api.revisions[self.service['name']].append(old)
        self.service['trafficStatuses']=[{'type':'TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION','revision':old['name'],'percent':100}]
        output=self.inventory(rollback_manifest={'complete':True,'services':{'modtale-backend-alpha':[old['name']]}})
        self.assertEqual(output['status'],'ready')
        self.assertEqual(output['legacy_revisions_to_preserve'],[{'service':'modtale-backend-alpha','revision':old['name']}])
        self.assertEqual(output['legacy_source_policy'],'retain_all_original_secrets_and_tokens')
        self.assertEqual(output['reference_scope'],'fixed_project_cloud_run_services_only')
        self.assertTrue(output['references']['complete'])

    def test_unmanaged_current_or_historical_bundle_consumer_is_never_ignored(self):
        for historical in (False, True):
            with self.subTest(historical=historical):
                service, revision = self.add_unrelated()
                target = revision if historical else service['template']
                target.update(config())
                with self.assertRaisesRegex(inv.InventoryError, 'unmanaged service references'):
                    self.inventory()

    def test_unrelated_service_without_bundle_references_is_allowed(self):
        service, revision = self.add_unrelated()
        self.assertNotIn('labels', service)
        self.assertNotIn('serviceAccount', revision)
        self.assertEqual(self.inventory()['status'], 'ready')

    def test_unmanaged_missing_identity_or_configuration_fails_closed(self):
        for key in ('uid', 'etag', 'generation', 'template'):
            with self.subTest(key=key):
                service, _ = self.add_unrelated()
                service.pop(key)
                with self.assertRaises(inv.InventoryError):
                    self.inventory()
        for configuration in ({}, {'containers': []}, {'containers': None}, {'containers': [None]}):
            service, _ = self.add_unrelated()
            service['template'] = configuration
            with self.assertRaises(inv.InventoryError):
                self.inventory()

    def test_unmanaged_revision_requires_scoped_identity_uid_and_configuration(self):
        for key in ('name', 'uid', 'service', 'containers'):
            with self.subTest(key=key):
                _, revision = self.add_unrelated()
                revision.pop(key)
                with self.assertRaises(inv.InventoryError):
                    self.inventory()
        for key, value in (('uid', None), ('uid', ''), ('uid', '\ud800'),
                           ('service', 'projects/foreign/locations/us-central1/services/other-app'),
                           ('service', 'another-app'), ('name', 'another-app-00001-abc'),
                           ('name', 'projects/foreign/locations/us-central1/services/other-app/revisions/other-app-00001-abc')):
            with self.subTest(key=key):
                _, revision = self.add_unrelated()
                revision[key] = value
                with self.assertRaises(inv.InventoryError):
                    self.inventory()

    def test_unmanaged_recursive_unmasked_or_malformed_fields_are_rejected(self):
        mutations = (
            lambda c: c.update(annotations={'SYNTHETIC-PRIVATE': 'unexpected'}),
            lambda c: c['containers'][0].update(image='SYNTHETIC-PRIVATE'),
            lambda c: c['containers'][0]['env'][0].update(value='SYNTHETIC-PRIVATE'),
            lambda c: c['containers'][0]['env'][0].update(valueSource=None),
            lambda c: c['containers'][0]['env'][0].update(valueSource={'secretKeyRef': {'secret': 'legacy'}}),
            lambda c: c['containers'][0]['env'][0].update(valueSource={'secretKeyRef': {'secret': 'legacy', 'version': 'latest', 'value': 'SYNTHETIC-PRIVATE'}}),
            lambda c: c.update(volumes=[None]),
            lambda c: c.update(volumes=[{'name': 'v', 'secret': None}]),
            lambda c: c.update(volumes=[{'name': 'v', 'secret': {'secret': 'legacy', 'items': [{'version': '1'}]}}]),
            lambda c: c.update(volumes=[{'name': 'v', 'secret': {'secret': 'legacy', 'items': [{'path': 'x', 'version': '1', 'payload': 'SYNTHETIC-PRIVATE'}]}}]),
            lambda c: c['containers'][0].update(volumeMounts=[{'name': 'missing'}]),
        )
        for mutation in mutations:
            for historical in (False, True):
                service, revision = self.add_unrelated()
                mutation(revision if historical else service['template'])
                with self.assertRaises(inv.InventoryError) as caught:
                    self.inventory()
                self.assertNotIn('SYNTHETIC-PRIVATE', str(caught.exception))

    def test_unmanaged_top_level_diagnostics_are_rejected(self):
        service, _ = self.add_unrelated()
        service['terminalCondition']['message'] = 'SYNTHETIC-PRIVATE'
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_unmanaged_missing_or_duplicate_revision_inventory_is_incomplete(self):
        service, revision = self.add_unrelated()
        self.api.revisions[service['name']] = []
        with self.assertRaises(inv.InventoryError):
            self.inventory()
        service, revision = self.add_unrelated()
        self.api.revisions[service['name']].append(copy.deepcopy(revision))
        with self.assertRaises(inv.InventoryError):
            self.inventory()
        self.api.revisions[service['name']][-1]['name'] = 'other-app-00002-abc'
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_unmanaged_sidecars_and_other_runtime_identities_are_allowed(self):
        service, revision = self.add_unrelated()
        for value in (service['template'], revision):
            value['serviceAccount'] = 'unrelated-runtime@example.invalid'
            value['containers'].append({'name': 'sidecar', 'env': [{'name': 'OTHER_SECRET', 'valueSource': {
                'secretKeyRef': {'secret': 'projects/other-project/secrets/other-secret', 'version': 'latest'}}}]})
        self.assertEqual(self.inventory()['status'], 'ready')

    def test_unmanaged_metadata_drift_invalidates_inventory(self):
        service, _ = self.add_unrelated()
        self.api.hook = lambda r, b, n: dict(b, uid='recreated') if b.get('name') == service['name'] and n > 1 else b
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_service_names_cannot_inject_request_paths(self):
        self.api.service_pages = [[{'name': self.api.root + '/services/..'}]]
        with self.assertRaises(inv.InventoryError):
            self.inventory()
        self.assertEqual(len(self.api.requests), 1)

    def test_transport_requests_only_fixed_metadata_fields(self):
        self.inventory()
        raw = json.dumps(self.api.requests)
        for forbidden in ('env(value)', 'annotations', 'message', 'logUri', 'access_token', 'Authorization', 'secretmanager', 'image', 'args', 'command'):
            self.assertNotIn(forbidden, raw)
        for request in self.api.requests:
            self.assertEqual(request['method'], 'GET')
            self.assertIn('fields', request['params'])
            self.assertNotIn('*', request['params']['fields'])
            self.assertIn(inv.TARGETS[BOUNDARY][0], request['path'])

    def test_missing_mode_provenance_is_never_ready(self):
        output = self.inventory(bundle_mode_provenance=[])
        self.assert_blocked(output, 'active_bundle_mode_not_verified')
        self.assertEqual(output['references']['items'][0]['version'], '1')
        with self.assertRaises(plan.PlanError):
            plan._services(BOUNDARY, output['services'])

    def test_mode_provenance_must_match_immutable_identity_and_pin(self):
        for key, value in (('service_uid', 'recreated'), ('revision_uid', 'recreated'), ('bundle_version', '2'),
                           ('preview_id', 'other'), ('profile', 'pr-preview'), ('verified_active', False)):
            with self.subTest(key=key):
                evidence = self.api.proof()
                evidence[0][key] = value
                with self.assertRaises(inv.InventoryError):
                    self.inventory(bundle_mode_provenance=evidence)

    def test_duplicate_or_unknown_provenance_fails(self):
        evidence = self.api.proof()
        with self.assertRaises(inv.InventoryError):
            self.inventory(bundle_mode_provenance=evidence + evidence)
        evidence[0]['revision'] = 'modtale-backend-alpha-99999-abc'
        with self.assertRaises(inv.InventoryError):
            self.inventory(bundle_mode_provenance=evidence)

    def test_ownership_is_never_inferred_from_service_name(self):
        original = copy.deepcopy(self.service)
        for label in ('app', 'environment', 'component', 'branch'):
            with self.subTest(label=label):
                self.service.update(copy.deepcopy(original))
                self.service['labels'].pop(label)
                output = self.inventory()
                self.assert_blocked(output, 'managed_ownership_not_verified')
                backend = next(row for row in output['services']['items'] if row['component'] == 'backend')
                self.assertEqual(backend['observation'], 'present')
        self.service.update(original)

    def test_wrong_runtime_service_account_blocks_ready_rows(self):
        self.service['template']['serviceAccount'] = 'unrelated@example.invalid'
        self.assert_blocked(self.inventory(), 'managed_ownership_not_verified')

    def test_wrong_retained_revision_runtime_blocks_ready_rows(self):
        self.api.revisions[self.service['name']][0]['serviceAccount'] = 'unrelated@example.invalid'
        self.assert_blocked(self.inventory(), 'revision_runtime_identity_not_verified')

    def test_legacy_unpinned_environment_binding_requires_initial_migration(self):
        for value in (self.service['template'], self.api.revisions[self.service['name']][0]):
            value.pop('volumes')
            value['containers'][0].pop('volumeMounts')
            value['containers'][0]['env'] = [{'name': 'R2_SECRET_KEY', 'valueSource': {'secretKeyRef': {'secret': 'legacy', 'version': 'latest'}}}]
        output = self.inventory(bundle_mode_provenance=[])
        self.assert_blocked(output, 'legacy_or_missing_bundle_binding')
        self.assertIsNone(output['services']['items'][0]['bundle_version'])
        self.assertEqual(output['references']['items'], [])

    def test_coexisting_legacy_refs_are_not_considered_fully_migrated(self):
        self.service['template']['containers'][0]['env'].append({'name': 'R2_SECRET_KEY', 'valueSource': {'secretKeyRef': {'secret': 'legacy', 'version': 'latest'}}})
        self.assert_blocked(self.inventory(), 'legacy_or_missing_bundle_binding')

    def test_latest_or_alias_bundle_versions_fail_closed(self):
        for version in ('latest', 'stable', '0', '01', 1, None):
            with self.subTest(version=version):
                self.service['template']['volumes'][0]['secret']['items'][0]['version'] = version
                with self.assertRaises(inv.InventoryError):
                    self.inventory(bundle_mode_provenance=[])

    def test_bundle_environment_variable_delivery_fails(self):
        self.service['template']['containers'][0]['env'] = [{'name': 'BUNDLE', 'valueSource': {'secretKeyRef': {'secret': inv.TARGETS[BOUNDARY][1], 'version': '1'}}}]
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_mount_requires_exact_file_and_volume_link(self):
        original = copy.deepcopy(self.service['template'])
        for mutator in (
            lambda c: c['containers'][0]['volumeMounts'][0].update(name='unlinked'),
            lambda c: c['containers'][0]['volumeMounts'][0].update(mountPath='/tmp/bundles'),
            lambda c: c['containers'][0]['volumeMounts'][0].update(subPath='nested'),
            lambda c: c['volumes'][0]['secret']['items'][0].update(path='../branch-preview.json'),
            lambda c: c['volumes'][0]['secret'].update(items=[]),
            lambda c: c['volumes'][0]['secret']['items'].append({'path': 'other', 'version': '1'}),
        ):
            self.service['template'] = copy.deepcopy(original)
            mutator(self.service['template'])
            with self.assertRaises(inv.InventoryError):
                self.inventory()

    def test_ancestor_descendant_and_duplicate_mounts_fail(self):
        original = copy.deepcopy(self.service['template'])
        for path in ('/app', '/app/secrets/bundles/branch-preview.json', '/app/secrets/bundles', '/app//shadow', '/app/../shadow'):
            with self.subTest(path=path):
                self.service['template'] = copy.deepcopy(original)
                self.service['template']['containers'][0]['volumeMounts'].append({'name': 'shadow', 'mountPath': path})
                self.service['template']['volumes'].append({'name': 'shadow'})
                with self.assertRaises(inv.InventoryError):
                    self.inventory()

    def test_cross_project_and_wrong_boundary_secret_refs_fail(self):
        for secret in ('projects/unrelated/secrets/' + inv.TARGETS[BOUNDARY][1], inv.TARGETS['pr-preview'][1]):
            self.service['template']['volumes'][0]['secret']['secret'] = secret
            with self.assertRaises(inv.InventoryError):
                self.inventory()

    def test_sidecars_are_explicitly_unsupported(self):
        self.service['template']['containers'].append({'name': 'sidecar'})
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_frontend_receives_no_bundle(self):
        frontend = self.api.add(component='frontend')
        self.assertEqual(self.inventory()['status'], 'ready')
        frontend['template'] = config()
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_env_value_and_unknown_diagnostic_fields_are_rejected_without_leak(self):
        self.service['template']['containers'][0]['env'][0]['value'] = 'SYNTHETIC-PRIVATE'
        try:
            self.inventory()
            self.fail('Expected rejection')
        except inv.InventoryError as error:
            self.assertNotIn('SYNTHETIC-PRIVATE', str(error))
            self.assertIsNone(error.__cause__)

    def test_transport_exceptions_and_statuses_never_become_absence(self):
        for status in (400, 401, 403, 429, 500, 503):
            self.api.status = status
            with self.assertRaises(inv.InventoryError):
                self.adapter.observe_service_absence(BOUNDARY, 'alpha', 'backend')
        def timeout(request):
            raise TimeoutError('SYNTHETIC-PRIVATE')
        with self.assertRaises(inv.InventoryError) as caught:
            inv.PreviewInventory(timeout).observe_service_absence(BOUNDARY, 'alpha', 'backend')
        self.assertNotIn('SYNTHETIC-PRIVATE', str(caught.exception))
        self.assertIsNone(caught.exception.__cause__)

    def test_only_explicit_404_is_absence_for_dedicated_get(self):
        self.assertEqual(self.adapter.observe_service_absence(BOUNDARY, 'missing', 'backend')['observation'], 'not_found')
        self.assertEqual(self.adapter.observe_service_absence(BOUNDARY, 'alpha', 'backend')['observation'], 'present')

    def test_complete_empty_inventory_can_establish_both_services_absent(self):
        self.api.services.clear()
        output = self.inventory(requested_preview_ids=['alpha'], bundle_mode_provenance=[])
        self.assertEqual(output['status'], 'ready')
        self.assertEqual([row['observation'] for row in output['services']['items']], ['not_found', 'not_found'])

    def test_empty_pages_with_cursors_do_not_imply_absence(self):
        self.api.service_pages = [[], [{'name': self.service['name']}]]
        self.api.revision_pages = [[], self.api.revisions[self.service['name']]]
        self.assertEqual(self.inventory()['status'], 'ready')
        self.assertTrue(any(request['params'].get('pageToken') == '1' for request in self.api.requests))

    def test_unreachable_is_requested_and_never_masked_away(self):
        self.assertIn('unreachable', inv.SERVICE_LIST_FIELDS)
        self.api.hook = lambda r, b, n: dict(b, unreachable=['us-central1']) if 'services' in b else b
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_malformed_cursor_and_repeated_cursor_fail(self):
        for cursor in (None, False, 0, [], 'again'):
            with self.subTest(cursor=cursor):
                self.api.hook = lambda r, b, n: dict(b, nextPageToken=cursor) if 'services' in b else b
                with self.assertRaises(inv.InventoryError):
                    self.inventory()

    def test_duplicate_services_or_revisions_fail(self):
        self.api.service_pages = [[{'name': self.service['name']}, {'name': self.service['name']}]]
        with self.assertRaises(inv.InventoryError):
            self.inventory()
        self.api.service_pages = None
        self.api.revisions[self.service['name']] *= 2
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_hard_limits_fail_instead_of_truncating(self):
        with patch.object(inv, 'MAX_ITEMS', 1):
            with self.assertRaises(inv.InventoryError):
                self.inventory()
        with patch.object(inv, 'MAX_PAGES', 1):
            self.api.service_pages = [[], [{'name': self.service['name']}]]
            with self.assertRaises(inv.InventoryError):
                self.inventory()

    def test_observed_service_and_ownership_drift_fail(self):
        self.api.hook = lambda r, b, n: dict(b, etag='changed') if 'etag' in b and n > 1 else b
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_service_list_churn_fails(self):
        self.api.hook = lambda r, b, n: {'services': []} if 'services' in b and n > 1 else b
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_missing_referenced_revision_fails(self):
        self.api.revisions[self.service['name']].clear()
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_unknown_rollback_service_or_revision_fails(self):
        for rollback in ({'modtale-backend-unknown': []}, {'modtale-backend-alpha': ['modtale-backend-alpha-99999-abc']}):
            with self.assertRaises(inv.InventoryError):
                self.inventory(rollback_manifest={'complete': True, 'services': rollback})

    def test_failed_rollout_preserves_observed_desired_tagged_and_rollback_pins(self):
        full = self.service['name']
        revisions = self.api.revisions[full]
        for number in (2, 3, 4):
            revision = copy.deepcopy(revisions[0])
            revision['name'] = f'modtale-backend-alpha-0000{number}-abc'
            revision['uid'] = revision['name'] + '-uid'
            revision['volumes'][0]['secret']['items'][0]['version'] = str(number)
            revisions.append(revision)
        self.service.update(latestCreatedRevision=revisions[-1]['name'], generation='2',
            terminalCondition={'state': 'CONDITION_FAILED'}, template=config(version='4'),
            trafficStatuses=[{'type': 'TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION', 'revision': revisions[0]['name'], 'percent': 100},
                             {'type': 'TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION', 'revision': revisions[1]['name'], 'tag': 'review'}],
            traffic=[{'type': 'TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION', 'revision': revisions[-1]['name'], 'percent': 100}])
        output = self.inventory(rollback_manifest={'complete': True, 'services': {'modtale-backend-alpha': [revisions[2]['name']]}})
        self.assertEqual(output['services']['items'][0]['rollout_state'], 'failed')
        self.assertEqual(plan._references(BOUNDARY, output['references']), {'1', '2', '3', '4'})
        refs = {row['version']: row for row in output['references']['items']}
        self.assertTrue(refs['1']['rollback'])
        self.assertTrue(refs['2']['tagged'])
        self.assertTrue(refs['3']['rollback'])

    def test_pending_rollout_never_claims_ready(self):
        self.service['reconciling'] = True
        self.assertEqual(self.inventory()['services']['items'][0]['rollout_state'], 'pending')

    def test_default_omissions_are_normalized(self):
        self.assertNotIn('reconciling', self.service)
        self.assertNotIn('traffic', self.service)
        output = self.inventory()
        self.assertEqual(output['services']['items'][0]['rollout_state'], 'ready')
        self.assertTrue(output['references']['items'][0]['serving'])

    def test_unknown_readiness_or_scope_fails(self):
        self.service['terminalCondition'] = {'state': 'UNRECOGNIZED'}
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_pr_boundary_uses_dedicated_project_and_profile(self):
        self.api = FakeAPI('pr-preview')
        self.api.add('42')
        self.adapter = inv.PreviewInventory(self.api)
        output = self.inventory()
        self.assertEqual(output['status'], 'ready')
        self.assertEqual(output['services']['project'], 'modtale-pr-preview')
        self.assertEqual(output['references']['items'][0]['preview_id'], '42')

    def test_reserved_identifiers_fail_before_transport(self):
        for name in ('dev', 'main', 'develop', '../alpha'):
            with self.assertRaises(inv.InventoryError):
                self.inventory(requested_preview_ids=[name])
        self.assertEqual(self.api.requests, [])

    def test_github_branch_pagination_including_empty_page(self):
        self.api.github_pages = [[], [{'name': 'alpha', 'target': {'oid': HEAD}}]]
        output = self.adapter.github_lifecycle(BOUNDARY)
        self.assertEqual(output['items'], [{'name': 'alpha', 'head_sha': HEAD}])
        self.assertEqual([r['variables']['cursor'] for r in self.api.requests], [None, 'g1'])
        for request in self.api.requests:
            self.assertIn('owner: "Modtale", name: "modtale"', request['query'])
            self.assertNotIn('title', request['query'])
            self.assertNotIn('body', request['query'])

    def test_github_pr_states_and_heads(self):
        self.api.github_pages = [[{'number': n, 'state': state, 'headRefOid': HEAD,
                                  'headRepository': {'nameWithOwner': 'contributor/modtale'}}
                                 for n, state in enumerate(('OPEN', 'CLOSED', 'MERGED'), 1)]]
        output = self.adapter.github_lifecycle('pr-preview')
        self.assertEqual([row['state'] for row in output['items']], ['open', 'closed', 'closed'])

    def test_github_partial_errors_null_repository_and_wrong_scope_fail(self):
        for result in ({'data': {'repository': None}}, {'data': {'repository': {'nameWithOwner': 'other/repo', 'refs': {}}}},
                       {'data': {}, 'errors': [{'message': 'SYNTHETIC-PRIVATE'}]}):
            self.api.hook = lambda r, b, n: result
            with self.assertRaises(inv.InventoryError) as caught:
                self.adapter.github_lifecycle(BOUNDARY)
            self.assertNotIn('SYNTHETIC-PRIVATE', str(caught.exception))

    def test_github_collision_and_duplicate_branch_are_ambiguous(self):
        for names in (('feature/a', 'feature-a'), ('alpha', 'alpha')):
            self.api.github_pages = [[{'name': name, 'target': {'oid': HEAD}} for name in names]]
            with self.assertRaises(inv.InventoryError):
                self.adapter.github_lifecycle(BOUNDARY)

    def test_github_missing_head_repository_fails_closed(self):
        self.api.github_pages = [[{'number': 42, 'state': 'CLOSED', 'headRefOid': HEAD, 'headRepository': None}]]
        with self.assertRaises(inv.InventoryError):
            self.adapter.github_lifecycle('pr-preview')

    def test_github_hard_limit_and_cursor_repetition(self):
        self.api.github_pages = [[{'name': 'alpha', 'target': {'oid': HEAD}}, {'name': 'beta', 'target': {'oid': HEAD}}]]
        with patch.object(inv, 'MAX_ITEMS', 1):
            with self.assertRaises(inv.InventoryError):
                self.adapter.github_lifecycle(BOUNDARY)
        self.api.github_pages = [[], []]
        def repeat(request, body, call):
            body['data']['repository']['refs']['pageInfo'] = {'hasNextPage': True, 'endCursor': 'g1'}
            return body
        self.api.hook = repeat
        with self.assertRaises(inv.InventoryError):
            self.adapter.github_lifecycle(BOUNDARY)

    def test_missing_or_incomplete_rollback_manifest_is_not_ready(self):
        for manifest in (None, {'complete': False, 'services': {}}):
            self.assert_blocked(self.inventory(rollback_manifest=manifest), 'rollback_inventory_not_verified')

    def test_ownership_proof_uses_exact_selector_without_returning_labels(self):
        self.inventory()
        ownership = [request for request in self.api.requests if request['api'] == 'cloud-run-v1-ownership']
        self.assertEqual(len(ownership), 2)
        self.assertEqual(ownership[0]['params']['labelSelector'],
                         'app=modtale,environment=branch-preview,component=backend,branch=alpha')
        self.assertEqual(ownership[0]['params']['fields'], 'items(metadata(name,uid)),metadata(continue),unreachable')
        self.assertNotIn('labels', inv.SERVICE_FIELDS)

    def test_ownership_pagination_continues_through_empty_page(self):
        self.api.ownership_pages = [[], [{'metadata': {'name': 'modtale-backend-alpha', 'uid': self.service['uid']}}]]
        self.assertEqual(self.inventory()['status'], 'ready')
        self.assertTrue(any(request['params'].get('continue') == '1' for request in self.api.requests))

    def test_ownership_uid_mismatch_blocks_similarly_named_recreated_service(self):
        self.api.ownership_pages = [[{'metadata': {'name': 'modtale-backend-alpha', 'uid': 'different-uid'}}]]
        output = self.inventory()
        self.assert_blocked(output, 'managed_ownership_not_verified')
        self.assertEqual(output['services']['items'][0]['observation'], 'present')

    def test_ownership_changes_after_read_are_rejected(self):
        def drift(request, body, call):
            return {'items': [], 'metadata': {}} if request['api'] == 'cloud-run-v1-ownership' and call > 1 else body
        self.api.hook = drift
        with self.assertRaises(inv.InventoryError):
            self.inventory()

    def test_ownership_unreachable_duplicate_and_malformed_cursor_fail(self):
        original = {'metadata': {'name': 'modtale-backend-alpha', 'uid': self.service['uid']}}
        for body in ({'items': [], 'unreachable': ['us-central1']}, {'items': [original, original]},
                     {'items': [], 'metadata': {'continue': None}}):
            self.api.hook = lambda r, b, n: body if r['api'] == 'cloud-run-v1-ownership' else b
            with self.assertRaises(inv.InventoryError):
                self.inventory()

    def test_unsupported_ownership_api_has_no_fallback(self):
        def unsupported(request):
            if request['api'] == 'cloud-run-v1-ownership':
                return {'status': 400, 'body': {}}
            return self.api(request)
        with self.assertRaises(inv.InventoryError):
            inv.PreviewInventory(unsupported).cloud_run_inventory(BOUNDARY,
                bundle_mode_provenance=self.api.proof(), rollback_manifest={'complete': True, 'services': {}})
        self.assertEqual(len(self.api.requests), 2)

    def test_existing_pr_without_labels_remains_migration_required(self):
        self.api = FakeAPI('pr-preview')
        service = self.api.add('42')
        service.pop('labels')
        self.adapter = inv.PreviewInventory(self.api)
        self.assert_blocked(self.inventory(), 'managed_ownership_not_verified')

    def test_refuses_unmasked_full_service_or_v1_config_shape(self):
        for field in ('labels', 'annotations', 'spec', 'status', 'conditions'):
            self.api.hook = lambda r, b, n: dict(b, **{field: {'SYNTHETIC-PRIVATE': 'unused'}}) if 'etag' in b else b
            with self.assertRaises(inv.InventoryError) as caught:
                self.inventory()
            self.assertNotIn('SYNTHETIC-PRIVATE', str(caught.exception))

    def test_bounded_output_contains_no_environment_values(self):
        output = json.dumps(self.inventory())
        self.assertNotIn('NONSECRET_CONFIG', output)
        self.assertNotIn('valueSource', output)
        self.assertNotIn('serviceAccount', output)
        self.assertIn('"version": "1"', output)


if __name__ == '__main__':
    unittest.main()
