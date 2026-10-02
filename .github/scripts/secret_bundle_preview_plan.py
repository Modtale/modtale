"""Pure preview lifecycle plans. No cloud client, credential values, or executor.

The caller must supply fresh, complete, metadata-only observations from trusted APIs
while holding the repository-wide mutation lock. A boolean assertion cannot prove
that lock exists. GitHub ref/PR changes are not locked by Actions concurrency, so
the future executor must recheck lifecycle immediately before each side effect.

Credential values belong exclusively in the future store executor. It should call
preview_updates() once with the full four-field credential dictionary, then call
BundleStore.update() once, under the same lock, after require_current_base().
This module never calls that store, provisions tokens, changes traffic, or retires
versions. A publication result is metadata returned by BundleStore.update().
"""
import hashlib
import json
import re

from secret_bundle import TARGETS, numeric_version

LOCK_GROUP = 'modtale-secret-bundle-mutations-v1'
REPOSITORY = 'Modtale/modtale'
REGION = 'us-central1'
BOUNDARIES = {'branch-preview', 'pr-preview'}
RESERVED = {'main', 'develop', 'dev'}
SUFFIXES = ('access-key', 'endpoint', 'secret-key', 'token-id')
MAX_ITEMS = 10000


class PlanError(ValueError):
    """Safe errors contain no input values or credential-bearing causes."""


def _fail():
    raise PlanError('Preview metadata or execution preconditions are invalid.')


def _shape(value, keys):
    if not isinstance(value, dict) or set(value) != set(keys):
        _fail()


def _text(value, maximum=255):
    if not isinstance(value, str) or not value or len(value) > maximum or any(ord(c) < 32 or ord(c) == 127 for c in value):
        _fail()
    try:
        value.encode('utf-8')
    except UnicodeError:
        raise PlanError('Preview metadata contains invalid text.') from None
    return value


def _list(value):
    if not isinstance(value, list) or len(value) > MAX_ITEMS:
        _fail()
    return value


def _version(value):
    try:
        if not isinstance(value, str) or len(value) > 19:
            _fail()
        return numeric_version(value)
    except ValueError:
        raise PlanError('An explicit positive numeric bundle version is required.') from None


def _sha(value):
    if not isinstance(value, str) or re.fullmatch(r'[0-9a-f]{40}', value) is None:
        _fail()
    return value


def _lock(lock_held, lock_group):
    if lock_held is not True or lock_group != LOCK_GROUP:
        _fail()


def _boundary(boundary):
    if not isinstance(boundary, str) or boundary not in BOUNDARIES:
        _fail()
    return boundary


def _preview_id(boundary, preview_id):
    _boundary(boundary)
    if not isinstance(preview_id, str):
        _fail()
    pattern = r'[a-z0-9](?:[a-z0-9-]{0,18}[a-z0-9])?' if boundary == 'branch-preview' else r'[1-9][0-9]{0,19}'
    if re.fullmatch(pattern, preview_id) is None or preview_id in RESERVED:
        _fail()
    return preview_id


def branch_slug(raw_branch, head_sha):
    """Matches the current workflow's 20-character branch-slug algorithm."""
    _text(raw_branch)
    _sha(head_sha)
    return re.sub('-+', '-', re.sub('[^a-zA-Z0-9-]', '-', raw_branch).lower())[:20].strip('-') or head_sha[:7]


def credential_keys(boundary, preview_id):
    _preview_id(boundary, preview_id)
    return [f'{boundary}-{preview_id}-r2-{suffix}' for suffix in SUFFIXES]


def _lifecycle(boundary, snapshot):
    """Return validated owners, including closed PR records, without API access."""
    _boundary(boundary)
    _shape(snapshot, ('repository', 'complete', 'items'))
    if snapshot['repository'] != REPOSITORY or snapshot['complete'] is not True:
        _fail()
    owners = {}
    seen = set()
    for item in _list(snapshot['items']):
        if boundary == 'branch-preview':
            _shape(item, ('name', 'head_sha'))
            name = _text(item['name'])
            head = _sha(item['head_sha'])
            if name in seen:
                _fail()
            seen.add(name)
            slug = branch_slug(name, head)
            if name in RESERVED:
                continue
            # A non-protected raw branch that maps to a protected slug is unsafe.
            _preview_id(boundary, slug)
            if slug in owners:
                _fail()
            owners[slug] = {'name': name, 'head_sha': head, 'state': 'open'}
        else:
            _shape(item, ('number', 'state', 'head_sha', 'head_repository'))
            number = _preview_id(boundary, item['number'])
            if number in seen or item['state'] not in ('open', 'closed'):
                _fail()
            seen.add(number)
            head = _sha(item['head_sha'])
            head_repository = _text(item['head_repository'])
            if re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', head_repository) is None:
                _fail()
            # This project provisions dedicated PR previews only for fork PRs.
            if head_repository == REPOSITORY:
                continue
            owners[number] = {'head_sha': head, 'state': item['state'], 'head_repository': head_repository}
    return owners


