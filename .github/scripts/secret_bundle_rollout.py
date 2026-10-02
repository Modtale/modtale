"""Conditional, resumable numeric-pin rollout. No credentials or live transport.

This executor sends real GET/PATCH request descriptions to an injected transport;
tests supply FakeRun only. The transport must honor server response field masks,
never log bodies, and never read non-success response bodies. Existing authorized
SDK sessions can implement it without extracting authentication material.

One AtomicFileJournal belongs to ONE service on an existing persistent workspace.
save() fsyncs before returning and performs a compare-and-swap. On ephemeral CI,
the caller MUST replace it with a synchronous durable journal adapter (for example
an existing metadata-only artifact store), retrieve the latest journal before any
deployment, and acknowledge durability before a cloud write. Uploading in an
`always`/post-job step is NOT sufficient. No new infrastructure is created here.

All deploy/provision/cleanup writers must hold the same external non-canceling
lock. A boolean cannot prove a lock exists. Cloud Run etags are also required;
unexpected generation/UID/config/traffic changes stop rather than overwrite.
An uncertain PATCH is reconciled by deterministic revision name and desired state
before replaying the exact etag-conditional intent. Never reset an unfinished
journal to unblock a deployment. Keep terminal journals/rollback metadata before
an operator starts a subsequent transaction.

Scope is already-active, digest-pinned single-container/single-secret-volume
services only. Mutable image tags must first be prepared through the controlled
ordinary deployment path; retaining a tag is not proof of retaining its image.
Mount-only rollouts require an inherited HTTP application-readiness startup
probe. Generic metadata parsers still accept legacy probes so ordinary deployment
can repair them; this executor never changes the probe itself.
An unfinished journal captured with an older projection may lack probe history;
if live metadata then differs, recovery fails closed for explicit reconciliation.
Never discard that journal or infer the missing historical configuration.
Initial activation remains a separate controlled migration. Partial PATCH masks
never include image, containers, environment, annotations, IAM, or secret values.
Stage freezes LATEST into explicit numeric old revisions. Promotion moves only
the current revision's percentage; existing tags stay on their original revisions.
Rollback restores old volume metadata and saved numeric traffic. Nothing deletes,
disables, revokes, or retires any revision, secret version, or token.
"""
import base64
import copy
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
from urllib.parse import urlsplit
import zlib

import secret_bundle_preview_plan as plan
from secret_bundle import TARGETS
from secret_bundle_activation_evidence import activation_request, verify_activation
from secret_bundle_journal_codec import scoped_record_bytes, parse_scoped_record

MAX_BYTES = 65536
MAX_ETAG_BYTES = 1024
ORIGIN = 'https://run.googleapis.com'
TRAFFIC = '(type,revision,percent,tag)'
STARTUP_PROBE_FIELDS = ('startupProbe(initialDelaySeconds,timeoutSeconds,periodSeconds,failureThreshold,'
                        'httpGet(path,port,httpHeaders(name)),tcpSocket(port),grpc(port))')
CONFIG = ('containers(name,image,volumeMounts(name,mountPath,subPath),' + STARTUP_PROBE_FIELDS
          + '),volumes(name,secret(secret,defaultMode,items(path,version,mode)))')
SERVICE_FIELDS = ('name,uid,etag,generation,observedGeneration,reconciling,terminalCondition(state),'
                  'latestCreatedRevision,latestReadyRevision,traffic' + TRAFFIC + ',trafficStatuses' + TRAFFIC
                  + ',template(revision,' + CONFIG + ')')
WARMUP_SERVICE_FIELDS = SERVICE_FIELDS + ',uri,ingress'
ROUTE_FIELDS = 'name,uid,generation,trafficStatuses(type,revision,percent,tag,uri)'
REVISION_FIELDS = 'name,uid,service,createTime,' + CONFIG
# Readiness is mutable status, not part of the immutable journal snapshot.
REVISION_READINESS_FIELDS = 'name,uid,conditions(type,state)'
REVISION_TYPE = 'TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION'
LATEST_TYPE = 'TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST'
PHASES = {'stage_intent', 'stage_wait', 'promote_intent', 'promote_wait', 'complete',
          'rollback_intent', 'rollback_wait', 'rolled_back'}


class RolloutError(ValueError):
    pass


def fail(message='Rollout metadata or preconditions are invalid; no unsafe recovery attempted.'):
    raise RolloutError(message)


def shape(value, allowed, required=()):
    if not isinstance(value, dict) or not set(value).issubset(allowed) or not set(required).issubset(value):
        fail()
    return value


def text(value, maximum=1024):
    try:
        result = plan._text(value, maximum)
        if len(result.encode('utf-8')) > maximum:
            fail()
        return result
    except Exception:
        raise RolloutError('Invalid rollout metadata text.') from None


def array(value, maximum=100):
    if not isinstance(value, list) or len(value) > maximum:
        fail()
    return value


def version(value):
    try:
        return plan._version(value)
    except Exception:
        raise RolloutError('An explicit numeric bundle version is required.') from None


def scope(profile, preview_id):
    if profile in ('prod', 'dev'):
        if preview_id is not None:
            fail()
        boundary, service = 'shared', 'modtale-backend' + ('-dev' if profile == 'dev' else '')
    elif profile in ('branch-preview', 'pr-preview'):
        try:
            service = plan.service_name(profile, preview_id, 'backend')
        except Exception:
            raise RolloutError('Invalid preview identity.') from None
        boundary = profile
    else:
        fail()
    project, secret = TARGETS[boundary]
    full = f'projects/{project}/locations/us-central1/services/{service}'
    return boundary, secret, service, full


def revision_name(value, full):
    value = text(value)
    prefix = full + '/revisions/'
    short = value[len(prefix):] if value.startswith(prefix) else value
    service = full.rsplit('/', 1)[-1]
    if len(short) > 63 or re.fullmatch(re.escape(service) + r'-[a-z0-9-]+[a-z0-9]', short) is None:
        fail('Revision identity does not belong to this service.')
    return short


def startup_probe(raw):
    """Validate only the projected shape, without fetching HTTP header values.

    Missing probes are handled by the caller. TCP/gRPC and larger legacy timing
    budgets are parseable, but cannot authorize a mount-only forward rollout.
    Normalize documented omitted scalar defaults for service/revision comparison.
    """
    shape(raw, {'initialDelaySeconds', 'timeoutSeconds', 'periodSeconds', 'failureThreshold',
                'httpGet', 'tcpSocket', 'grpc'})
    modes = set(raw).intersection({'httpGet', 'tcpSocket', 'grpc'})
    if len(modes) != 1:
        fail('Startup probe metadata is incomplete or ambiguous.')
    result = copy.deepcopy(raw)
    defaults = {'initialDelaySeconds': 0, 'timeoutSeconds': 1, 'periodSeconds': 10, 'failureThreshold': 3}
    for key, default in defaults.items():
        value = raw.get(key, default)
        maximum = 2147483647 if key == 'failureThreshold' else 3600
        minimum = 0 if key == 'initialDelaySeconds' else 1
        if type(value) is not int or not minimum <= value <= maximum:
            fail('Startup probe timing metadata is invalid.')
        result[key] = value
    if result['timeoutSeconds'] > result['periodSeconds']:
        fail('Startup probe timing metadata is invalid.')
    mode = next(iter(modes))
    action = shape(raw[mode], {'path', 'port', 'httpHeaders'} if mode == 'httpGet' else {'port'})
    if 'port' in action and (type(action['port']) is not int or not 1 <= action['port'] <= 65535):
        fail('Startup probe port metadata is invalid.')
    if mode == 'httpGet':
        if 'path' in action:
            text(action['path'], 2048)
        headers = array(action.get('httpHeaders', []))
        for header in headers:
            shape(header, {'name'}, {'name'})
            text(header['name'], 256)
        # An omitted empty list and an explicit empty list describe the same probe.
        result[mode].pop('httpHeaders', None)
        if headers:
            result[mode]['httpHeaders'] = copy.deepcopy(headers)
    return result


