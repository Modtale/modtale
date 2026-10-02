"""Credential-free, resumable gate for ordinary opted-in backend deployments."""
import base64
import copy
import hashlib
import json
import re
import time
import zlib

from configure_secret_bundle import plan as runtime_plan
from secret_bundle import TARGETS, numeric_version
from secret_bundle_journal_codec import scoped_record_bytes, parse_scoped_record
from secret_bundle_activation_evidence import verify_activation
from secret_bundle_ci import CIError, LOCK, fail
from secret_bundle_rollout import (RolloutExecutor, SecretManagerJournal, SERVICE_FIELDS, REVISION_FIELDS, parse_service,
                                   parse_revision, ready, scope, REVISION_TYPE, require_readiness_startup_probe, operation_name, staged_ready, revision_ready, REVISION_READINESS_FIELDS, public_origin)

DEPLOY_SERVICE_FIELDS = SERVICE_FIELDS + ',uri,ingress'
TAG_ROUTE_FIELDS = 'name,uid,generation,trafficStatuses(type,revision,percent,tag,uri)'
MAX_OBSOLETE_TAGS = 8
RUNTIME_IDENTITIES = {
    'prod': 'modtale-prod-runtime@gen-lang-client-0244308719.iam.gserviceaccount.com',
    'dev': 'modtale-dev-runtime@gen-lang-client-0244308719.iam.gserviceaccount.com',
    'branch-preview': 'modtale-branch-preview-runtime@gen-lang-client-0244308719.iam.gserviceaccount.com',
    'pr-preview': 'modtale-pr-preview-runtime@modtale-pr-preview.iam.gserviceaccount.com',
}
READINESS_STARTUP_PROBE = ('httpGet.path=/actuator/health/readiness,httpGet.port=8080,'
                           'initialDelaySeconds=0,periodSeconds=10,timeoutSeconds=5,failureThreshold=24')
PHASES = {'stage_intent', 'stage_wait', 'promote_intent', 'promote_wait', 'complete'}
MAX_RECORD_BYTES = 65536
MAX_ETAG_BYTES = 1024
MAX_RETAINED_REVISIONS = 1


def raw_service(raw):
    return {key: value for key, value in raw.items() if key not in ('uri', 'ingress')}


def _encode_etags(value):
    if isinstance(value, list):
        return [_encode_etags(item) for item in value]
    if isinstance(value, dict):
        result = {}
        for key, item in value.items():
            if key == 'etag' and isinstance(item, str):
                raw = item.encode('utf-8')
                if len(raw) > MAX_ETAG_BYTES:
                    fail()
                result[key] = {'base64': base64.b64encode(raw).decode('ascii')}
            else:
                result[key] = _encode_etags(item)
        return result
    return value


def _decode_etags(value):
    if isinstance(value, list):
        return [_decode_etags(item) for item in value]
    if isinstance(value, dict):
        result = {}
        for key, item in value.items():
            if key == 'etag' and isinstance(item, dict) and set(item) == {'base64'}:
                raw = base64.b64decode(item['base64'], validate=True)
                if len(raw) > MAX_ETAG_BYTES:
                    fail()
                result[key] = raw.decode('utf-8')
            else:
                result[key] = _decode_etags(item)
        return result
    return value