def _check_target(boundary, preview_id, raw_branch, expected_head, snapshot, cleanup):
    _preview_id(boundary, preview_id)
    _sha(expected_head)
    owners = _lifecycle(boundary, snapshot)
    owner = owners.get(preview_id)
    if boundary == 'branch-preview':
        if branch_slug(raw_branch, expected_head) != preview_id or raw_branch in RESERVED:
            _fail()
        if cleanup:
            if owner is not None:
                _fail()
        elif owner is None or owner['name'] != raw_branch or owner['head_sha'] != expected_head:
            _fail()
    else:
        if raw_branch is not None or owner is None or owner['head_sha'] != expected_head:
            _fail()
        if owner['state'] != ('closed' if cleanup else 'open'):
            _fail()
    return owners


def _proof(before, recheck):
    # Only validated lifecycle metadata is fingerprinted. No payloads enter plans.
    raw = json.dumps([before, recheck], sort_keys=True, separators=(',', ':')).encode('utf-8')
    return hashlib.sha256(raw).hexdigest()


def _base_plan(boundary, operation, version):
    project, secret = TARGETS[_boundary(boundary)]
    return {
        'schema_version': 1, 'operation': operation, 'boundary': boundary,
        'project': project, 'region': REGION, 'secret': secret,
        'base_version': _version(version), 'lock_group': LOCK_GROUP,
        'recheck_lifecycle_before_each_side_effect': True,
        'token_policy': 'retain_all_existing_tokens',
        'automatic_retirement': False, 'actions': [],
    }


def require_current_base(plan, current_version, *, lock_held=False, lock_group=''):
    """Future executor must run this under its real lock immediately before update."""
    _lock(lock_held, lock_group)
    if not isinstance(plan, dict) or plan.get('lock_group') != LOCK_GROUP:
        _fail()
    if _version(plan.get('base_version')) != _version(current_version):
        raise PlanError('Bundle changed after planning; re-observe and re-plan.')


def plan_credential_upsert(boundary, preview_id, *, raw_branch, expected_head,
                           before, recheck, base_version, credentials_changed,
                           lock_held=False, lock_group=''):
    """Plan one complete four-key patch. Never accepts a credential dictionary."""
    _lock(lock_held, lock_group)
    if type(credentials_changed) is not bool:
        _fail()
    _check_target(boundary, preview_id, raw_branch, expected_head, before, False)
    _check_target(boundary, preview_id, raw_branch, expected_head, recheck, False)
    plan = _base_plan(boundary, 'upsert', base_version)
    plan['preview_id'] = preview_id
    plan['lifecycle_proof'] = _proof(before, recheck)
    if credentials_changed:
        plan['actions'] = [{
            'action': 'atomic_store_patch', 'set_keys': credential_keys(boundary, preview_id),
            'remove_keys': [], 'preserve_unlisted_keys': True,
            'store_calls': 1, 'expected_base_version': plan['base_version'],
        }]
    return plan


def service_name(boundary, preview_id, component):
    _preview_id(boundary, preview_id)
    if component not in ('backend', 'frontend'):
        _fail()
    return f'modtale-{component}-{preview_id}' if boundary == 'branch-preview' else f'modtale-pr-{preview_id}-{component}'


def _services(boundary, inventory):
    _shape(inventory, ('project', 'region', 'complete', 'items'))
    if inventory['project'] != TARGETS[_boundary(boundary)][0] or inventory['region'] != REGION or inventory['complete'] is not True:
        _fail()
    result = {}
    for item in _list(inventory['items']):
        _shape(item, ('preview_id', 'component', 'name', 'observation', 'bundle_version', 'revision', 'rollout_state'))
        name = service_name(boundary, item['preview_id'], item['component'])
        if item['name'] != name or name in result or item['observation'] not in ('present', 'not_found'):
            # Permission errors, timeouts and unknown outcomes are never absence.
            _fail()
        if item['observation'] == 'not_found':
            if any(item[key] is not None for key in ('bundle_version', 'revision', 'rollout_state')):
                _fail()
        else:
            revision = _text(item['revision'], 63)
            if not revision.startswith(name + '-') or re.fullmatch(r'[a-z0-9][a-z0-9-]*[a-z0-9]', revision) is None:
                _fail()
            if item['rollout_state'] not in ('ready', 'pending', 'failed'):
                _fail()
            if item['component'] == 'backend':
                _version(item['bundle_version'])
            elif item['bundle_version'] is not None:
                _fail()
        result[name] = dict(item)
    return result