def require_readiness_startup_probe(container):
    """Fail closed unless inherited startup waits for application HTTP readiness.

    This is a conservative local rollout policy, not a statement that every
    larger probe accepted by Cloud Run is invalid. Ordinary deployment sets the
    canonical 0/10/5/24 probe; existing compatible budgets up to 240s are accepted.
    Header names suffice to reject custom-header probes; values are never needed.
    """
    if not isinstance(container, dict) or 'startupProbe' not in container:
        fail('A verified HTTP readiness startup probe is required before repinning.')
    probe = startup_probe(container['startupProbe'])
    http = probe.get('httpGet')
    if (http is None or http.get('path') != '/actuator/health/readiness' or http.get('port') != 8080
            or http.get('httpHeaders') or probe['initialDelaySeconds'] != 0
            or probe['periodSeconds'] > 240 or probe['periodSeconds'] * probe['failureThreshold'] > 240):
        fail('A verified HTTP readiness startup probe is required before repinning.')


def configuration(raw, profile, preview_id):
    boundary, secret, _, full = scope(profile, preview_id)
    shape(raw, {'revision', 'containers', 'volumes'}, {'containers', 'volumes'})
    containers, volumes = array(raw['containers'], 1), array(raw['volumes'], 1)
    if len(containers) != 1 or len(volumes) != 1:
        fail('Only an existing single-container/single-secret-volume bundle mount is supported.')
    container = shape(containers[0], {'name', 'image', 'volumeMounts', 'startupProbe'}, {'image', 'volumeMounts'})
    normalized_containers = copy.deepcopy(containers)
    if 'startupProbe' in container:
        normalized_containers[0]['startupProbe'] = startup_probe(container['startupProbe'])
    image = text(container['image'])
    if re.fullmatch(r'[A-Za-z0-9._:/-]+@sha256:[a-f0-9]{64}', image) is None:
        fail('An explicit immutable image digest is required before numeric-only repinning.')
    if 'name' in container:
        text(container['name'], 63)
    mounts = array(container['volumeMounts'], 1)
    if len(mounts) != 1:
        fail()
    mount = shape(mounts[0], {'name', 'mountPath', 'subPath'}, {'name', 'mountPath'})
    name = text(mount['name'], 63)
    if mount['mountPath'] != '/app/secrets/bundles' or mount.get('subPath', '') != '':
        fail('Unexpected bundle mount.')
    volume = shape(volumes[0], {'name', 'secret'}, {'name', 'secret'})
    if volume['name'] != name:
        fail()
    source = shape(volume['secret'], {'secret', 'defaultMode', 'items'}, {'secret', 'items'})
    project = full.split('/')[1]
    if source['secret'] not in (secret, f'projects/{project}/secrets/{secret}'):
        fail('Unexpected bundle boundary.')
    items = array(source['items'], 1)
    if len(items) != 1:
        fail()
    item = shape(items[0], {'path', 'version', 'mode'}, {'path', 'version'})
    if item['path'] != boundary + '.json':
        fail()
    for obj, key in ((source, 'defaultMode'), (item, 'mode')):
        if key in obj and (type(obj[key]) is not int or not 0 <= obj[key] <= 511):
            fail()
    if 'revision' in raw and raw['revision']:
        revision_name(raw['revision'], full)
    return version(item['version']), copy.deepcopy(volumes), normalized_containers


def traffic(raw, full, latest):
    rows = array(raw)
    percentages, tags = {}, {}
    if not rows:
        if latest is None:
            fail('Traffic has no known numeric target.')
        rows = [{'type': REVISION_TYPE, 'revision': latest, 'percent': 100}]
    for row in rows:
        shape(row, {'type', 'revision', 'percent', 'tag'}, {'type'})
        if row['type'] not in (LATEST_TYPE, REVISION_TYPE):
            fail()
        rev = revision_name(row['revision'], full) if row.get('revision') else latest if row['type'] == LATEST_TYPE else None
        if rev is None:
            fail()
        percent, tag = row.get('percent', 0), row.get('tag', '')
        if type(percent) is not int or not 0 <= percent <= 100 or not isinstance(tag, str):
            fail()
        percentages[rev] = percentages.get(rev, 0) + percent
        if tag:
            if re.fullmatch(r'[a-z][a-z0-9-]{0,62}', tag) is None or tag in tags:
                fail()
            tags[tag] = rev
    if sum(percentages.values()) != 100:
        fail('Traffic percentages are incomplete.')
    result = [{'type': REVISION_TYPE, 'revision': rev, 'percent': percent}
              for rev, percent in sorted(percentages.items()) if percent]
    result += [{'type': REVISION_TYPE, 'revision': rev, 'percent': 0, 'tag': tag}
               for tag, rev in sorted(tags.items())]
    return result


def parse_service(raw, profile, preview_id):
    _, _, _, full = scope(profile, preview_id)
    keys = {'name', 'uid', 'etag', 'generation', 'observedGeneration', 'reconciling', 'terminalCondition',
            'latestCreatedRevision', 'latestReadyRevision', 'traffic', 'trafficStatuses', 'template', 'uri', 'ingress'}
    shape(raw, keys, {'name', 'uid', 'etag', 'generation', 'terminalCondition', 'latestCreatedRevision', 'template'})
    if raw['name'] != full:
        fail()
    # Cloud Run etags are opaque and can encode the full service resource name.
    # Preserve quotes and every byte for CAS; the UID bound is unrelated.
    uid, etag = text(raw['uid'], 128), text(raw['etag'], MAX_ETAG_BYTES)
    if not isinstance(raw['generation'], str) or not re.fullmatch(r'[1-9][0-9]{0,18}', raw['generation']):
        fail()
    observed = raw.get('observedGeneration', '0')
    if not isinstance(observed, str) or not re.fullmatch(r'[0-9]{1,19}', observed):
        fail()
    if type(raw.get('reconciling', False)) is not bool:
        fail()
    condition = shape(raw['terminalCondition'], {'state'}, {'state'})['state']
    if condition not in ('CONDITION_SUCCEEDED', 'CONDITION_FAILED', 'CONDITION_PENDING', 'CONDITION_RECONCILING'):
        fail()
    created = revision_name(raw['latestCreatedRevision'], full)
    ready = revision_name(raw['latestReadyRevision'], full) if raw.get('latestReadyRevision') else None
    pin, volumes, containers = configuration(raw['template'], profile, preview_id)
    template = revision_name(raw['template']['revision'], full) if raw['template'].get('revision') else created
    desired = traffic(raw.get('traffic', []), full, ready)
    actual = traffic(raw['trafficStatuses'], full, ready) if raw.get('trafficStatuses') else None
    if 'uri' in raw:
        public_origin(raw['uri'], full.rsplit('/', 1)[-1])
    if 'ingress' in raw and raw['ingress'] not in ('INGRESS_TRAFFIC_ALL', 'INGRESS_TRAFFIC_INTERNAL_ONLY',
                                                  'INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER', 'INGRESS_TRAFFIC_UNSPECIFIED'):
        fail()
    return {'uid': uid, 'etag': etag, 'generation': int(raw['generation']), 'observed': int(observed),
            'condition': condition, 'reconciling': raw.get('reconciling', False), 'created': created, 'ready': ready,
            'template': template, 'pin': pin, 'volumes': volumes, 'containers': containers, 'traffic': desired, 'actual': actual,
            'uri': raw.get('uri'), 'ingress': raw.get('ingress')}


def public_origin(value, service, tag=None):
    value = text(value)
    try:
        parsed = urlsplit(value)
        port = parsed.port
    except ValueError:
        fail('Invalid readiness route origin.')
    if (parsed.scheme != 'https' or parsed.username or parsed.password or port or parsed.path
            or parsed.query or parsed.fragment or not parsed.hostname or value != 'https://' + parsed.hostname):
        fail('Invalid readiness route origin.')
    prefix = '' if tag is None else re.escape(tag) + '---'
    if re.fullmatch(prefix + re.escape(service) + r'-[a-z0-9-]+\.a\.run\.app', parsed.hostname) is None:
        fail('Unsupported readiness route origin.')
    if len(parsed.hostname) > 253 or any(len(label) > 63 for label in parsed.hostname.split('.')):
        fail('Readiness route exceeds DNS bounds.')
    return value


def parse_revision(raw, profile, preview_id, expected):
    _, _, short, full = scope(profile, preview_id)
    shape(raw, {'name', 'uid', 'service', 'createTime', 'containers', 'volumes'},
          {'name', 'uid', 'service', 'createTime', 'containers', 'volumes'})
    if revision_name(raw['name'], full) != expected or raw['service'] not in (short, full):
        fail()
    text(raw['uid'], 128)
    try:
        activation_request(profile, expected, raw['createTime'], preview_id)
    except Exception:
        raise RolloutError('Invalid immutable revision creation metadata.') from None
    return configuration({key: raw[key] for key in ('containers', 'volumes')}, profile, preview_id)