def deployment_record_bytes(record):
    if record.get('schema') == 3:
        return scoped_record_bytes(record, scope(record['profile'], record['preview_id'])[3])
    # Opaque etags remain byte-exact after decoding. Their storage representation
    # has a known4/3 bound, including strings containing JSON-escape characters.
    return json.dumps(_encode_etags(record), ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode('utf-8')


def deployment_reservation_bytes(record):
    """Reserve a bounded, uncompressed worst-shaped remaining checkpoint.

    Fixed base/serving metadata is retained verbatim. The new image and numeric
    pin are fixed before staging; only bounded API metadata is unknown. Full
    resource names, maximal UID/etag JSON escaping, optional metadata fields,
    canonical traffic rows and codec overhead are budgeted without assuming a
    compression ratio. The bound is stable across all transaction phases.
    """
    _, _, _, full = scope(record['profile'], record['preview_id'])
    boundary = 'shared' if record['profile'] in ('prod', 'dev') else record['profile']
    project, secret = TARGETS[boundary]
    qualified = full + '/revisions/' + record['revision']
    name = '"' * 63
    container = {'name': name, 'image': record['expected_image'],
                 'volumeMounts': [{'name': name, 'mountPath': '/app/secrets/bundles', 'subPath': ''}],
                 'startupProbe': {'httpGet': {'path': '/actuator/health/readiness', 'port': 8080, 'httpHeaders': []},
                                  'initialDelaySeconds': 0, 'periodSeconds': 10, 'timeoutSeconds': 5, 'failureThreshold': 24}}
    volumes = [{'name': name, 'secret': {'secret': f'projects/{project}/secrets/{secret}', 'defaultMode': 511,
                                       'items': [{'path': boundary + '.json', 'version': record['pin'], 'mode': 511}]}}]
    candidate = {'name': qualified, 'uid': '"' * 128, 'service': full,
                 'createTime': '2026-12-31T23:59:59.123456789Z', 'containers': [container], 'volumes': volumes}
    if record['base'] is not None:
        base = parse_service(raw_service(record['base']), record['profile'], record['preview_id'])
        targets = copy.deepcopy(base['traffic'])
        uid = record['base']['uid']
        uri, ingress = record['base']['uri'], record['base'].get('ingress', '')
    else:
        targets = [{'type': REVISION_TYPE, 'revision': record['revision'], 'percent': 100}]
        uid, uri, ingress = '"' * 128, 'u' * 1024, 'INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER'
    for target in targets:
        target['revision'] = full + '/revisions/' + target['revision']
        target.setdefault('tag', '')
    stage_targets = copy.deepcopy(targets)
    if record['schema'] in (2, 3):
        stage_targets.append({'type': REVISION_TYPE, 'revision': qualified, 'percent': 0, 'tag': record['temporary_tag']})
    staged = {'name': full, 'uid': uid, 'etag': '"' * MAX_ETAG_BYTES,
              'generation': '9' * 18, 'observedGeneration': '9' * 18, 'reconciling': False,
              'terminalCondition': {'state': 'CONDITION_RECONCILING'},
              'latestCreatedRevision': qualified, 'latestReadyRevision': qualified,
              'traffic': stage_targets, 'trafficStatuses': copy.deepcopy(stage_targets),
              'template': {'revision': qualified, 'containers': [container], 'volumes': volumes},
              'uri': uri, 'ingress': ingress}
    future = copy.deepcopy(record)
    future.update(phase='promote_intent', staged=staged, candidate=candidate,
                  desired=copy.deepcopy(targets), reservation_bytes=16383,
                  operation=full.rsplit('/services/', 1)[0] + '/operations/' + 'o' * 128)
    #256 bytes cover bounded optional/default spelling differences. zlib
    # output cannot expand this <=64KiB input beyond the additional128 bytes.
    raw_limit = len(deployment_record_bytes(future)) + 256
    if raw_limit > MAX_RECORD_BYTES:
        fail()
    return 3 + 4 * ((raw_limit + 128 + 2) // 3) + 1


class DeploymentJournal:
    """One bounded annotation per service. No payloads, credentials, or CLI args."""
    def __init__(self, ci, profile=None, preview_id=None):
        self.ci = ci
        self.profile = profile or ci.profile
        self.preview_id = ci.preview_id if profile is None else preview_id
        boundary, _, service, _ = scope(self.profile, self.preview_id)
        project, secret = TARGETS[boundary]
        self.name = f'projects/{project}/secrets/{secret}'
        self.key = 'modtale-deploy-' + service
        self.consume_terminal = None
        self.evict_completed = []

    def request(self, method, *, body=None):
        req = {'api': 'secret-manager-metadata', 'origin': 'https://secretmanager.googleapis.com',
               'method': method, 'path': '/v1/' + self.name,
               'params': {'fields': 'name,etag,annotations' if method == 'GET' else 'name,etag'}}
        if body is not None:
            req.update(body=body)
            req['params']['updateMask'] = 'annotations'
        response = self.ci.transport(req)
        canonical = self.name.replace('/gen-lang-client-0244308719/', '/145553429208/').replace('/modtale-pr-preview/', '/759035195996/')
        if response['status'] != 200 or response['body'].get('name') not in (self.name, canonical):
            fail()
        return response['body']

    def decode(self, value):
        if value is None:
            return None, None
        if not isinstance(value, str) or not value.startswith('v1:') or len(value) >= 16384:
            fail()
        decoder = zlib.decompressobj()
        parts = value[3:].split('.', 1)
        if len(parts) == 2 and any(character != '~' for character in parts[1]):
            fail()
        raw = decoder.decompress(base64.b64decode(parts[0], validate=True), MAX_RECORD_BYTES + 1)
        if len(raw) > MAX_RECORD_BYTES or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
            fail()
        header = json.loads(raw)
        record = (parse_scoped_record(raw, scope(self.profile, self.preview_id)[3])
                  if isinstance(header, dict) and header.get('schema') == 3 else _decode_etags(header))
        allowed = {'schema', 'profile', 'preview_id', 'digest', 'revision', 'pin', 'head', 'base', 'phase', 'staged', 'candidate', 'desired', 'serving_revision', 'serving_metadata', 'retained_revisions', 'expected_image', 'reservation_bytes', 'operation'}
        if record.get('schema') in (2, 3):
            allowed |= {'temporary_tag', 'obsolete_tags'}
        if record.get('schema') == 3:
            allowed.update(('baseline_provenance', 'intent_kind'))
        if set(record) != allowed or record['schema'] not in (1, 2, 3) or record['profile'] != self.profile or record['preview_id'] != self.preview_id or record['phase'] not in PHASES:
            fail()
        if not re.fullmatch(r'[a-f0-9]{64}', record['digest']) or not re.fullmatch(r'[a-f0-9]{40}', record['head']):
            fail()
        if record['schema'] == 3 and record['baseline_provenance'] not in ('activation_event', 'completed_checkpoint', 'healthy_fallback_unverified', 'mount_handoff', 'no_service'):
            fail()
        if record['schema'] == 3 and record['intent_kind'] not in ('workflow', 'fanout_refresh'):
            fail()
        numeric_version(record['pin'])
        service = scope(self.profile, self.preview_id)[2]
        if record['revision'] != service + '-bd-' + record['digest'][:12]:
            fail()
        if record['schema'] in (2, 3):
            expected_tag = ('b' + record['digest'][:9]) if record['schema'] == 3 else ('sb-' + record['digest'][:12])
            if record['temporary_tag'] != expected_tag:
                fail()
            tags = record['obsolete_tags']
            if (not isinstance(tags, list) or len(tags) > MAX_OBSOLETE_TAGS or len(set(tags)) != len(tags)
                    or any(not isinstance(tag, str) or re.fullmatch(r'(?:sb-[a-f0-9]{12}|b[a-f0-9]{9})', tag) is None or tag == record['temporary_tag'] for tag in tags)):
                fail()
        if record['operation'] is not None:
            operation_name(record['operation'], scope(self.profile, self.preview_id)[3])
        if not isinstance(record['expected_image'], str) or re.fullmatch(r'[A-Za-z0-9._:/-]+@sha256:[a-f0-9]{64}', record['expected_image']) is None:
            fail()
        if record.get('intent_kind') == 'fanout_refresh' and (record['base'] is None
                or record['expected_image'] != record['base']['template']['containers'][0]['image']):
            fail()
        if not isinstance(record['retained_revisions'], list) or len(record['retained_revisions']) > MAX_RETAINED_REVISIONS:
            fail()
        reserve = record['reservation_bytes']
        if type(reserve) is not int or not 0 < reserve < 16384 or reserve != deployment_reservation_bytes(record):
            fail()
        return record, hashlib.sha256(raw).hexdigest()

    def load(self):
        metadata = self.request('GET')
        return self.decode(metadata.get('annotations', {}).get(self.key))

    def save(self, record, expected):
        raw = deployment_record_bytes(record)
        value = 'v1:' + base64.b64encode(zlib.compress(raw, 9)).decode()
        self.decode(value)  # Check metadata envelope before any side effect.
        if len(value) + 1 > record['reservation_bytes']:
            raise CIError('Deployment checkpoint exceeded its pre-mutation reservation; no dependent write attempted.')
        if record['phase'] != 'complete':
            value += '.' + '~' * (record['reservation_bytes'] - len(value) - 1)
        metadata = self.request('GET')
        annotations = metadata.get('annotations', {})
        _, checksum = self.decode(annotations.get(self.key))
        if checksum != expected:
            fail()
        if self.consume_terminal is not None:
            handoff = self.consume_terminal
            source = SecretManagerJournal(self.ci.transport, self.profile, self.preview_id)
            source_record, source_checksum = source._decode(annotations.get(source.key))
            if (record['phase'] != 'stage_intent' or handoff['journal_key'] != source.key
                    or source_checksum != handoff['journal_checksum'] or source_record is None
                    or source_record['phase'] not in ('complete', 'rolled_back')
                    or record['base'] != handoff['service']
                    or record['serving_revision'] != handoff['serving_revision']
                    or record['serving_metadata'] != handoff['serving_metadata']):
                raise CIError('Terminal mount provenance changed; no journal transfer was committed.')
            del annotations[source.key]
        for eviction in self.evict_completed:
            if (record['phase'] != 'stage_intent' or eviction['key'] == self.key
                    or eviction['validate'](annotations.get(eviction['key']), copy.deepcopy(record)) != eviction['checksum']):
                raise CIError('Completed checkpoint changed; eviction and dependent staging were stopped.')
            del annotations[eviction['key']]
        annotations[self.key] = value
        if len(annotations) > 100 or sum(len(k.encode()) + len(v.encode()) for k, v in annotations.items()) >= 16384:
            fail()
        self.request('PATCH', body={'name': self.name, 'etag': metadata['etag'], 'annotations': annotations})
        self.consume_terminal = None
        self.evict_completed = []
        return hashlib.sha256(raw).hexdigest()


def assert_no_pending_deployment(ci, profile, preview_id):
    record, _ = DeploymentJournal(ci, profile, preview_id).load()
    if record is not None and record['phase'] != 'complete':
        raise CIError('A backend deployment is pending; resume that deployment before publishing another pin.')



def readiness_startup_args(args):
    """All opted-in deployments hold startup until the HTTP readiness endpoint.

    Use a conservative 240-second budget (24 x 10), zero initial delay, and
    timeout strictly below period. Existing legacy/off workflows are unchanged.
    Caller-supplied HTTP readiness probes are normalized; TCP, empty, alternate
    endpoints, and header-bearing probes are rejected rather than inherited.
    """
    result = []
    index = 0
    seen = False
    while index < len(args):
        argument = args[index]
        if argument == '--startup-probe' or argument.startswith('--startup-probe='):
            if seen:
                fail()
            seen = True
            if argument == '--startup-probe':
                index += 1
                if index >= len(args):
                    fail()
                raw = args[index]
            else:
                raw = argument.split('=', 1)[1]
            fields = {}
            for entry in raw.split(','):
                pieces = entry.split('=', 1)
                if len(pieces) != 2 or pieces[0] in fields:
                    fail()
                fields[pieces[0]] = pieces[1]
            allowed = {'httpGet.path', 'httpGet.port', 'initialDelaySeconds', 'periodSeconds', 'timeoutSeconds', 'failureThreshold'}
            if set(fields) - allowed or fields.get('httpGet.path') != '/actuator/health/readiness' or fields.get('httpGet.port') != '8080':
                fail()
            for key in set(fields) - {'httpGet.path'}:
                if not re.fullmatch(r'[0-9]+', fields[key]):
                    fail()
            period = int(fields.get('periodSeconds', '10'))
            timeout = int(fields.get('timeoutSeconds', '1'))
            failures = int(fields.get('failureThreshold', '3'))
            if not (1 <= timeout <= period <= 240 and 1 <= failures and period * failures <= 600 and 0 <= int(fields.get('initialDelaySeconds', '0')) <= 240):
                fail()
        else:
            result.append(argument)
        index += 1
    return result + ['--startup-probe', READINESS_STARTUP_PROBE]


def deployment_args(profile, boundary, preview_id, pin, args):
    args = list(args)
    expected_runtime = RUNTIME_IDENTITIES.get(profile)
    supplied = [index for index, arg in enumerate(args) if arg == '--service-account']
    if (expected_runtime is None or len(supplied) > 1 or any(arg.startswith('--service-account=') for arg in args)
            or (supplied and (supplied[0] + 1 >= len(args) or args[supplied[0] + 1] != expected_runtime))):
        raise CIError('Bundle deployment requires the fixed runtime identity for this profile.')
    if not supplied:
        args += ['--service-account', expected_runtime]
    # These arguments originate from checked-in trusted workflows. Credentials
    # still must never be accepted on the command line, including legacy aliases.
    forbidden = {'--set-secrets', '--update-secrets', '--set-env-vars', '--env-vars-file', '--set-env-vars-file',
                 '--traffic', '--to-latest', '--no-traffic', '--revision-suffix', '--tag', '--ingress'}
    if any(arg.split('=', 1)[0] in forbidden for arg in args):
        fail()
    for index, arg in enumerate(args):
        if arg == '--update-env-vars' and index + 1 < len(args):
            if any(entry.split('=', 1)[0] in ('WARDEN_URL', 'MODTALE_SECRET_BUNDLES_ENABLED', 'MODTALE_SECRET_BUNDLE_PROFILE', 'MODTALE_SECRET_BUNDLE_PREVIEW_ID') for entry in args[index + 1].split(',')):
                fail()
    return readiness_startup_args(args) + runtime_plan('true', profile, {boundary: pin}, preview_id or '')


def _identity_matches(record, raw, *, promoted=False):
    base = record['base']
    if base is not None and (raw['uid'] != base['uid'] or raw.get('uri') != base.get('uri') or raw.get('ingress') != base.get('ingress')):
        fail()
    if not isinstance(raw.get('uri'), str) or len(raw['uri'].encode('utf-8')) > 1024:
        fail()
    expected_generation = int(base['generation']) + 1 if base is not None else 1
    if promoted:
        expected_generation += 1
    if int(raw['generation']) != expected_generation:
        fail()
    if raw['latestCreatedRevision'].rsplit('/', 1)[-1] != record['revision']:
        fail()




def remove_owned_tags(rows, record, tags):
    if rows is None:
        fail()
    result = []
    for row in rows:
        tag = row.get('tag')
        if tag not in tags:
            result.append(copy.deepcopy(row))
            continue
        if tag == record.get('temporary_tag'):
            expected = record['revision']
        else:
            if tag not in record.get('obsolete_tags', []) or record['base'] is None:
                fail()
            baseline = parse_service(raw_service(record['base']), record['profile'], record['preview_id'])
            saved = [item for item in baseline['traffic'] if item.get('tag') == tag and item['percent'] == 0]
            if len(saved) != 1:
                fail()
            expected = saved[0]['revision']
        if row['percent'] != 0 or row['revision'] != expected:
            fail()
    return result


def stage_distribution(record):
    if record['base'] is None:
        rows = [{'type': REVISION_TYPE, 'revision': record['revision'], 'percent': 100}]
    else:
        rows = parse_service(raw_service(record['base']), record['profile'], record['preview_id'])['traffic']
    rows = copy.deepcopy(rows)
    if record['schema'] in (2, 3):
        if any(row.get('tag') == record['temporary_tag'] for row in rows):
            fail()
        rows.append({'type': REVISION_TYPE, 'revision': record['revision'], 'percent': 0, 'tag': record['temporary_tag']})
    return sorted(rows, key=lambda row: (bool(row.get('tag')), row.get('tag', row['revision'])))


def warm_candidate(ci, record, current):
    if record['schema'] not in (2, 3):
        raise CIError('An untagged legacy checkpoint requires controlled fresh-head supersession before warmup.')
    full = scope(record['profile'], record['preview_id'])[3]
    route = ci.request('GET', '/v2/' + full, TAG_ROUTE_FIELDS)
    if (set(route) - {'name', 'uid', 'generation', 'trafficStatuses'} or route.get('name') != full
            or route.get('uid') != current['uid'] or route.get('generation') != current['generation']):
        fail()
    rows = [row for row in route.get('trafficStatuses', []) if row.get('tag') == record['temporary_tag']]
    if len(rows) != 1:
        return False
    row = rows[0]
    if (set(row) - {'type', 'revision', 'percent', 'tag', 'uri'} or row.get('type') != REVISION_TYPE
            or row.get('revision', '').rsplit('/', 1)[-1] != record['revision'] or row.get('percent', 0) != 0):
        fail()
    service = full.rsplit('/', 1)[-1]
    public_origin(current['uri'], service)
    expected_uri = 'https://' + record['temporary_tag'] + '---' + current['uri'].removeprefix('https://')
    public_origin(expected_uri, service, record['temporary_tag'])
    if row.get('uri') != expected_uri or not current['uri'].startswith('https://'):
        fail()
    if ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS) != current:
        return False
    response = ci.transport({'api': 'cloud-run-readiness', 'method': 'HEAD', 'origin': row['uri'],
        'path': '/actuator/health/readiness', 'service': full, 'service_uid': current['uid'],
        'revision': record['revision'], 'tag': record['temporary_tag'], 'base_uri': current['uri']})
    if set(response) != {'status', 'body'} or response['body'] != {} or response['status'] not in (0, 200, 503):
        raise CIError('Owned candidate warmup failed; normal service traffic was not changed.')
    if ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS) != current:
        return False
    return response['status'] == 200


