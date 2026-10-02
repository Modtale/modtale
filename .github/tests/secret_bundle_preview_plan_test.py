import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).parents[1] / 'scripts'))
from secret_bundle import encode
from secret_bundle_store import BundleStore, preview_updates
import secret_bundle_preview_plan as p

HEAD = 'a' * 40
NEXT = 'b' * 40
LOCK = {'lock_held': True, 'lock_group': p.LOCK_GROUP}


def branches(*names):
    return {'repository': p.REPOSITORY, 'complete': True,
            'items': [{'name': name, 'head_sha': HEAD} for name in names]}


def prs(state='open', head=HEAD):
    return {'repository': p.REPOSITORY, 'complete': True,
            'items': [{'number': '42', 'state': state, 'head_sha': head, 'head_repository': 'contributor/modtale'}]}


def service(preview='alpha', component='backend', version='1', state='ready', observation='present', boundary='branch-preview'):
    name = p.service_name(boundary, preview, component)
    return {'preview_id': preview, 'component': component, 'name': name, 'observation': observation,
            'bundle_version': version if component == 'backend' and observation == 'present' else None,
            'revision': name + '-00001-abc' if observation == 'present' else None,
            'rollout_state': state if observation == 'present' else None}


def inventory(*items, boundary='branch-preview'):
    return {'project': p.TARGETS[boundary][0], 'region': p.REGION, 'complete': True, 'items': list(items)}


def references(*versions):
    return {'complete': True, 'items': [
        {'preview_id': 'alpha', 'service': 'modtale-backend-alpha',
         'revision': f'modtale-backend-alpha-0000{i}-abc', 'version': version,
         'serving': i == 0, 'tagged': i == 1, 'rollback': i >= 2}
        for i, version in enumerate(versions)]}


def upsert(preview='alpha', **overrides):
    args = {'raw_branch': preview, 'expected_head': HEAD, 'before': branches('alpha', 'beta'),
            'recheck': branches('alpha', 'beta'), 'base_version': '1', 'credentials_changed': True, **LOCK}
    args.update(overrides)
    return p.plan_credential_upsert('branch-preview', preview, **args)


def cleanup(**overrides):
    args = {'raw_branch': 'alpha', 'expected_head': HEAD, 'before': branches('beta'),
            'recheck': branches('beta'), 'base_version': '2',
            'services': inventory(service(observation='not_found'), service(component='frontend', observation='not_found')),
            'existing_key_names': p.credential_keys('branch-preview', 'alpha'), **LOCK}
    args.update(overrides)
    return p.plan_cleanup('branch-preview', 'alpha', **args)


def repin(**overrides):
    args = {'lifecycle': branches('alpha', 'beta'),
            'services': inventory(service(), service('beta'), service(component='frontend')),
            'references': references('1'), 'enabled_versions': ['1', '2', '3'], 'rollback_versions': [], **LOCK}
    publication = overrides.pop('publication', {'version': '2', 'changed': True, 'previousVersion': '1'})
    args.update(overrides)
    return p.plan_repin_after_publication('branch-preview', publication, **args)


class FakeBackend:
    def __init__(self):
        self.versions = {'1': encode({'schemaVersion': 1, 'boundary': 'branch-preview',
                                     'secrets': {'BRANCH_PREVIEW_MONGODB_URI': 'SYNTHETIC-PRIVATE'}})}
        self.latest = '1'
        self.publishes = 0

    def latest_enabled(self):
        return self.latest

    def read(self, version):
        return self.versions[version]

    def publish(self, raw):
        self.latest = str(int(self.latest) + 1)
        self.versions[self.latest] = raw
        self.publishes += 1
        return self.latest