def staged_ready(state, expected):
    """The intended template is reconciled while saved traffic remains stable.

    Service.latestReadyRevision describes the serving revision and may remain
    the baseline for an untagged zero-traffic candidate. Callers must separately
    bind revision_ready() to immutable candidate metadata and keep activation,
    expected-image/probe/pin, saved-distribution and CAS checks before promotion.
    """
    return (not state['reconciling'] and state['condition'] == 'CONDITION_SUCCEEDED'
            and state['observed'] == state['generation'] and state['created'] == state['template'] == expected
            and state['actual'] == state['traffic'])


def revision_ready(raw, profile, preview_id, expected, expected_uid):
    """Validate separate, secret-free platform readiness for the exact revision.

    Only the Ready aggregate is sufficient, with no contradictory container
    readiness condition. Active/ResourcesAvailable may differ for a zero-traffic
    revision. This never substitutes for actual loader activation evidence.
    Missing/unknown readiness waits; malformed or mismatched identity fails closed.
    """
    shape(raw, {'name', 'uid', 'conditions'}, {'name', 'uid'})
    full = scope(profile, preview_id)[3]
    if revision_name(raw['name'], full) != expected or text(raw['uid'], 128) != text(expected_uid, 128):
        fail('Revision readiness identity does not match the immutable candidate.')
    states = {}
    for condition in array(raw.get('conditions', []), 32):
        shape(condition, {'type', 'state'}, {'type'})
        kind = text(condition['type'], 128)
        state = condition.get('state', 'STATE_UNSPECIFIED')
        if kind in states or state not in ('STATE_UNSPECIFIED', 'CONDITION_PENDING', 'CONDITION_RECONCILING',
                                           'CONDITION_FAILED', 'CONDITION_SUCCEEDED'):
            fail('Revision readiness metadata is invalid.')
        states[kind] = state
    return (states.get('Ready') == 'CONDITION_SUCCEEDED'
            and all(states[kind] == 'CONDITION_SUCCEEDED'
                    for kind in ('ContainerReady', 'ContainerHealthy') if kind in states))


def ready(state, expected):
    """Full serving readiness, required after promotion (not for a zero-traffic stage)."""
    return staged_ready(state, expected) and state['ready'] == expected


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            fail('Duplicate journal key.')
        result[key] = value
    return result


def _storage_etags(value, decode=False):
    """Bound opaque-token JSON growth while preserving the exact CAS bytes."""
    if isinstance(value, list):
        return [_storage_etags(item, decode) for item in value]
    if isinstance(value, dict):
        result = {}
        for key, item in value.items():
            if key == 'etag':
                if decode:
                    shape(item, {'base64'}, {'base64'})
                    raw = base64.b64decode(item['base64'], validate=True)
                    if len(raw) > MAX_ETAG_BYTES:
                        fail()
                    result[key] = text(raw.decode('utf-8'), MAX_ETAG_BYTES)
                else:
                    result[key] = {'base64': base64.b64encode(text(item, MAX_ETAG_BYTES).encode('utf-8')).decode('ascii')}
            else:
                result[key] = _storage_etags(item, decode)
        return result
    return value


def decode_journal_bytes(raw):
    if len(raw) > MAX_BYTES:
        fail()
    record = _storage_etags(json.loads(raw.decode('utf-8'), object_pairs_hook=_pairs), True)
    return validate_record(record)