def verify_completed_deployment(ci, record, current, *, activation=False):
    """Read-only proof of an already-landed promotion, including stale-head runs."""
    if current is None or record['candidate'] is None or record['desired'] is None:
        fail()
    profile, identifier = record['profile'], record['preview_id']
    full = scope(profile, identifier)[3]
    _identity_matches(record, current, promoted=True)
    state = parse_service(raw_service(current), profile, identifier)
    candidate = ci.request('GET', '/v2/' + full + '/revisions/' + record['revision'], REVISION_FIELDS)
    if (not ready(state, record['revision']) or state['pin'] != record['pin']
            or state['traffic'] != record['desired'] or state['actual'] != record['desired']
            or candidate != record['candidate'] or state['containers'][0]['image'] != record['expected_image']):
        fail()
    pin, volumes, containers = parse_revision(candidate, profile, identifier, record['revision'])
    if pin != state['pin'] or volumes != state['volumes'] or containers != state['containers']:
        fail()
    for container in containers:
        require_readiness_startup_probe(container)
    if record['base'] is None and current.get('ingress') != 'INGRESS_TRAFFIC_ALL':
        fail()
    if activation and verify_activation(ci.transport, profile, record['revision'], candidate['createTime'], identifier).get('verified_active') is not True:
        fail()
    if ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS) != current:
        fail()
    return current['uri']