class PreviewPlanTests(unittest.TestCase):
    def test_full_credential_set_is_one_atomic_patch(self):
        plan = upsert()
        self.assertEqual(len(plan['actions']), 1)
        action = plan['actions'][0]
        self.assertEqual(action['set_keys'], p.credential_keys('branch-preview', 'alpha'))
        self.assertEqual(action['store_calls'], 1)
        self.assertTrue(action['preserve_unlisted_keys'])

    def test_planning_noop_emits_no_store_write(self):
        self.assertEqual(upsert(credentials_changed=False)['actions'], [])

    def test_store_noop_avoids_new_version(self):
        backend = FakeBackend()
        store = BundleStore('branch-preview', backend)
        values = {suffix: 'SYNTHETIC-PRIVATE' for suffix in p.SUFFIXES}
        updates = preview_updates('branch-preview', 'alpha', values)
        store.update(updates, lock_held=True)
        result = store.update(updates, lock_held=True)
        self.assertEqual(result, {'version': '2', 'changed': False})
        self.assertEqual(backend.publishes, 1)

    def test_serialized_writers_preserve_other_preview_credentials(self):
        backend = FakeBackend()
        store = BundleStore('branch-preview', backend)
        outputs = []
        for preview in ('alpha', 'beta'):
            plan = upsert(preview, base_version=backend.latest_enabled())
            p.require_current_base(plan, backend.latest_enabled(), **LOCK)
            credentials = {suffix: f'SYNTHETIC-PRIVATE-{preview}' for suffix in p.SUFFIXES}
            result = store.update(preview_updates('branch-preview', preview, credentials), lock_held=True)
            outputs.extend((plan, result))
        secret_values = store.read('3')['secrets']
        for preview in ('alpha', 'beta'):
            for key in p.credential_keys('branch-preview', preview):
                self.assertEqual(secret_values[key], f'SYNTHETIC-PRIVATE-{preview}')
        self.assertNotIn('SYNTHETIC-PRIVATE', json.dumps(outputs))

    def test_stale_concurrent_plan_is_refused_until_replanned(self):
        plan_a, plan_b = upsert(), upsert('beta')
        p.require_current_base(plan_a, '1', **LOCK)
        with self.assertRaises(p.PlanError):
            p.require_current_base(plan_b, '2', **LOCK)
        p.require_current_base(upsert('beta', base_version='2'), '2', **LOCK)

    def test_publication_failure_preserves_original_snapshot_and_token_metadata(self):
        backend = FakeBackend()
        store = BundleStore('branch-preview', backend)
        old = {suffix: 'SYNTHETIC-PRIVATE-OLD' for suffix in p.SUFFIXES}
        store.update(preview_updates('branch-preview', 'alpha', old), lock_held=True)
        before = backend.read('2')
        plan = upsert(base_version='2')

        def fail_publish(raw):
            raise RuntimeError('Synthetic publication failure')

        backend.publish = fail_publish
        p.require_current_base(plan, backend.latest_enabled(), **LOCK)
        with self.assertRaises(RuntimeError):
            store.update(preview_updates('branch-preview', 'alpha', {suffix: 'SYNTHETIC-PRIVATE-NEW' for suffix in p.SUFFIXES}), lock_held=True)
        self.assertEqual(backend.latest_enabled(), '2')
        self.assertEqual(backend.read('2'), before)
        self.assertEqual(store.read('2')['secrets']['branch-preview-alpha-r2-token-id'], 'SYNTHETIC-PRIVATE-OLD')
        self.assertEqual(plan['token_policy'], 'retain_all_existing_tokens')

    def test_global_lock_must_be_explicit(self):
        for args in ({'lock_held': False}, {'lock_group': 'per-branch-alpha'}, {'lock_held': 1}):
            with self.assertRaises(p.PlanError):
                upsert(**args)
            with self.assertRaises(p.PlanError):
                cleanup(**args)
            with self.assertRaises(p.PlanError):
                repin(**args)

    def test_branch_slug_uses_current_twenty_character_algorithm(self):
        self.assertEqual(p.branch_slug('FEATURE/Some__Branch-Name', HEAD), 'feature-some-branch')
        self.assertEqual(p.branch_slug('!!!', HEAD), HEAD[:7])

    def test_raw_branch_has_exactly_one_slug_owner(self):
        for collision in (branches('feature/a', 'feature-a'), branches('a' * 20 + 'one', 'a' * 20 + 'two')):
            first = collision['items'][0]['name']
            slug = p.branch_slug(first, HEAD)
            with self.assertRaises(p.PlanError):
                upsert(slug, raw_branch=first, before=collision, recheck=collision)

    def test_changed_branch_head_or_deleted_branch_blocks_upsert(self):
        changed = branches('alpha')
        changed['items'][0]['head_sha'] = NEXT
        for latest in (changed, branches()):
            with self.assertRaises(p.PlanError):
                upsert(recheck=latest)

    def test_deleted_recreated_branch_blocks_cleanup(self):
        with self.assertRaises(p.PlanError):
            cleanup(recheck=branches('alpha', 'beta'))

    def test_reserved_and_invalid_ids_are_rejected(self):
        for value in ('main', 'develop', 'dev', '-alpha', 'alpha-', 'a' * 21, 'alpha/branch'):
            with self.assertRaises(p.PlanError):
                p.credential_keys('branch-preview', value)
        for value in ('0', '01', '-1', 'latest', '2' * 21):
            with self.assertRaises(p.PlanError):
                p.credential_keys('pr-preview', value)

    def test_pr_deploy_requires_open_matching_head_on_recheck(self):
        args = {'raw_branch': None, 'expected_head': HEAD, 'before': prs(), 'recheck': prs(),
                'base_version': '1', 'credentials_changed': True, **LOCK}
        self.assertEqual(len(p.plan_credential_upsert('pr-preview', '42', **args)['actions']), 1)
        for latest in (prs('closed'), prs(head=NEXT)):
            with self.assertRaises(p.PlanError):
                p.plan_credential_upsert('pr-preview', '42', **{**args, 'recheck': latest})

    def test_reopened_pr_blocks_cleanup(self):
        with self.assertRaises(p.PlanError):
            p.plan_cleanup('pr-preview', '42', raw_branch=None, expected_head=HEAD,
                           before=prs('closed'), recheck=prs('open'), base_version='1',
                           services=inventory(boundary='pr-preview'), existing_key_names=[], **LOCK)

    def test_closed_pr_cleanup_requires_both_absent_services(self):
        services = inventory(service('42', observation='not_found', boundary='pr-preview'),
                             service('42', component='frontend', observation='not_found', boundary='pr-preview'), boundary='pr-preview')
        plan = p.plan_cleanup('pr-preview', '42', raw_branch=None, expected_head=HEAD,
                              before=prs('closed'), recheck=prs('closed'), base_version='1',
                              services=services, existing_key_names=p.credential_keys('pr-preview', '42'), **LOCK)
        self.assertEqual(len(plan['actions'][0]['remove_keys']), 4)

    def test_pr_head_change_while_closed_blocks_cleanup(self):
        with self.assertRaises(p.PlanError):
            p.plan_cleanup('pr-preview', '42', raw_branch=None, expected_head=HEAD,
                           before=prs('closed'), recheck=prs('closed', NEXT), base_version='1',
                           services=inventory(boundary='pr-preview'), existing_key_names=[], **LOCK)

    def test_same_repository_pr_is_not_a_dedicated_fork_preview(self):
        snapshot = prs()
        snapshot['items'][0]['head_repository'] = p.REPOSITORY
        with self.assertRaises(p.PlanError):
            p.plan_credential_upsert('pr-preview', '42', raw_branch=None, expected_head=HEAD,
                                    before=snapshot, recheck=snapshot, base_version='1', credentials_changed=True, **LOCK)

    def test_cleanup_never_treats_api_errors_as_absence(self):
        for outcome in ('permission_denied', 'transient_error', 'unknown', 'present'):
            services = inventory(service(observation=outcome), service(component='frontend', observation='not_found'))
            with self.assertRaises(p.PlanError):
                cleanup(services=services)

    def test_cleanup_requires_complete_service_pair_and_four_keys(self):
        with self.assertRaises(p.PlanError):
            cleanup(services=inventory(service(observation='not_found')))
        with self.assertRaises(p.PlanError):
            cleanup(existing_key_names=p.credential_keys('branch-preview', 'alpha')[:3])
        self.assertEqual(cleanup(existing_key_names=[])['actions'], [])

    def test_cleanup_only_removes_target_keys_and_preserves_tokens(self):
        plan = cleanup()
        self.assertEqual(plan['actions'][0]['remove_keys'], p.credential_keys('branch-preview', 'alpha'))
        self.assertEqual(plan['token_policy'], 'retain_all_existing_tokens')
        self.assertFalse(plan['automatic_retirement'])

    def test_failed_cleanup_observation_never_reaches_store(self):
        backend = FakeBackend()
        before = dict(backend.versions)
        with self.assertRaises(p.PlanError):
            cleanup(services=inventory(service(observation='transient_error'), service(component='frontend', observation='not_found')))
        self.assertEqual(backend.versions, before)
        self.assertEqual(backend.publishes, 0)

    def test_repin_all_live_backend_consumers_without_rebuilding_or_env_reads(self):
        plan = repin()
        self.assertEqual({item['service'] for item in plan['actions']}, {'modtale-backend-alpha', 'modtale-backend-beta'})
        for action in plan['actions']:
            self.assertEqual(action['action'], 'configuration_only_repin')
            self.assertTrue(action['preserve_image'])
            self.assertFalse(action['read_environment_values'])
            self.assertTrue(action['preserve_existing_traffic_until_ready'])
            self.assertEqual(action['to_version'], '2')

    def test_partial_rollout_resume_skips_completed_and_retries_only_failed(self):
        plan = repin(services=inventory(service(version='2'), service('beta', version='2', state='failed')))
        self.assertEqual(plan['completed_services'], ['modtale-backend-alpha'])
        self.assertEqual(len(plan['actions']), 1)
        self.assertEqual(plan['actions'][0]['service'], 'modtale-backend-beta')
        self.assertTrue(plan['actions'][0]['retry_failed_revision'])
        self.assertEqual(plan['retain_versions'], ['1', '2'])

    def test_pending_rollout_waits_instead_of_overwriting_it(self):
        plan = repin(services=inventory(service(version='2', state='pending')))
        self.assertEqual(plan['actions'][0]['action'], 'wait_for_revision')
        self.assertEqual(plan['retain_versions'], ['1', '2'])

    def test_failed_publication_has_no_usable_repin_plan(self):
        for result in ({}, {'changed': True}, {'version': '2', 'changed': True}, {'version': '2', 'changed': True, 'previousVersion': '2'}):
            with self.assertRaises(p.PlanError):
                repin(publication=result)

    def test_unchanged_publication_can_resume_unfinished_rollout(self):
        plan = repin(publication={'version': '2', 'changed': False})
        self.assertEqual(len(plan['actions']), 2)
        self.assertEqual(plan['retain_versions'], ['1', '2'])

    def test_serving_tagged_rollback_and_current_versions_all_retained(self):
        plan = repin(references=references('1', '3', '4'), enabled_versions=['1', '2', '3', '4', '5', '6'], rollback_versions=['5'])
        self.assertEqual(plan['retain_versions'], ['1', '2', '3', '4', '5'])
        self.assertEqual(plan['unreferenced_versions_for_review'], ['6'])
        self.assertFalse(plan['automatic_retirement'])

    def test_latest_and_unknown_references_fail_closed(self):
        for invalid in ('latest', '', '0', 'bad', '1' * 20):
            with self.assertRaises(p.PlanError):
                repin(references=references(invalid))
        with self.assertRaises(p.PlanError):
            repin(references=references('9'))

    def test_orphan_consumers_keep_their_pins_and_tokens(self):
        plan = repin(lifecycle=branches('beta'))
        self.assertEqual(plan['actions'][0]['action'], 'reconcile_orphan')
        self.assertEqual(plan['actions'][0]['preserve_version'], '1')
        self.assertEqual(plan['token_policy'], 'retain_all_existing_tokens')

    def test_unknown_or_incomplete_metadata_is_rejected(self):
        for target in ('lifecycle', 'services', 'references'):
            default = {'lifecycle': branches('alpha'), 'services': inventory(service()), 'references': references('1')}[target]
            incomplete = copy.deepcopy(default)
            incomplete['complete'] = False
            with self.assertRaises(p.PlanError):
                repin(**{target: incomplete})
            unexpected = copy.deepcopy(default)
            unexpected['credential'] = 'SYNTHETIC-PRIVATE'
            with self.assertRaises(p.PlanError) as caught:
                repin(**{target: unexpected})
            self.assertNotIn('SYNTHETIC-PRIVATE', str(caught.exception))

    def test_malformed_bounds_duplicate_inventory_and_unknown_metadata_fail_closed(self):
        for boundary in (None, [], {}, 'shared', 'production'):
            with self.assertRaises(p.PlanError):
                p.credential_keys(boundary, 'alpha')
        for snapshot in (None, [], {}, {'repository': p.REPOSITORY, 'complete': True, 'items': 'unknown'}):
            with self.assertRaises(p.PlanError):
                upsert(recheck=snapshot)
        for invalid in ('short', 'A' * 40, '', None):
            with self.assertRaises(p.PlanError):
                upsert(expected_head=invalid)
        with self.assertRaises(p.PlanError):
            repin(enabled_versions=['1', '2', '2'])
        with self.assertRaises(p.PlanError):
            repin(services=inventory(service(), service()))

    def test_legacy_unpinned_preview_is_not_silently_claimed_migrated(self):
        legacy = service()
        legacy['bundle_version'] = None
        with self.assertRaises(p.PlanError):
            repin(services=inventory(legacy))

    def test_nonready_or_unknown_frontend_does_not_become_credential_consumer(self):
        plan = repin(services=inventory(service(version='2'), service(component='frontend', state='pending')))
        self.assertEqual(plan['actions'], [])
        bad = service(component='frontend')
        bad['bundle_version'] = '1'
        with self.assertRaises(p.PlanError):
            repin(services=inventory(bad))

    def test_incomplete_or_invalid_revision_references_block_plan(self):
        bad = references('1')
        bad['items'][0]['revision'] = 'modtale-backend-alpha-invalid#name'
        with self.assertRaises(p.PlanError):
            repin(references=bad)
        bad = references('1')
        bad['items'][0]['tagged'] = 'true'
        with self.assertRaises(p.PlanError):
            repin(references=bad)

    def test_pr_repin_stays_in_dedicated_preview_project(self):
        plan = p.plan_repin_after_publication('pr-preview', {'version': '2', 'changed': True, 'previousVersion': '1'},
                                              lifecycle=prs(), services=inventory(service('42', boundary='pr-preview'), boundary='pr-preview'),
                                              references={'complete': True, 'items': []}, enabled_versions=['1', '2'], rollback_versions=[], **LOCK)
        self.assertEqual(plan['project'], 'modtale-pr-preview')
        self.assertEqual(plan['actions'][0]['service'], 'modtale-pr-42-backend')
        self.assertEqual(plan['actions'][0]['secret'], 'MODTALE_CONFIG_PR_PREVIEW')

    def test_destination_is_fixed_and_unknown_service_names_are_rejected(self):
        services = inventory(service())
        services['project'] = 'other-project'
        with self.assertRaises(p.PlanError):
            repin(services=services)
        services = inventory(service())
        services['items'][0]['name'] = 'modtale-backend'
        with self.assertRaises(p.PlanError):
            repin(services=services)

    def test_plans_never_contain_credential_data_or_retirement_actions(self):
        for plan in (upsert(), cleanup(), repin()):
            text = json.dumps(plan)
            self.assertNotIn('SYNTHETIC-PRIVATE', text)
            for action in plan['actions']:
                self.assertNotIn(action['action'], ('destroy', 'disable', 'revoke', 'deploy_image', 'delete_service'))


if __name__ == '__main__':
    unittest.main()