def journal_bytes(record):
    validate_record(record)
    try:
        raw = json.dumps(_storage_etags(record), ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode('utf-8')
    except (UnicodeError, ValueError, TypeError):
        raise RolloutError('Invalid journal encoding.') from None
    if len(raw) > MAX_BYTES:
        fail('Rollout journal exceeds its bound.')
    return raw


def validate_record(record):
    keys = {'schema', 'profile', 'preview_id', 'raw_branch', 'head_sha', 'transaction_id', 'target_version',
            'base', 'base_revision', 'phase', 'intent_base', 'candidate', 'recovery_candidate',
            'candidate_verified', 'candidate_warmed', 'temporary_tag', 'retained_revisions', 'rollback_origin', 'operation'}
    shape(record, keys, keys)
    if type(record['schema']) is not int or record['schema'] != 2 or record['phase'] not in PHASES:
        fail()
    profile, identifier = record['profile'], record['preview_id']
    _, _, service, full = scope(profile, identifier)
    text(record['transaction_id'], 12)
    if (not re.fullmatch(r'[a-f0-9]{12}', record['transaction_id'])
            or record['temporary_tag'] not in ('sr-' + record['transaction_id'], 'r' + record['transaction_id'][:9])):
        fail()
    try:
        plan._sha(record['head_sha'])
    except Exception:
        raise RolloutError('Invalid lifecycle head.') from None
    if profile == 'pr-preview':
        if record['raw_branch'] is not None:
            fail()
    else:
        text(record['raw_branch'], 255)
        if profile in ('prod', 'dev') and record['raw_branch'] != ('main' if profile == 'prod' else 'develop'):
            fail()
    version(record['target_version'])
    base = parse_service(record['base'], profile, identifier)
    if base['uri'] is None or base['ingress'] != 'INGRESS_TRAFFIC_ALL' or any(row.get('tag') == record['temporary_tag'] for row in base['traffic']):
        fail('The owned warmup route must start absent on an existing public service.')
    ref_pin, _, _ = parse_revision(record['base_revision'], profile, identifier, base['created'])
    if ref_pin != base['pin'] or not ready(base, base['created']):
        fail('The original revision was not verified ready.')
    intent = parse_service(record['intent_base'], profile, identifier)
    if intent['uid'] != base['uid'] or intent['generation'] < base['generation'] or intent['generation'] > base['generation'] + 2:
        fail()
    for key, suffix in (('candidate', '-sb-'), ('recovery_candidate', '-sbr-')):
        if record[key] is not None:
            parse_revision(record[key], profile, identifier, service + suffix + record['transaction_id'])
    if type(record['candidate_verified']) is not bool or (record['candidate_verified'] and record['candidate'] is None):
        fail()
    if type(record['candidate_warmed']) is not bool or (record['candidate_warmed'] and record['candidate'] is None):
        fail()
    if record['candidate_verified'] and not record['candidate_warmed']:
        fail('Activation requires a successful owned-route readiness probe.')
    if record['phase'].startswith('promote') or record['phase'] == 'complete':
        if not record['candidate_verified']:
            fail('Promotion requires a persisted successful activation gate.')
    seen = set()
    for reference in array(record['retained_revisions'], 1000):
        fields = {'revision', 'uid', 'createTime', 'bundle_version', 'verified_active'}
        shape(reference, fields, fields)
        name = revision_name(reference['revision'], full)
        text(reference['uid'], 128)
        version(reference['bundle_version'])
        if type(reference['verified_active']) is not bool or name in seen:
            fail()
        seen.add(name)
        try:
            activation_request(profile, name, reference['createTime'], identifier)
        except Exception:
            raise RolloutError('Invalid retained revision metadata.') from None
    origin = record['rollback_origin']
    if origin is not None:
        shape(origin, {'operation', 'base', 'consumed'}, {'operation', 'base', 'consumed'})
        if origin['operation'] not in ('stage', 'promote') or type(origin['consumed']) is not bool:
            fail()
        origin_base = shape(origin['base'], {'uid', 'generation', 'etag'}, {'uid', 'generation', 'etag'})
        text(origin_base['uid'], 128)
        text(origin_base['etag'], MAX_ETAG_BYTES)
        expected_generation = base['generation'] + (1 if origin['operation'] == 'promote' else 0)
        if type(origin_base['generation']) is not int or origin_base['uid'] != base['uid'] or origin_base['generation'] != expected_generation:
            fail('Rollback origin must remain anchored to its original conditional intent.')
    if record['phase'].startswith('rollback') or record['phase'] == 'rolled_back':
        if origin is None:
            fail()
    if record['operation'] is not None:
        operation_name(record['operation'], full)
    return record


def operation_name(value, full):
    prefix = full.rsplit('/services/', 1)[0] + '/operations/'
    if not isinstance(value, str) or not value.startswith(prefix) or re.fullmatch(r'[A-Za-z0-9_-]{1,128}', value[len(prefix):]) is None:
        fail('Unrecognized Cloud Run operation identity.')
    return value


def reservation_bytes(record):
    """Physical upper-bound reservation for all remaining transaction checkpoints.

    Use an uncompressed worst-shaped record plus codec overhead, not an estimated
    compression ratio. Incoming etags are bounded to 1024 bytes and UIDs to 128.
    Full revision
    resource names and optional traffic/default fields are included. Unsupported
    large traffic/history records therefore stop BEFORE the first Cloud Run write.
    """
    future = copy.deepcopy(record)
    profile, identifier = record['profile'], record['preview_id']
    _, _, service, full = scope(profile, identifier)
    base = parse_service(record['base'], profile, identifier)
    staged, restored = service + '-sb-' + record['transaction_id'], service + '-sbr-' + record['transaction_id']
    def qualified(name):
        return full + '/revisions/' + name
    candidate = copy.deepcopy(record['base_revision'])
    candidate.update(name=qualified(staged), uid='"'*128, createTime='2026-12-31T23:59:59.123456789Z', service=full)
    candidate['volumes'][0]['secret']['items'][0]['version'] = record['target_version']
    recovery = copy.deepcopy(candidate)
    recovery.update(name=qualified(restored), uid='"'*128)
    recovery['volumes'] = base['volumes']
    future.update(phase='rollback_intent', candidate=candidate, recovery_candidate=recovery,
                  candidate_verified=False, candidate_warmed=False)
    targets = copy.deepcopy(base['traffic'])
    # A current tagged entry becomes separate percentage/tag rows in canonical form.
    for row in targets:
        if row['revision'] == base['created'] and row['percent']:
            row['revision'] = staged
        row['revision'] = qualified(row['revision'])
        row.setdefault('tag', '')
    targets.append({'type': REVISION_TYPE, 'revision': qualified(staged), 'percent': 0, 'tag': record['temporary_tag']})
    state = copy.deepcopy(record['base'])
    state.update(etag='"'*MAX_ETAG_BYTES, generation=str(base['generation']+2), observedGeneration=str(base['generation']+2),
                 reconciling=False, terminalCondition={'state': 'CONDITION_RECONCILING'},
                 latestCreatedRevision=qualified(restored), latestReadyRevision=qualified(restored),
                 traffic=targets, trafficStatuses=copy.deepcopy(targets))
    state['template'].update(revision=qualified(restored), volumes=candidate['volumes'])
    future.update(intent_base=state, rollback_origin={'operation': 'promote',
                  'base': {'uid': base['uid'], 'generation': base['generation']+1, 'etag': '"'*MAX_ETAG_BYTES}, 'consumed': False},
                  operation=full.rsplit('/services/', 1)[0]+'/operations/'+'o'*128)
    # 512 bytes covers representation/default-value differences not used by guards;
    # 128 is conservative zlib overhead for our <=64-KiB bounded input.
    file_limit = len(json.dumps(_storage_etags(future), ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')) + 512
    if file_limit > MAX_BYTES:
        fail('A recovery checkpoint would exceed the journal bound.')
    try:
        # The scoped representation references only known fixed resources. It
        # does not deduplicate unknown UID/etag data or assume compressibility.
        raw_limit = len(scoped_record_bytes(future, full)) + 512
    except Exception:
        raise RolloutError('A scoped recovery checkpoint would exceed the journal bound.') from None
    return 3 + 4*((raw_limit + 128 + 2)//3) + 1


class AtomicFileJournal:
    """Bounded atomic metadata file on a pre-existing durable filesystem.

    A stable file per service plus the external global lock prevents another run
    from skipping an incomplete transaction. This is not durable across ephemeral
    runners unless the caller synchronously persists/restores it before writes.
    The lock file contains no journal data. No cleanup or deletion is performed.
    """
    def __init__(self, path):
        self.path = Path(path)

    def _read(self):
        if self.path.is_symlink():
            fail('Journal symlinks are not supported.')
        try:
            with self.path.open('rb') as stream:
                raw = stream.read(MAX_BYTES + 1)
        except FileNotFoundError:
            return None, None
        if len(raw) > MAX_BYTES:
            fail()
        try:
            record = decode_journal_bytes(raw)
        except (ValueError, UnicodeError, RecursionError):
            raise RolloutError('Invalid persisted journal.') from None
        validate_record(record)
        return record, hashlib.sha256(raw).hexdigest()

    def load(self):
        try:
            return self._read()
        except OSError:
            raise RolloutError('Journal read failed; details suppressed.') from None

    def save(self, record, expected):
        raw = journal_bytes(record)
        temporary = None
        try:
            with self.path.with_name(self.path.name + '.lock').open('a+b') as lock:
                fcntl.flock(lock, fcntl.LOCK_EX)
                _, actual = self._read()
                if actual != expected:
                    fail('Journal changed concurrently; reload before continuing.')
                with tempfile.NamedTemporaryFile(dir=self.path.parent, prefix=self.path.name + '.', delete=False) as stream:
                    temporary = stream.name
                    stream.write(raw)
                    stream.flush()
                    os.fsync(stream.fileno())
                os.replace(temporary, self.path)
                temporary = None
                directory = os.open(self.path.parent, os.O_RDONLY | os.O_DIRECTORY)
                try:
                    os.fsync(directory)
                finally:
                    os.close(directory)
        except OSError:
            raise RolloutError('Journal persistence failed; cloud write was not authorized.') from None
        finally:
            if temporary is not None:
                # This uncommitted local temporary contains metadata only.
                try:
                    os.unlink(temporary)
                except OSError:
                    pass
        return hashlib.sha256(raw).hexdigest()


class SecretManagerJournal:
    """Concrete cross-run journal on an approved bundle container's annotations.

    Only the fixed bundle Secret resource is used, never a SecretVersion endpoint.
    Existing unrelated annotation keys are preserved. A 16-KiB aggregate metadata
    budget is enforced BEFORE PATCH. CAS conflicts stop; no grants or storage are
    created. The transport uses the existing deployer's authorized SDK session.
    The encoded value contains only the bounded metadata record, not credentials.
    Physical padding reserves completion/recovery space while a transaction is
    active. Completion releases it so other services can roll out serially. A
    later operator-requested rollback must acquire capacity again and can refuse
    safely before any write if the container filled meanwhile. That post-terminal
    operational limit is not an in-flight crash-recovery guarantee.
    """
    LIMIT = 16384

    def __init__(self, transport, profile, preview_id=None, *, consume_terminal=None, evict_completed=None):
        boundary, _, service, _ = scope(profile, preview_id)
        project, secret = TARGETS[boundary]
        self.transport, self.profile, self.preview_id = transport, profile, preview_id
        self.name = f'projects/{project}/secrets/{secret}'
        number = {'gen-lang-client-0244308719': '145553429208', 'modtale-pr-preview': '759035195996'}[project]
        self.response_names = {self.name, f'projects/{number}/secrets/{secret}'}
        self.key = 'modtale-rollout-' + service
        self.consume_terminal = None
        if consume_terminal is not None:
            shape(consume_terminal, {'key', 'checksum', 'validate'}, {'key', 'checksum', 'validate'})
            if (consume_terminal['key'] != 'modtale-deploy-' + service
                    or not isinstance(consume_terminal['checksum'], str)
                    or not re.fullmatch(r'[a-f0-9]{64}', consume_terminal['checksum'])
                    or not callable(consume_terminal['validate'])):
                fail('Invalid same-service terminal journal transfer.')
            self.consume_terminal = dict(consume_terminal)
        self.evict_completed = []
        seen = set()
        for eviction in array(evict_completed if evict_completed is not None else []):
            shape(eviction, {'key', 'checksum', 'validate'}, {'key', 'checksum', 'validate'})
            key = eviction['key']
            if not isinstance(key, str) or not key.startswith(('modtale-deploy-', 'modtale-rollout-')):
                fail('Invalid completed-journal eviction scope.')
            other = key[len('modtale-deploy-'):] if key.startswith('modtale-deploy-') else key[len('modtale-rollout-'):]
            if boundary == 'shared':
                allowed = other in ('modtale-backend', 'modtale-backend-dev')
            elif boundary == 'branch-preview':
                allowed = other.startswith('modtale-backend-') and scope('branch-preview', other[len('modtale-backend-'):])[2] == other
            else:
                match = re.fullmatch(r'modtale-pr-([1-9][0-9]*)-backend', other)
                allowed = match is not None and scope('pr-preview', match.group(1))[2] == other
            if (not allowed or other == service or key in seen
                    or not isinstance(eviction['checksum'], str)
                    or not re.fullmatch(r'[a-f0-9]{64}', eviction['checksum'])
                    or not callable(eviction['validate'])):
                fail('Invalid completed-journal eviction scope.')
            seen.add(key)
            self.evict_completed.append(dict(eviction))

    def _request(self, method, fields, **kwargs):
        request = {'api': 'secret-manager-metadata', 'origin': 'https://secretmanager.googleapis.com',
                   'method': method, 'path': '/v1/' + self.name, 'params': {'fields': fields}}
        if method == 'PATCH':
            request['params']['updateMask'] = 'annotations'
            request['body'] = kwargs['body']
        try:
            response = self.transport(request)
        except Exception:
            raise RolloutError('Journal metadata request failed; reload before recovery.') from None
        shape(response, {'status', 'body'}, {'status', 'body'})
        if type(response['status']) is not int or response['status'] != 200:
            fail('Journal metadata request was rejected; no dependent Cloud Run write is permitted.')
        body = shape(response['body'], {'name', 'etag', 'annotations'} if method == 'GET' else {'name', 'etag'}, {'name', 'etag'})
        if body['name'] not in self.response_names:
            fail('Journal resource scope mismatch.')
        text(body['etag'])
        annotations = body.get('annotations', {})
        if not isinstance(annotations, dict) or len(annotations) > 100:
            fail()
        try:
            size = sum(len(key.encode('utf-8')) + len(value.encode('utf-8')) for key, value in annotations.items()
                       if isinstance(key, str) and isinstance(value, str))
        except UnicodeError:
            raise RolloutError('Invalid annotation metadata.') from None
        if any(not isinstance(key, str) or not isinstance(value, str) for key, value in annotations.items()) or size >= self.LIMIT:
            fail('Annotation metadata exceeds its supported bound.')
        return body, copy.deepcopy(annotations)

    def _decode(self, encoded):
        if encoded is None:
            return None, None
        if not isinstance(encoded, str) or encoded[:3] not in ('v1:', 'v2:') or len(encoded) >= self.LIMIT:
            fail('Invalid rollout annotation encoding.')
        try:
            parts = encoded[3:].split('.', 1)
            if len(parts) == 2 and any(character != '~' for character in parts[1]):
                fail('Invalid journal reservation padding.')
            packed = base64.b64decode(parts[0], validate=True)
            decoder = zlib.decompressobj()
            raw = decoder.decompress(packed, MAX_BYTES + 1)
            if len(raw) > MAX_BYTES or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
                fail()
            if encoded.startswith('v1:'):
                record = decode_journal_bytes(raw)
            else:
                record = parse_scoped_record(raw, scope(self.profile, self.preview_id)[3])
        except (ValueError, UnicodeError, zlib.error, RecursionError):
            raise RolloutError('Invalid bounded rollout annotation.') from None
        validate_record(record)
        if record['profile'] != self.profile or record['preview_id'] != self.preview_id:
            fail('Journal does not belong to the requested service.')
        return record, hashlib.sha256(raw).hexdigest()

    def load(self):
        _, annotations = self._request('GET', 'name,etag,annotations')
        return self._decode(annotations.get(self.key))

    def save(self, record, expected):
        if record.get('profile') != self.profile or record.get('preview_id') != self.preview_id:
            fail()
        journal_bytes(record)  # Preserve the independent file/logical bound.
        try:
            raw = scoped_record_bytes(record, scope(self.profile, self.preview_id)[3])
            if parse_scoped_record(raw, scope(self.profile, self.preview_id)[3]) != record:
                fail()
        except Exception:
            raise RolloutError('Invalid scoped journal encoding.') from None
        body, annotations = self._request('GET', 'name,etag,annotations')
        previous, actual = self._decode(annotations.get(self.key))
        if actual != expected:
            fail('Service journal changed concurrently; resume the stored transaction.')
        accepting_new_stage = (record['phase'] == 'stage_intent'
            and (previous is None or (previous['phase'] in ('complete', 'rolled_back')
                                      and previous['transaction_id'] != record['transaction_id'])))
        if self.consume_terminal is not None and accepting_new_stage:
            # CI provides a pure ordinary-journal decoder/validator bound to an
            # independently verified terminal snapshot. It must check complete,
            # profile/service, and exact new record.base equality, then return
            # its logical checksum. Only this same-service key can be consumed,
            # in the very PATCH that durably accepts the new stage intent.
            transfer = self.consume_terminal
            if not isinstance(annotations.get(transfer['key']), str):
                fail('The terminal source journal is unavailable for atomic transfer.')
            try:
                accepted = transfer['validate'](annotations[transfer['key']], copy.deepcopy(record))
            except Exception:
                raise RolloutError('The terminal source journal could not be verified.') from None
            if accepted != transfer['checksum']:
                fail('The terminal source journal changed; no transfer was committed.')
            del annotations[transfer['key']]
        if accepting_new_stage:
            for eviction in self.evict_completed:
                encoded_source = annotations.get(eviction['key'])
                if not isinstance(encoded_source, str):
                    fail('A completed checkpoint changed before capacity reclamation.')
                try:
                    # The collector's pure validator must require COMPLETE,
                    # exact scope and checksum, after a fresh read-only proof of
                    # positively serving immutable identity/configuration. An
                    # incomplete or rolled_back witness must never be evicted.
                    accepted = eviction['validate'](encoded_source, copy.deepcopy(record))
                except Exception:
                    raise RolloutError('A completed checkpoint could not be verified.') from None
                if accepted != eviction['checksum']:
                    fail('A completed checkpoint changed; no eviction was committed.')
                del annotations[eviction['key']]
        encoded = 'v2:' + base64.b64encode(zlib.compress(raw, level=9)).decode('ascii')
        if record['phase'] not in ('complete', 'rolled_back'):
            reserve = reservation_bytes(record)
            if len(encoded) + 1 > reserve:
                fail('Recovery reservation is insufficient; no dependent cloud write is permitted.')
            encoded += '.' + '~'*(reserve - len(encoded) - 1)
        annotations[self.key] = encoded
        if len(annotations) > 100:
            fail('Journal annotation count exceeds its bound.')
        if sum(len(key.encode('utf-8')) + len(value.encode('utf-8')) for key, value in annotations.items()) >= self.LIMIT:
            fail('Journal container metadata is full; no Cloud Run side effect was attempted.')
        self._request('PATCH', 'name,etag', body={'name': self.name, 'etag': body['etag'], 'annotations': annotations})
        return hashlib.sha256(raw).hexdigest()


class RolloutExecutor:
    """One bounded advance per call; callers wait/reinvoke while retaining the lock.

    transport(request) -> {status, body}, journal.load() -> (record, checksum),
    journal.save(record, expected_checksum) -> new_checksum. A save must be durable.
    lifecycle(profile) -> complete fixed-repository branch/PR snapshot from the
    inventory adapter. Every side effect rechecks the expected head/state.
    """
    def __init__(self, transport, journal, lifecycle):
        if not callable(transport) or not callable(lifecycle):
            fail()
        self.transport, self.journal, self.lifecycle = transport, journal, lifecycle

    def _lock(self, held, group):
        try:
            plan._lock(held, group)
        except Exception:
            raise RolloutError('The repository-wide mutation lock is required.') from None

    def _request(self, request, allow_404=False):
        try:
            result = self.transport(request)
        except Exception:
            raise RolloutError('Cloud request outcome is uncertain; resume from the journal.') from None
        shape(result, {'status', 'body'}, {'status', 'body'})
        if type(result['status']) is not int:
            fail()
        if result['status'] == 404 and allow_404:
            return None
        if result['status'] != 200:
            fail('Cloud request did not succeed; journal and old versions were retained.')
        if not isinstance(result['body'], dict):
            fail()
        return result['body']

    def _service(self, record):
        full = scope(record['profile'], record['preview_id'])[3]
        raw = self._request({'api': 'cloud-run-v2', 'origin': ORIGIN, 'method': 'GET',
                             'path': '/v2/' + full, 'params': {'fields': WARMUP_SERVICE_FIELDS}})
        state = parse_service(raw, record['profile'], record['preview_id'])
        if 'base' in record:
            base = parse_service(record['base'], record['profile'], record['preview_id'])
            if state['uri'] != base['uri'] or state['ingress'] != base['ingress']:
                fail('Service endpoint or ingress changed; no route was warmed.')
        return raw, state

    def _revision_ready(self, record, candidate):
        full = scope(record['profile'], record['preview_id'])[3]
        name = revision_name(candidate['name'], full)
        raw = self._request({'api': 'cloud-run-v2', 'origin': ORIGIN, 'method': 'GET',
                             'path': '/v2/' + full + '/revisions/' + name,
                             'params': {'fields': REVISION_READINESS_FIELDS}})
        return revision_ready(raw, record['profile'], record['preview_id'], name, candidate['uid'])

    def _warm(self, record, state):
        """One status-only request per advance, through an authenticated owned route."""
        full = scope(record['profile'], record['preview_id'])[3]
        name = self._desired(record, 'stage')[0]
        raw = self._request({'api': 'cloud-run-v2', 'origin': ORIGIN, 'method': 'GET',
                             'path': '/v2/' + full, 'params': {'fields': ROUTE_FIELDS}})
        shape(raw, {'name', 'uid', 'generation', 'trafficStatuses'}, {'name', 'uid', 'generation', 'trafficStatuses'})
        if raw['name'] != full or raw['uid'] != state['uid'] or raw['generation'] != str(state['generation']):
            fail('Warmup route identity changed.')
        rows = array(raw['trafficStatuses'])
        matches = []
        for row in rows:
            shape(row, {'type', 'revision', 'percent', 'tag', 'uri'}, {'type'})
            if row.get('tag') == record['temporary_tag']:
                matches.append(row)
        clean = [{key: value for key, value in row.items() if key != 'uri'} for row in rows]
        if traffic(clean, full, state['ready']) != state['actual'] or len(matches) != 1:
            fail('Warmup route distribution does not match the saved stage.')
        route = matches[0]
        if (route.get('percent', 0) != 0 or route['type'] != REVISION_TYPE
                or revision_name(route.get('revision'), full) != name):
            fail('Warmup tag is not bound to the exact zero-traffic candidate.')
        if 'uri' not in route:
            return False
        uri = public_origin(route['uri'], full.rsplit('/', 1)[-1], record['temporary_tag'])
        if uri != 'https://' + record['temporary_tag'] + '---' + urlsplit(state['uri']).hostname:
            fail('Warmup route does not belong to the verified service endpoint.')
        if self._service(record)[1] != state:
            fail('Service changed before the readiness request.')
        request = {'api': 'cloud-run-readiness', 'origin': uri, 'method': 'HEAD',
                   'path': '/actuator/health/readiness', 'service': full, 'service_uid': state['uid'],
                   'revision': name, 'tag': record['temporary_tag'], 'base_uri': state['uri']}
        try:
            response = self.transport(request)
        except Exception:
            raise RolloutError('Readiness request failed; the zero-traffic checkpoint was retained.') from None
        shape(response, {'status', 'body'}, {'status', 'body'})
        if type(response['status']) is not int or response['status'] not in (0, 200, 503) or response['body'] != {}:
            fail('Readiness response metadata is invalid.')
        if self._service(record)[1] != state:
            fail('Service changed during the readiness request.')
        # Only the transport's sanitized connection/timeout sentinel and the
        # application's not-ready status are retryable. Auth, redirects, scope,
        # certificate, and every other failure remain closed. The caller owns
        # the bounded retry budget; this call never loops or changes traffic.
        return response['status'] == 200

    def _revision(self, record, name, allow_404=False):
        full = scope(record['profile'], record['preview_id'])[3]
        raw = self._request({'api': 'cloud-run-v2', 'origin': ORIGIN, 'method': 'GET',
                             'path': '/v2/' + full + '/revisions/' + name, 'params': {'fields': REVISION_FIELDS}}, allow_404)
        if raw is not None:
            parse_revision(raw, record['profile'], record['preview_id'], name)
        return raw

    def _lifecycle(self, record):
        try:
            snapshot = self.lifecycle(record['profile'])
            if record['profile'] in ('branch-preview', 'pr-preview'):
                plan._check_target(record['profile'], record['preview_id'], record['raw_branch'], record['head_sha'], snapshot, False)
            else:
                plan._shape(snapshot, ('repository', 'complete', 'items'))
                if snapshot['repository'] != plan.REPOSITORY or snapshot['complete'] is not True:
                    fail()
                matches = [item for item in plan._list(snapshot['items']) if item.get('name') == record['raw_branch']]
                if len(matches) != 1 or matches[0] != {'name': record['raw_branch'], 'head_sha': record['head_sha']}:
                    fail()
        except Exception:
            raise RolloutError('Branch/PR lifecycle changed or cannot be verified; promotion stopped.') from None

    def _save(self, record, checksum, **changes):
        updated = copy.deepcopy(record)
        updated.update(changes)
        validate_record(updated)
        if updated['phase'] not in ('complete', 'rolled_back'):
            # The raw checkpoint bound applies to every journal implementation,
            # including local files. SM additionally reserves aggregate quota.
            reservation_bytes(updated)
        try:
            checksum = self.journal.save(updated, checksum)
        except Exception:
            raise RolloutError('Durable journal checkpoint failed; no dependent cloud write attempted.') from None
        return updated, checksum

    def begin(self, profile, target_version, transaction_id, base_proof, *, preview_id=None, raw_branch=None,
              head_sha, replace_terminal=False, lock_held=False, lock_group=''):
        self._lock(lock_held, lock_group)
        existing, checksum = self.journal.load()
        if existing is not None:
            validate_record(existing)
            if replace_terminal is not True or existing['phase'] not in ('complete', 'rolled_back'):
                fail('A journal already exists; resume it instead of starting another rollout.')
            if existing['profile'] != profile or existing['preview_id'] != preview_id or existing['transaction_id'] == transaction_id:
                fail('A terminal journal can only be replaced by a new transaction for the same service.')
        elif checksum is not None:
            fail()
        record = {'schema': 2, 'profile': profile, 'preview_id': preview_id, 'raw_branch': raw_branch,
                  'head_sha': head_sha, 'transaction_id': transaction_id, 'target_version': version(target_version),
                  'temporary_tag': 'r' + transaction_id[:9] if isinstance(transaction_id, str) else None}
        # Scope and transaction validation happen before even a read request.
        _, _, service, _ = scope(profile, preview_id)
        if not isinstance(transaction_id, str) or not re.fullmatch(r'[a-f0-9]{12}', transaction_id) or len(service + '-sbr-' + transaction_id) > 63:
            fail()
        self._lifecycle(record)
        raw, state = self._service(record)
        if existing is not None:
            prior = parse_service(existing['base'], profile, preview_id)
            last_intent = parse_service(existing['intent_base'], profile, preview_id)
            if state['uid'] != prior['uid'] or state['generation'] < last_intent['generation'] + 1:
                fail('Terminal journal identity cannot be adopted after recreation or stale observations.')
        if not ready(state, state['created']):
            fail('The service is not quiescent and ready.')
        require_readiness_startup_probe(state['containers'][0])
        original = self._revision(record, state['created'])
        original_pin, original_volumes, original_containers = parse_revision(original, profile, preview_id, state['created'])
        if (original_pin, original_volumes, original_containers) != (state['pin'], state['volumes'], state['containers']):
            fail('Current revision and template differ.')
        proof_keys = {'verified_active', 'ownership_verified', 'profile', 'preview_id', 'service_uid', 'revision_uid', 'bundle_version'}
        shape(base_proof, proof_keys, proof_keys)
        if base_proof != {'verified_active': True, 'ownership_verified': True, 'profile': profile, 'preview_id': preview_id,
                          'service_uid': state['uid'], 'revision_uid': original['uid'], 'bundle_version': state['pin']}:
            fail('Trusted ownership and active-loader provenance are required.')
        if type(base_proof['verified_active']) is not bool or type(base_proof['ownership_verified']) is not bool:
            fail()
        if not any(row['revision'] == state['created'] and row['percent'] for row in state['traffic']):
            fail('Current revision has no traffic to promote; explicit review is required.')
        fresh, fresh_state = self._service(record)
        if fresh_state != state:
            fail('Service changed while preparing the rollout.')
        self._lifecycle(record)
        if target_version == state['pin']:
            return {'status': 'unchanged', 'version': target_version}
        if state['uri'] is None or state['ingress'] != 'INGRESS_TRAFFIC_ALL':
            fail('An existing public service is required for the isolated readiness probe.')
        if any(row.get('tag') == record['temporary_tag'] for row in state['traffic']):
            fail('The intended warmup tag already exists; it was not adopted or replaced.')
        # Keep the immediate predecessor's rollback/provenance metadata bounded.
        # Older actual revisions and secret versions remain untouched and are
        # still scanned by inventory; terminal history is never an allowlist for
        # retirement. Appending every prior transaction would exhaust the shared
        # metadata container despite successful serial rollouts.
        history = []
        if existing is not None:
            references = [('base_revision', True), ('candidate', existing['candidate_verified']), ('recovery_candidate', False)]
            for key, verified in references:
                reference = existing[key]
                if reference is None:
                    continue
                name = revision_name(reference['name'], scope(profile, preview_id)[3])
                pin = parse_revision(reference, profile, preview_id, name)[0]
                entry = {'revision': name, 'uid': reference['uid'], 'createTime': reference['createTime'],
                         'bundle_version': pin, 'verified_active': verified}
                previous = next((value for value in history if value['revision'] == name), None)
                if previous is not None:
                    if any(previous[key] != entry[key] for key in ('uid', 'createTime', 'bundle_version')):
                        fail('Retained revision identity changed.')
                    previous['verified_active'] |= verified
                else:
                    history.append(entry)
        record.update(base=raw, base_revision=original, phase='stage_intent', intent_base=raw, candidate=None,
                      recovery_candidate=None, candidate_verified=False, candidate_warmed=False,
                      retained_revisions=history, rollback_origin=None, operation=None)
        # Reserve an actual traffic-row slot before accepting a new journal.
        self._desired(record, 'stage')
        self._save(record, checksum)
        return {'status': 'prepared', 'revision': service + '-sb-' + transaction_id}

    def _desired(self, record, operation):
        profile, identifier = record['profile'], record['preview_id']
        base = parse_service(record['base'], profile, identifier)
        service = scope(profile, identifier)[2]
        staged_name = service + '-sb-' + record['transaction_id']
        volumes = copy.deepcopy(base['volumes'])
        volumes[0]['secret']['items'][0]['version'] = record['target_version']
        distribution = copy.deepcopy(base['traffic'])
        if operation == 'stage':
            distribution.append({'type': REVISION_TYPE, 'revision': staged_name, 'percent': 0, 'tag': record['temporary_tag']})
            distribution = traffic(distribution, scope(profile, identifier)[3], None)
        if operation == 'promote':
            percent = sum(row['percent'] for row in distribution if row['revision'] == base['created'])
            distribution = [row for row in distribution if row['revision'] != base['created'] or row.get('tag')]
            distribution.append({'type': REVISION_TYPE, 'revision': staged_name, 'percent': percent})
            distribution = traffic(distribution, scope(profile, identifier)[3], None)
        if operation == 'rollback':
            volumes = base['volumes']
            staged_name = service + '-sbr-' + record['transaction_id']
        return staged_name, volumes, distribution

    def _matches(self, record, state, operation, *, intent_generation=None):
        profile, identifier = record['profile'], record['preview_id']
        base = parse_service(record['base'], profile, identifier)
        generation = (parse_service(record['intent_base'], profile, identifier)['generation']
                      if intent_generation is None else intent_generation)
        name, volumes, distribution = self._desired(record, operation)
        return (state['uid'] == base['uid'] and state['generation'] == generation + 1
                and state['template'] == name and state['volumes'] == volumes and state['containers'] == base['containers']
                and state['traffic'] == distribution)

    def _patch(self, record, operation, state):
        full = scope(record['profile'], record['preview_id'])[3]
        name, volumes, distribution = self._desired(record, operation)
        body = {'name': full, 'etag': state['etag'], 'traffic': distribution}
        mask = 'traffic'
        if operation != 'promote':
            body['template'] = {'revision': name, 'volumes': volumes}
            mask = 'template.revision,template.volumes,traffic'
        result = self._request({'api': 'cloud-run-v2', 'origin': ORIGIN, 'method': 'PATCH', 'path': '/v2/' + full,
            'params': {'updateMask': mask, 'fields': 'name', 'allowMissing': False, 'forceNewRevision': False}, 'body': body})
        shape(result, {'name'}, {'name'})
        return operation_name(result['name'], full)

    def _operation_status(self, record):
        if record['operation'] is None:
            return 'waiting_for_accepted_write'
        body = self._request({'api': 'cloud-run-v2', 'origin': ORIGIN, 'method': 'GET',
            'path': '/v2/' + record['operation'], 'params': {'fields': 'name,done,error(code)'}})
        shape(body, {'name', 'done', 'error'}, {'name'})
        if body['name'] != record['operation'] or type(body.get('done', False)) is not bool:
            fail()
        if 'error' in body:
            error = shape(body['error'], {'code'}, {'code'})
            if type(error['code']) is not int or error['code'] <= 0 or body.get('done') is not True:
                fail()
            return 'operation_failed'
        return 'waiting_for_service_observation' if body.get('done') else 'waiting_for_accepted_write'

    def _capture_staged(self, record):
        name, volumes, _ = self._desired(record, 'stage')
        observed = self._revision(record, name, allow_404=True)
        if observed is None:
            return None
        pin, actual_volumes, containers = parse_revision(observed, record['profile'], record['preview_id'], name)
        base = parse_service(record['base'], record['profile'], record['preview_id'])
        if actual_volumes != volumes or containers != base['containers'] or (record['candidate'] is not None and record['candidate'] != observed):
            fail('Staged revision metadata cannot be reconciled safely.')
        return observed

    def _load(self):
        try:
            record, checksum = self.journal.load()
            validate_record(record)
            if record['phase'] not in ('complete', 'rolled_back'):
                reservation_bytes(record)
            if not isinstance(checksum, str) or not re.fullmatch(r'[0-9a-f]{64}', checksum):
                fail()
            return record, checksum
        except Exception:
            raise RolloutError('A valid persisted rollout journal is required.') from None

    def advance(self, *, lock_held=False, lock_group=''):
        self._lock(lock_held, lock_group)
        record, checksum = self._load()
        phase = record['phase']
        operation = 'stage' if phase.startswith('stage') else 'rollback' if phase.startswith('rollback') or phase == 'rolled_back' else 'promote'
        raw, state = self._service(record)
        if operation != 'rollback':
            require_readiness_startup_probe(state['containers'][0])
        if phase.endswith('_intent'):
            operation_id = record['operation']
            if not self._matches(record, state, operation):
                expected = parse_service(record['intent_base'], record['profile'], record['preview_id'])
                if state != expected:
                    origin = record['rollback_origin']
                    if operation == 'rollback' and origin is not None and not origin['consumed']:
                        if self._matches(record, state, origin['operation'], intent_generation=origin['base']['generation']):
                            if state['reconciling'] or state['condition'] in ('CONDITION_PENDING', 'CONDITION_RECONCILING'):
                                return {'status': 'waiting_for_origin_reconciliation'}
                            candidate = self._capture_staged(record)
                            if candidate is None:
                                return {'status': 'waiting_for_revision_before_rollback'}
                            self._save(record, checksum, intent_base=raw, candidate=candidate,
                                       rollback_origin={**origin, 'consumed': True}, operation=None)
                            return {'status': 'rollback_rebased_after_origin_write'}
                    fail('Conditional write preconditions changed; old traffic and pins were retained.')
                if operation != 'rollback':
                    self._lifecycle(record)
                if operation == 'promote':
                    name = self._desired(record, operation)[0]
                    candidate = self._revision(record, name)
                    if candidate != record['candidate'] or not staged_ready(state, name) or not self._revision_ready(record, candidate):
                        fail('Promotion evidence is stale or the revision is not ready.')
                    proof = verify_activation(self.transport, record['profile'], name, candidate['createTime'], record['preview_id'])
                    if proof.get('verified_active') is not True:
                        return {'status': 'waiting_for_activation', 'reason': proof['reason']}
                    fresh, fresh_state = self._service(record)
                    if fresh_state != state:
                        fail('Service changed before conditional promotion.')
                    self._lifecycle(record)
                operation_id = self._patch(record, operation, state)
            self._save(record, checksum, phase=operation + '_wait', operation=operation_id)
            return {'status': operation + '_requested'}
        if not self._matches(record, state, operation):
            expected = parse_service(record['intent_base'], record['profile'], record['preview_id'])
            if state == expected and phase.endswith('_wait'):
                return {'status': self._operation_status(record)}
            fail('Service UID, generation, configuration, or traffic drifted; automatic recovery stopped.')
        target_name = self._desired(record, operation)[0]
        candidate = self._revision(record, target_name, allow_404=True)
        if candidate is None:
            return {'status': 'waiting_for_revision'}
        key = 'recovery_candidate' if operation == 'rollback' else 'candidate'
        existing = record[key]
        if existing is not None and candidate != existing:
            fail('The immutable revision identity/configuration changed.')
        pin, volumes, containers = parse_revision(candidate, record['profile'], record['preview_id'], target_name)
        if operation != 'rollback':
            require_readiness_startup_probe(containers[0])
        if volumes != state['volumes'] or containers != state['containers']:
            fail('Created revision does not match the intended numeric mount.')
        if existing is None:
            record, checksum = self._save(record, checksum, **{key: candidate})
        platform_ready = staged_ready(state, target_name) if operation in ('stage', 'rollback') else ready(state, target_name)
        if not platform_ready:
            return {'status': 'revision_failed' if state['condition'] == 'CONDITION_FAILED' else 'waiting_for_readiness'}
        if phase in ('complete', 'rolled_back'):
            return {'status': phase, 'revision': target_name, 'version': pin}
        if operation == 'rollback':
            if self._revision(record, revision_name(record['base_revision']['name'], scope(record['profile'], record['preview_id'])[3])) != record['base_revision']:
                fail('Original rollback revision metadata changed.')
            if not self._revision_ready(record, record['base_revision']):
                return {'status': 'waiting_for_original_readiness'}
            self._save(record, checksum, phase='rolled_back')
            return {'status': 'rolled_back', 'revision': target_name, 'version': pin}
        self._lifecycle(record)
        if not self._revision_ready(record, candidate):
            return {'status': 'waiting_for_revision_readiness'}
        if operation == 'stage' and not record['candidate_warmed']:
            if not self._warm(record, state):
                return {'status': 'waiting_for_http_readiness'}
            record, checksum = self._save(record, checksum, candidate_warmed=True)
        proof = verify_activation(self.transport, record['profile'], target_name, candidate['createTime'], record['preview_id'])
        if proof.get('verified_active') is not True:
            return {'status': 'waiting_for_activation', 'reason': proof['reason']}
        fresh, fresh_state = self._service(record)
        if fresh_state != state or self._revision(record, target_name) != candidate:
            fail('Readiness/evidence observations became stale.')
        self._lifecycle(record)
        if operation == 'promote':
            self._save(record, checksum, phase='complete')
            return {'status': 'complete', 'revision': target_name, 'version': pin}
        self._save(record, checksum, phase='promote_intent', intent_base=fresh, candidate_verified=True, operation=None)
        return {'status': 'verified_ready_for_promotion', 'revision': target_name, 'version': pin}

    def rollback(self, *, lock_held=False, lock_group=''):
        """Prepare a compensating restore; later rollback requires free journal space.

        Successful preparation physically reserves every remaining checkpoint.
        Failure to reserve makes no Cloud Run change and leaves the old journal.
        """
        self._lock(lock_held, lock_group)
        record, checksum = self._load()
        if record['phase'].startswith('rollback') or record['phase'] == 'rolled_back':
            return self.advance(lock_held=lock_held, lock_group=lock_group)
        raw, state = self._service(record)
        operation = 'stage' if record['phase'].startswith('stage') else 'promote'
        origin_base = parse_service(record['intent_base'], record['profile'], record['preview_id'])
        landed = self._matches(record, state, operation)
        if not landed and state != origin_base:
            fail('Unknown drift cannot be adopted for rollback.')
        if state['reconciling'] or state['condition'] in ('CONDITION_PENDING', 'CONDITION_RECONCILING'):
            return {'status': 'waiting_for_reconciliation_before_rollback'}
        old = parse_service(record['base'], record['profile'], record['preview_id'])
        if self._revision(record, old['created']) != record['base_revision']:
            fail('Original rollback revision identity is unavailable or changed.')
        candidate = self._capture_staged(record)
        if candidate is None and (landed or operation == 'promote'):
            return {'status': 'waiting_for_revision_before_rollback'}
        # Configuration/traffic expectations are derived from the immutable base
        # and operation. Preserve only the original CAS identity here, avoiding
        # a third redundant service snapshot in bounded annotation storage.
        origin = {'operation': operation, 'base': {key: origin_base[key] for key in ('uid', 'generation', 'etag')}, 'consumed': landed}
        self._save(record, checksum, phase='rollback_intent', intent_base=raw, rollback_origin=origin,
                   candidate=candidate, operation=None)
        return {'status': 'rollback_prepared'}

    def terminal_handoff(self, *, use_persisted_activation=False, lock_held=False, lock_group=''):
        """Read-only provenance for atomic transfer into an ordinary deployment.

        Rollback leaves a zero-traffic recovery template while the saved original
        revision serves. A new deploy may adopt that exact state only after this
        check. The caller must recheck the service snapshot and atomically save
        its durable stage intent AND remove this journal in one Secret metadata
        CAS, checking journal_checksum. Never delete the journal first: a crash
        would lose the evidence that permits adoption. This method performs no
        journal/runtime write and does not authorize a stale lifecycle deployment.

        A caller reading the authenticated durable journal can explicitly reuse
        its activation proof after event retention expires. COMPLETE includes a
        persisted candidate_verified gate; rollback's original base_revision was
        bound to verified_active provenance before the first intent was saved.
        This option never relaxes immutable identity/configuration/readiness or
        changes the fresh event requirement for any newly staged candidate.
        """
        self._lock(lock_held, lock_group)
        if type(use_persisted_activation) is not bool:
            fail('Persisted activation reuse must be explicitly selected.')
        record, checksum = self._load()
        if record['phase'] not in ('complete', 'rolled_back'):
            fail('A pending mount rollout cannot be handed to ordinary deployment.')
        operation = 'rollback' if record['phase'] == 'rolled_back' else 'promote'
        raw, state = self._service(record)
        target = self._desired(record, operation)[0]
        if (not self._matches(record, state, operation)
                or not (staged_ready(state, target) if operation == 'rollback' else ready(state, target))):
            fail('Terminal rollout state drifted; ordinary deployment adoption stopped.')
        created = record['recovery_candidate'] if operation == 'rollback' else record['candidate']
        if created is None or self._revision(record, target) != created:
            fail('Terminal revision identity changed; ordinary deployment adoption stopped.')
        pin, volumes, containers = parse_revision(created, record['profile'], record['preview_id'], target)
        if (pin, volumes, containers) != (state['pin'], state['volumes'], state['containers']):
            fail()
        serving = record['base_revision'] if operation == 'rollback' else record['candidate']
        full = scope(record['profile'], record['preview_id'])[3]
        name = revision_name(serving['name'], full)
        if any(not any(row['revision'] == name and row['percent'] > 0 for row in distribution)
               for distribution in (state['traffic'], state['actual'])):
            fail('Saved serving revision has no verified traffic.')
        if self._revision(record, name) != serving or not self._revision_ready(record, serving):
            fail('Saved serving revision is unavailable or not ready.')
        if not use_persisted_activation and verify_activation(self.transport, record['profile'], name, serving['createTime'], record['preview_id']).get('verified_active') is not True:
            fail('Saved serving revision activation could not be verified.')
        if self._service(record)[1] != state or self._revision(record, name) != serving or self._load()[1] != checksum:
            fail('Terminal handoff observations became stale.')
        return {'schema': 1, 'profile': record['profile'], 'preview_id': record['preview_id'],
                'phase': record['phase'], 'journal_key': 'modtale-rollout-' + full.rsplit('/', 1)[-1],
                'journal_checksum': checksum, 'bundle_version': state['pin'], 'service': raw,
                'serving_revision': name, 'serving_metadata': copy.deepcopy(serving)}