def defer_own_pending_deployment(ci, profile, preview_id):
    """Prove an observed own candidate can reach ordinary resume/supersession.

    This performs no runtime mutation and does not claim fanout completion. The
    whole fanout is deferred until the workflow's mandatory strict final pass.
    An unobserved stage write or another preview's pending journal is not adopted.
    """
    if profile != ci.profile or preview_id != ci.preview_id or ci.boundary == 'shared':
        fail()
    journal = DeploymentJournal(ci, profile, preview_id)
    record, checksum = journal.load()
    if record is None or record['phase'] == 'complete':
        return False
    full = scope(profile, preview_id)[3]
    if ci.env.get('BACKEND_SERVICE') != full.rsplit('/', 1)[-1]:
        fail()
    current = ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS)
    record, checksum, current = reconcile_prior_promotion(ci, journal, record, checksum, current)
    if record['phase'] == 'complete':
        return False
    ci.lifecycle()
    if record['phase'] not in ('stage_intent', 'stage_wait') or record['base'] is None:
        raise CIError('Unobserved or ambiguous deployment needs explicit recovery before publication fanout.')
    _identity_matches(record, current)
    state = parse_service(raw_service(current), profile, preview_id)
    if (not staged_ready(state, record['revision']) or state['traffic'] != stage_distribution(record)
            or state['pin'] != record['pin'] or state['containers'][0]['image'] != record['expected_image']):
        fail()
    candidate = ci.request('GET', '/v2/' + full + '/revisions/' + record['revision'], REVISION_FIELDS)
    parsed = parse_revision(candidate, profile, preview_id, record['revision'])
    if (parsed != (state['pin'], state['volumes'], state['containers'])
            or (record['candidate'] is not None and candidate != record['candidate'])):
        fail()
    saved = record['serving_metadata']
    name = record['serving_revision']
    if saved is None or name is None or ci.request('GET', '/v2/' + full + '/revisions/' + name, REVISION_FIELDS) != saved:
        fail()
    parse_revision(saved, profile, preview_id, name)
    if any(not any(row['revision'] == name and row['percent'] > 0 for row in distribution)
           for distribution in (state['traffic'], state['actual'])):
        fail()
    if ci.inventory._ownership(profile, preview_id, 'backend', full.rsplit('/', 1)[-1], [0]) != current['uid']:
        fail()
    ci.lifecycle()
    if ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS) != current or journal.load()[1] != checksum:
        fail()
    return True