def plan_cleanup(boundary, preview_id, *, raw_branch, expected_head, before, recheck,
                 services, base_version, existing_key_names, lock_held=False, lock_group=''):
    """Only plans removing keys after both services are authoritatively absent."""
    _lock(lock_held, lock_group)
    _check_target(boundary, preview_id, raw_branch, expected_head, before, True)
    _check_target(boundary, preview_id, raw_branch, expected_head, recheck, True)
    observations = _services(boundary, services)
    for component in ('backend', 'frontend'):
        item = observations.get(service_name(boundary, preview_id, component))
        if item is None or item['observation'] != 'not_found':
            _fail()
    expected_keys = credential_keys(boundary, preview_id)
    keys = _list(existing_key_names)
    if any(not isinstance(key, str) for key in keys) or len(set(keys)) != len(keys):
        _fail()
    if keys and set(keys) != set(expected_keys):
        _fail()
    plan = _base_plan(boundary, 'cleanup', base_version)
    plan['preview_id'] = preview_id
    plan['lifecycle_proof'] = _proof(before, recheck)
    if keys:
        plan['actions'] = [{
            'action': 'atomic_store_patch', 'set_keys': [], 'remove_keys': expected_keys,
            'preserve_unlisted_keys': True, 'store_calls': 1,
            'expected_base_version': plan['base_version'],
        }]
    return plan


def _publication(result):
    if not isinstance(result, dict) or type(result.get('changed')) is not bool:
        _fail()
    _shape(result, ('version', 'changed', 'previousVersion') if result['changed'] else ('version', 'changed'))
    version = _version(result['version'])
    previous = _version(result['previousVersion']) if result['changed'] else None
    if previous is not None and int(previous) >= int(version):
        _fail()
    return version, previous


def _references(boundary, inventory):
    _shape(inventory, ('complete', 'items'))
    if inventory['complete'] is not True:
        _fail()
    retained = set()
    seen = set()
    for item in _list(inventory['items']):
        _shape(item, ('preview_id', 'service', 'revision', 'version', 'serving', 'tagged', 'rollback'))
        name = service_name(boundary, item['preview_id'], 'backend')
        revision = _text(item['revision'], 63)
        if (item['service'] != name or not revision.startswith(name + '-')
                or re.fullmatch(r'[a-z0-9][a-z0-9-]*[a-z0-9]', revision) is None
                or (name, revision) in seen):
            _fail()
        seen.add((name, revision))
        if any(type(item[key]) is not bool for key in ('serving', 'tagged', 'rollback')):
            _fail()
        version = _version(item['version'])  # Even an inactive latest reference is ambiguous.
        if item['serving'] or item['tagged'] or item['rollback']:
            retained.add(version)
    return retained


def plan_repin_after_publication(boundary, publication, *, lifecycle, services,
                                 references, enabled_versions, rollback_versions,
                                 lock_held=False, lock_group=''):
    """Resume a rollout from fresh metadata; retain old revisions and every token.

    Repins affect only backend bundle consumers. Frontends never receive secrets.
    Mount-only updates preserve existing images, environment values and traffic
    policy. A future executor must apply explicit readiness/traffic gates; this
    plan does not assert that publication or an emitted action completed.
    """
    _lock(lock_held, lock_group)
    target, previous = _publication(publication)
    owners = _lifecycle(boundary, lifecycle)
    observations = _services(boundary, services)
    retained = _references(boundary, references)
    enabled_list = [_version(value) for value in _list(enabled_versions)]
    enabled = set(enabled_list)
    if len(enabled) != len(enabled_list):
        _fail()
    retained.update(_version(value) for value in _list(rollback_versions))
    retained.add(target)
    if previous is not None:
        retained.add(previous)
    plan = _base_plan(boundary, 'repin', target)
    plan['target_version'] = target
    completed = []
    for name, item in sorted(observations.items()):
        if item['observation'] != 'present' or item['component'] != 'backend':
            continue
        current = _version(item['bundle_version'])
        retained.add(current)
        owner = owners.get(item['preview_id'])
        if owner is None or owner['state'] != 'open':
            # Unresolved orphan consumers keep their pins and tokens until cleanup.
            plan['actions'].append({'action': 'reconcile_orphan', 'service': name, 'preserve_version': current})
            continue
        if item['rollout_state'] == 'pending':
            plan['actions'].append({'action': 'wait_for_revision', 'service': name, 'revision': item['revision'], 'preserve_version': current})
            continue
        if current == target and item['rollout_state'] == 'ready':
            completed.append(name)
            continue
        plan['actions'].append({
            'action': 'configuration_only_repin', 'service': name,
            'preview_id': item['preview_id'], 'from_version': current, 'to_version': target,
            'expected_revision': item['revision'], 'retry_failed_revision': item['rollout_state'] == 'failed',
            'mount_path': f'/app/secrets/bundles/{boundary}.json',
            'secret': TARGETS[boundary][1], 'preserve_image': True,
            'read_environment_values': False, 'preserve_existing_traffic_until_ready': True,
        })
    if not retained.issubset(enabled):
        raise PlanError('Version reference inventory is incomplete or inaccessible.')
    plan['completed_services'] = completed
    plan['retain_versions'] = sorted(retained, key=int)
    # Information for later review only; deliberately no retirement action.
    plan['unreferenced_versions_for_review'] = sorted(enabled - retained, key=int)
    return plan