def healthy_baseline_fallback(ci, profile, identifier, current, revision):
    """Observe existing traffic-pool health; never claim old loader activation.

    This can start only an ordinary controlled deployment. Its new candidate
    still must produce a fresh exact activation event before any promotion.
    """
    full = scope(profile, identifier)[3]
    name = revision['name'].rsplit('/', 1)[-1]
    state = parse_service(raw_service(current), profile, identifier)
    if (current.get('ingress') != 'INGRESS_TRAFFIC_ALL'
            or any([row for row in distribution if row['percent'] > 0] != [{'type': REVISION_TYPE, 'revision': name, 'percent': 100}]
                   for distribution in (state['traffic'], state['actual']))):
        fail()
    request = {'api': 'cloud-run-baseline-readiness', 'method': 'HEAD',
               'origin': current['uri'], 'path': '/actuator/health/readiness',
               'service': full, 'service_uid': current['uid'], 'revision': name,
               'revision_uid': revision['uid'], 'base_uri': current['uri']}
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        response = ci.transport(request)
        if (not isinstance(response, dict) or set(response) != {'status', 'body'}
                or response['body'] != {} or type(response['status']) is not int):
            fail()
        if response['status'] == 200:
            ci.lifecycle()
            if (ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS) != current
                    or ci.request('GET', '/v2/' + full + '/revisions/' + name, REVISION_FIELDS) != revision):
                fail()
            return
        if response['status'] not in (0, 503):
            fail()
        ci.sleep(5)
    raise CIError('The adopted traffic pool was not ready; no candidate was staged.')


def verify_baseline_identity(ci, profile, identifier, current, revision):
    """Bind an adopted immutable baseline to its fixed runtime/preview owner."""
    import secret_bundle_preview_inventory as inventory
    full = scope(profile, identifier)[3]
    service = ci.request('GET', '/v2/' + full, inventory.SERVICE_FIELDS)
    fields = 'name,uid,service,createTime,serviceAccount,' + inventory.CONFIG_FIELDS
    name = revision['name'].rsplit('/', 1)[-1]
    immutable = ci.request('GET', '/v2/' + full + '/revisions/' + name, fields)
    if (any(service.get(key) != current.get(key) for key in ('name', 'uid', 'etag', 'generation'))
            or service.get('template', {}).get('serviceAccount') != RUNTIME_IDENTITIES[profile]
            or immutable.get('serviceAccount') != RUNTIME_IDENTITIES[profile]
            or any(immutable.get(key) != revision.get(key) for key in ('name', 'uid', 'service', 'createTime'))):
        fail()
    if profile in ('branch-preview', 'pr-preview'):
        if ci.inventory._ownership(profile, identifier, 'backend', full.rsplit('/', 1)[-1], [0]) != current['uid']:
            fail()
    if ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS) != current:
        fail()


def completed_journal_evictions(ci, profile, preview_id):
    """Freshly verified, replaceable operational cache entries in this boundary.

    Actual revisions and versions remain untouched. Incomplete transactions and
    rolled-back zero-traffic-template witnesses are never cache entries.
    """
    from secret_bundle_preview_inventory import _identity
    import secret_bundle_preview_plan as preview_plan
    target_boundary, _, target_service, _ = scope(profile, preview_id)
    container = DeploymentJournal(ci, profile, preview_id)
    annotations = container.request('GET').get('annotations', {})
    evictions = []
    for key in sorted(annotations):
        kind = next((kind for kind in ('deploy', 'rollout') if key.startswith('modtale-' + kind + '-')), None)
        if kind is None:
            continue
        service = key[len('modtale-' + kind + '-'):]
        if service == target_service:
            continue
        if target_boundary == 'shared':
            if service not in ('modtale-backend', 'modtale-backend-dev'):
                continue
            source_profile, identifier = ('prod' if service == 'modtale-backend' else 'dev'), None
        else:
            identity = _identity(target_boundary, service)
            if identity is None or identity[1] != 'backend':
                continue
            source_profile, identifier = target_boundary, identity[0]
        source = (DeploymentJournal(ci, source_profile, identifier) if kind == 'deploy'
                  else SecretManagerJournal(ci.transport, source_profile, identifier))
        decode = source.decode if kind == 'deploy' else source._decode
        record, checksum = decode(annotations[key])
        if record is None or record['phase'] != 'complete':
            continue
        full = scope(source_profile, identifier)[3]
        current = ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS, allow_404=True)
        if current is None:
            # Closed/deleted completed caches are also dispensable, but absence
            # is authoritative for both service components and fresh lifecycle.
            if target_boundary == 'shared':
                continue
            owners = preview_plan._lifecycle(source_profile, ci.inventory.github_lifecycle(source_profile))
            if identifier in owners and owners[identifier]['state'] == 'open':
                continue
            if any(ci.inventory.observe_service_absence(source_profile, identifier, component)['observation'] != 'not_found'
                   for component in ('backend', 'frontend')):
                continue
        elif kind == 'deploy':
            verify_completed_deployment(ci, record, current)
        else:
            handoff = RolloutExecutor(ci.transport, source, ci.inventory.github_lifecycle).terminal_handoff(use_persisted_activation=True, **LOCK)
            if handoff['phase'] != 'complete' or handoff['service'] != current or handoff['journal_checksum'] != checksum:
                fail()
        if source.load()[1] != checksum:
            fail()
        def validate(encoded, next_record, *, decode=decode, saved=copy.deepcopy(record), expected=checksum):
            found, actual = decode(encoded)
            if (actual != expected or found != saved or found['phase'] != 'complete'
                    or next_record['phase'] != 'stage_intent' or next_record['profile'] != profile
                    or next_record['preview_id'] != preview_id):
                fail()
            return actual
        evictions.append({'key': key, 'checksum': checksum, 'validate': validate})
    return evictions


def run_attempt(env):
    value = env.get('GITHUB_RUN_ATTEMPT', '1')
    if not isinstance(value, str) or re.fullmatch(r'[1-9][0-9]{0,8}', value) is None:
        fail()
    return value


def ordinary_terminal_transfer(ci, profile, preview_id):
    """Verify same-service terminal provenance and supply a pure atomic CAS hook."""
    journal = DeploymentJournal(ci, profile, preview_id)
    record, checksum = journal.load()
    if record is None:
        return None
    if record['phase'] != 'complete':
        raise CIError('A backend deployment is pending; finish it before a mount rollout.')
    full = scope(profile, preview_id)[3]
    current = ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS)
    verify_completed_deployment(ci, record, current)
    if journal.load()[1] != checksum:
        fail()

    def validate(encoded, next_record):
        source, actual = journal.decode(encoded)
        if (actual != checksum or source is None or source['phase'] != 'complete'
                or source != record or next_record['profile'] != profile
                or next_record['preview_id'] != preview_id or next_record['phase'] != 'stage_intent'
                or next_record['base'] != current or next_record['base_revision'] != source['candidate']):
            fail()
        return actual
    return {'key': journal.key, 'checksum': checksum, 'validate': validate}


def reconcile_prior_promotion(ci, journal, record, checksum, current):
    """Settle an accepted old-head result using reads and journal metadata only."""
    if record is None or record['phase'] not in ('promote_intent', 'promote_wait'):
        return record, checksum, current
    full = scope(record['profile'], record['preview_id'])[3]
    deadline = time.monotonic() + 900
    while time.monotonic() < deadline:
        if current == record['staged']:
            if record['phase'] == 'promote_intent' or record['operation'] is None:
                # Unknown acceptance cannot authorize a stale-head runtime retry.
                return record, checksum, current
            outcome = ci.request('GET', '/v2/' + record['operation'], 'name,done,error(code)')
            if outcome.get('name') != record['operation'] or type(outcome.get('done', False)) is not bool:
                fail()
            fresh = ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS)
            if outcome.get('done') is True and outcome.get('error', {}).get('code') and fresh == current:
                record.update(phase='stage_wait', operation=None)
                return record, journal.save(record, checksum), current
            current = fresh
        else:
            state = parse_service(raw_service(current), record['profile'], record['preview_id'])
            if ready(state, record['revision']):
                verify_completed_deployment(ci, record, current, activation=True)
                record['phase'] = 'complete'
                return record, journal.save(record, checksum), current
            if state['condition'] == 'CONDITION_FAILED':
                fail()
        ci.sleep(5)
        current = ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS)
    raise CIError('An accepted promotion is still reconciling; no stale-head runtime write was issued.')


def deploy(ci, args):
    profile, boundary, identifier = ci.profile, ci.boundary, ci.preview_id
    _, _, service, full = scope(profile, identifier)
    if ci.env.get('BACKEND_SERVICE') != service:
        fail()
    pin = numeric_version(ci.env.get('MODTALE_SECRET_BUNDLE_VERSION', ''))
    args = list(args)
    intent_kind = 'fanout_refresh' if ci.env.get('MODTALE_SECRET_BUNDLE_FANOUT_REFRESH') == 'true' else 'workflow'
    if intent_kind == 'fanout_refresh' and (len(args) != 2 or args[0] != '--image' or '@sha256:' not in args[1]):
        fail()
    if '--image' in args:
        index = args.index('--image') + 1
        image = args[index]
        prefix = 'gcr.io/' + TARGETS[boundary][0] + '/modtale-backend'
        if not re.fullmatch(re.escape(prefix) + r'(?::[A-Za-z0-9_.-]+|@sha256:[a-f0-9]{64})', image):
            fail()
        if '@sha256:' not in image:
            digest_value = ci.runner(['gcloud', 'container', 'images', 'describe', image,
                '--project', TARGETS[boundary][0], '--format=value(image_summary.digest)']).decode().strip()
            if not re.fullmatch(r'sha256:[a-f0-9]{64}', digest_value):
                fail()
            args[index] = prefix + '@' + digest_value
    args = deployment_args(profile, boundary, identifier, pin, args)
    head = ci.env['PR_HEAD_SHA'] if profile == 'pr-preview' else ci.env['GITHUB_SHA']
    digest = hashlib.sha256(json.dumps([ci.env['GITHUB_RUN_ID'], run_attempt(ci.env), intent_kind, head, pin, args], separators=(',', ':')).encode()).hexdigest()
    suffix = 'bd-' + digest[:12]
    revision = service + '-' + suffix
    if len(revision) > 63:
        fail()
    journal = DeploymentJournal(ci)
    record, checksum = journal.load()
    mount_journal = SecretManagerJournal(ci.transport, profile, identifier)
    rollout, _ = mount_journal.load()
    if rollout is not None and rollout['phase'] not in ('complete', 'rolled_back'):
        raise CIError('A mount rollout is pending; resume it before deploying another backend revision.')
    current = ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS, allow_404=True)
    # An old run may have crashed after a successful promotion. Observe and
    # checkpoint that exact accepted result before applying the new-head gate.
    # This path never sends a runtime PATCH or promotes a stale-head candidate.
    record, checksum, current = reconcile_prior_promotion(ci, journal, record, checksum, current)
    ci.lifecycle()
    handoff = None
    if rollout is not None:
        if record is not None and record['phase'] != 'complete':
            raise CIError('Overlapping deployment and mount journals require explicit recovery.')
        if record is None or record['digest'] != digest:
            handoff = RolloutExecutor(ci.transport, mount_journal, ci.inventory.github_lifecycle).terminal_handoff(use_persisted_activation=True, **LOCK)
            if handoff['service'] != current:
                fail()
    superseded = None
    if record is not None and record['phase'] != 'complete' and record['digest'] != digest:
        # A fresh push may replace an observed, settled no-traffic candidate.
        # Never replace an unobserved request or an ambiguous promotion.
        if record['phase'] not in ('stage_intent', 'stage_wait') or record['base'] is None or current is None or '--image' not in args:
            raise CIError('An unresolved deployment write requires explicit recovery before supersession.')
        _identity_matches(record, current)
        previous = parse_service(raw_service(record['base']), profile, identifier)
        staged = parse_service(raw_service(current), profile, identifier)
        if (staged['reconciling'] or staged['observed'] != staged['generation']
                or staged['traffic'] != stage_distribution(record)
                or remove_owned_tags(staged['actual'], record, [record['temporary_tag']] if record['schema'] in (2, 3) else []) != previous['actual']):
            fail()
        abandoned = ci.request('GET', '/v2/' + full + '/revisions/' + record['revision'], REVISION_FIELDS)
        old_pin, old_volumes, old_containers = parse_revision(abandoned, profile, identifier, record['revision'])
        if old_pin != record['pin'] or old_volumes != staged['volumes'] or old_containers != staged['containers'] or (record['candidate'] is not None and abandoned != record['candidate']):
            fail()
        if ci.request('GET', '/v2/' + full + '/revisions/' + record['serving_revision'], REVISION_FIELDS) != record['serving_metadata']:
            fail()
        if ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS) != current:
            fail()
        superseded = {**record, 'candidate': abandoned}
    if record is None or record['digest'] != digest:
        if ci.request('GET', '/v2/' + full + '/revisions/' + revision, REVISION_FIELDS, allow_404=True) is not None:
            raise CIError('This attempt already used the candidate revision; rerun with a fresh workflow attempt.')
        serving_revision = None
        serving_metadata = None
        baseline_provenance = 'no_service'
        # Keep immediate rollback evidence in serving_metadata. Only the most
        # recent superseded, unresolved candidate needs an additional reference;
        # older Cloud Run revisions and Secret Manager versions remain untouched.
        retained = []
        obsolete_tags = []
        if superseded is not None:
            serving_revision, serving_metadata = record['serving_revision'], record['serving_metadata']
            baseline_provenance = record.get('baseline_provenance', 'activation_event')
            retained = [superseded['candidate']]
            if record['schema'] in (2, 3):
                obsolete_tags = record['obsolete_tags'] + [record['temporary_tag']]
                if len(obsolete_tags) > MAX_OBSOLETE_TAGS:
                    raise CIError('Too many interrupted tagged candidates; explicit recovery is required.')
        elif handoff is not None:
            serving_revision, serving_metadata = handoff['serving_revision'], handoff['serving_metadata']
            baseline_provenance = 'mount_handoff'
        elif current is not None:
            state = parse_service(raw_service(current), profile, identifier)
            if not ready(state, state['created']) or any(not any(target['revision'] == state['created'] and target.get('percent', 0) > 0
                    for target in distribution) for distribution in (state['traffic'], state['actual'])):
                raise CIError('Current latest revision must be verified serving before ordinary deployment adoption.')
            base_revision = ci.request('GET', '/v2/' + full + '/revisions/' + state['created'], REVISION_FIELDS)
            if parse_revision(base_revision, profile, identifier, state['created']) != (state['pin'], state['volumes'], state['containers']):
                fail()
            verify_baseline_identity(ci, profile, identifier, current, base_revision)
            if record is not None and record['phase'] == 'complete':
                verify_completed_deployment(ci, record, current)
                baseline_provenance = 'completed_checkpoint'
            else:
                evidence = verify_activation(ci.transport, profile, state['created'], base_revision['createTime'], identifier)
                if evidence.get('verified_active') is True:
                    baseline_provenance = 'activation_event'
                elif evidence.get('reason') == 'activation_not_observed':
                    healthy_baseline_fallback(ci, profile, identifier, current, base_revision)
                    baseline_provenance = 'healthy_fallback_unverified'
                else:
                    fail()
            serving_revision, serving_metadata = state['created'], base_revision
        elif ci.env.get('MODTALE_SECRET_BUNDLE_NEW_SERVICE_APPROVED') != service:
            raise CIError('New bundle service requires explicit approval for its controlled ingress activation.')
        expected_image = args[args.index('--image') + 1] if '--image' in args else current['template']['containers'][0]['image'] if current is not None else None
        if not isinstance(expected_image, str) or re.fullmatch(r'[A-Za-z0-9._:/-]+@sha256:[a-f0-9]{64}', expected_image) is None:
            fail()
        if intent_kind == 'fanout_refresh' and (current is None or expected_image != current['template']['containers'][0]['image']):
            fail()
        temporary_tag = 'b' + digest[:9]
        if current is not None:
            public_origin(current['uri'], service)
            public_origin('https://' + temporary_tag + '---' + current['uri'].removeprefix('https://'), service, temporary_tag)
        if current is not None and any(row.get('tag') == temporary_tag for row in parse_service(raw_service(current), profile, identifier)['traffic']):
            raise CIError('Candidate tag already exists; it was not adopted or overwritten.')
        record = {'schema': 3, 'profile': profile, 'preview_id': identifier, 'digest': digest,
                  'revision': revision, 'pin': pin, 'head': head, 'base': current, 'phase': 'stage_intent',
                  'staged': None, 'candidate': None, 'desired': None, 'serving_revision': serving_revision,
                  'serving_metadata': serving_metadata, 'retained_revisions': retained,
                  'expected_image': expected_image, 'reservation_bytes': 0, 'operation': None,
                  'temporary_tag': temporary_tag, 'obsolete_tags': obsolete_tags, 'baseline_provenance': baseline_provenance, 'intent_kind': intent_kind}
        record['reservation_bytes'] = deployment_reservation_bytes(record)
        if handoff is not None:
            if ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS) != current:
                fail()
            journal.consume_terminal = handoff
        journal.evict_completed = completed_journal_evictions(ci, profile, identifier)
        checksum = journal.save(record, checksum)
    if record['phase'] == 'complete':
        return verify_completed_deployment(ci, record, current)
    if record['phase'] == 'stage_intent':
        if current == record['base']:
            ci.lifecycle()
            command = ['gcloud', 'run', 'deploy', service, *args, '--project', TARGETS[boundary][0],
                       '--region', 'us-central1', '--revision-suffix', suffix, '--quiet', '--format=none']
            # The official CLI preserves resourceVersion in its v1 ReplaceService.
            # Existing traffic stays pinned while the candidate boots.
            command += ['--no-traffic'] if current is not None else ['--ingress', 'internal']
            if record['schema'] in (2, 3):
                command += ['--tag', record['temporary_tag']]
            ci.runner(command)
        current = ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS)
        _identity_matches(record, current)
        if record['base'] is None and current.get('ingress') != 'INGRESS_TRAFFIC_INTERNAL_ONLY':
            fail()
        record['phase'] = 'stage_wait'
        checksum = journal.save(record, checksum)
    deadline = time.monotonic() + 900
    while time.monotonic() < deadline:
        ci.lifecycle()
        current = ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS)
        promoting = record['phase'] in ('promote_intent', 'promote_wait')
        state = parse_service(raw_service(current), profile, identifier)
        if record['phase'] == 'promote_wait' and current == record['staged']:
            if record['operation'] is None:
                raise CIError('Promotion outcome is uncertain; explicit reconciliation is required.')
            outcome = ci.request('GET', '/v2/' + record['operation'], 'name,done,error(code)')
            if outcome.get('name') != record['operation'] or type(outcome.get('done', False)) is not bool:
                fail()
            if outcome.get('done') is True and outcome.get('error', {}).get('code'):
                record.update(phase='stage_wait', operation=None)
                journal.save(record, checksum)
                raise CIError('Promotion operation failed without changing traffic; rerun to retry the guarded promotion.')
            ci.sleep(5)
            continue
        if promoting and record['staged'] is not None and current != record['staged']:
            _identity_matches(record, current, promoted=True)
            if state['traffic'] != record['desired']:
                fail()
        else:
            _identity_matches(record, current)
        if state['pin'] != pin or state['containers'][0]['image'] != record['expected_image']:
            fail()
        if record['base'] is not None and not promoting:
            old = parse_service(raw_service(record['base']), profile, identifier)
            if state['traffic'] != stage_distribution(record):
                fail()
            if state['actual'] is not None and remove_owned_tags(state['actual'], record, [record['temporary_tag']] if record['schema'] in (2, 3) else []) != old['actual']:
                fail()
        if state['condition'] == 'CONDITION_FAILED':
            raise CIError('Candidate failed readiness; previous traffic is retained and deployment remains recoverable.')
        awaiting_serving = promoting and current != record['staged']
        if not (ready(state, revision) if awaiting_serving else staged_ready(state, revision)):
            ci.sleep(5)
            continue
        candidate = ci.request('GET', '/v2/' + full + '/revisions/' + revision, REVISION_FIELDS)
        candidate_pin, volumes, containers = parse_revision(candidate, profile, identifier, revision)
        if candidate_pin != pin or volumes != state['volumes'] or containers != state['containers']:
            fail()
        for container in containers:
            require_readiness_startup_probe(container)
        if record['candidate'] is not None and record['candidate'] != candidate:
            fail()
        if not awaiting_serving:
            if not warm_candidate(ci, record, current):
                ci.sleep(5)
                continue
            readiness = ci.request('GET', '/v2/' + full + '/revisions/' + revision, REVISION_READINESS_FIELDS)
            if not revision_ready(readiness, profile, identifier, revision, candidate['uid']):
                ci.sleep(5)
                continue
        if verify_activation(ci.transport, profile, revision, candidate['createTime'], identifier).get('verified_active') is not True:
            ci.sleep(5)
            continue
        if ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS) != current:
            ci.sleep(5)
            continue
        if record['phase'] == 'stage_wait':
            if record['base'] is None:
                desired = [{'type': REVISION_TYPE, 'revision': revision, 'percent': 100}]
            else:
                old = parse_service(raw_service(record['base']), profile, identifier)
                desired = remove_owned_tags(old['traffic'], record, record['obsolete_tags'] if record['schema'] in (2, 3) else [])
                moved = 0
                for target in desired:
                    if target['revision'] == record['serving_revision'] and target.get('percent', 0) > 0:
                        moved += target['percent']
                        target['revision'] = revision
                if not moved:
                    fail()
                desired.sort(key=lambda target: (bool(target.get('tag')), target.get('tag', target['revision'])))
            record.update(staged=current, candidate=candidate, desired=desired, phase='promote_intent')
            checksum = journal.save(record, checksum)
        if record['phase'] == 'promote_intent':
            if current == record['staged']:
                ci.lifecycle()
                body = {'name': full, 'etag': current['etag'], 'traffic': record['desired']}
                mask = 'traffic'
                if record['base'] is None:
                    if ci.env.get('MODTALE_SECRET_BUNDLE_NEW_SERVICE_APPROVED') != service:
                        fail()
                    body['ingress'] = 'INGRESS_TRAFFIC_ALL'
                    mask = 'traffic,ingress'
                operation = ci.request('PATCH', '/v2/' + full, 'name,done', body=body, params={'updateMask': mask})
                record['operation'] = operation_name(operation.get('name'), full)
            record['phase'] = 'promote_wait'
            checksum = journal.save(record, checksum)
            ci.sleep(5)
            continue
        if record['phase'] == 'promote_wait':
            _identity_matches(record, current, promoted=True)
            if state['traffic'] != record['desired'] or state['actual'] != record['desired']:
                ci.sleep(5)
                continue
            if record['base'] is None and current.get('ingress') != 'INGRESS_TRAFFIC_ALL':
                fail()
            record['phase'] = 'complete'
            journal.save(record, checksum)
            return current['uri']
    raise CIError('Candidate activation is pending; rerun the same workflow to resume its credential-free checkpoint.')
