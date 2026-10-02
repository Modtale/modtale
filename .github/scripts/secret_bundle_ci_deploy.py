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
from secret_bundle_activation_evidence import verify_activation
from secret_bundle_ci import CIError, LOCK, fail
from secret_bundle_rollout import (SecretManagerJournal, SERVICE_FIELDS, REVISION_FIELDS, parse_service,
                                   parse_revision, ready, scope, REVISION_TYPE, require_readiness_startup_probe, operation_name, staged_ready, revision_ready, REVISION_READINESS_FIELDS)

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
    if record['schema'] == 2:
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
        record = _decode_etags(json.loads(raw))
        allowed = {'schema', 'profile', 'preview_id', 'digest', 'revision', 'pin', 'head', 'base', 'phase', 'staged', 'candidate', 'desired', 'serving_revision', 'serving_metadata', 'retained_revisions', 'expected_image', 'reservation_bytes', 'operation'}
        if record.get('schema') == 2:
            allowed |= {'temporary_tag', 'obsolete_tags'}
        if set(record) != allowed or record['schema'] not in (1, 2) or record['profile'] != self.profile or record['preview_id'] != self.preview_id or record['phase'] not in PHASES:
            fail()
        if not re.fullmatch(r'[a-f0-9]{64}', record['digest']) or not re.fullmatch(r'[a-f0-9]{40}', record['head']):
            fail()
        numeric_version(record['pin'])
        service = scope(self.profile, self.preview_id)[2]
        if record['revision'] != service + '-bd-' + record['digest'][:12]:
            fail()
        if record['schema'] == 2:
            if record['temporary_tag'] != 'sb-' + record['digest'][:12]:
                fail()
            tags = record['obsolete_tags']
            if (not isinstance(tags, list) or len(tags) > MAX_OBSOLETE_TAGS or len(set(tags)) != len(tags)
                    or any(not isinstance(tag, str) or re.fullmatch(r'sb-[a-f0-9]{12}', tag) is None or tag == record['temporary_tag'] for tag in tags)):
                fail()
        if record['operation'] is not None:
            operation_name(record['operation'], scope(self.profile, self.preview_id)[3])
        if not isinstance(record['expected_image'], str) or re.fullmatch(r'[A-Za-z0-9._:/-]+@sha256:[a-f0-9]{64}', record['expected_image']) is None:
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
        annotations[self.key] = value
        if len(annotations) > 100 or sum(len(k.encode()) + len(v.encode()) for k, v in annotations.items()) >= 16384:
            fail()
        self.request('PATCH', body={'name': self.name, 'etag': metadata['etag'], 'annotations': annotations})
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
    service = scope(record['profile'], record['preview_id'])[2]
    result = []
    for row in rows:
        if row.get('tag') in tags:
            if row.get('percent', 0) != 0 or row.get('revision') != service + '-bd-' + row['tag'][3:]:
                fail()
        else:
            result.append(copy.deepcopy(row))
    return result


def stage_distribution(record):
    if record['base'] is None:
        rows = [{'type': REVISION_TYPE, 'revision': record['revision'], 'percent': 100}]
    else:
        rows = parse_service(raw_service(record['base']), record['profile'], record['preview_id'])['traffic']
    rows = copy.deepcopy(rows)
    if record['schema'] == 2:
        if any(row.get('tag') == record['temporary_tag'] for row in rows):
            fail()
        rows.append({'type': REVISION_TYPE, 'revision': record['revision'], 'percent': 0, 'tag': record['temporary_tag']})
    return sorted(rows, key=lambda row: (bool(row.get('tag')), row.get('tag', row['revision'])))


def warm_candidate(ci, record, current):
    if record['schema'] != 2:
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
    expected_uri = 'https://' + record['temporary_tag'] + '---' + current['uri'].removeprefix('https://')
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
    digest = hashlib.sha256(json.dumps([ci.env['GITHUB_RUN_ID'], head, pin, args], separators=(',', ':')).encode()).hexdigest()
    suffix = 'bd-' + digest[:12]
    revision = service + '-' + suffix
    if len(revision) > 63:
        fail()
    journal = DeploymentJournal(ci)
    record, checksum = journal.load()
    rollout, _ = SecretManagerJournal(ci.transport, profile, identifier).load()
    if rollout is not None and rollout['phase'] not in ('complete', 'rolled_back'):
        raise CIError('A mount rollout is pending; resume it before deploying another backend revision.')
    current = ci.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS, allow_404=True)
    # An old run may have crashed after a successful promotion. Observe and
    # checkpoint that exact accepted result before applying the new-head gate.
    # This path never sends a runtime PATCH or promotes a stale-head candidate.
    record, checksum, current = reconcile_prior_promotion(ci, journal, record, checksum, current)
    ci.lifecycle()
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
                or remove_owned_tags(staged['actual'], record, [record['temporary_tag']] if record['schema'] == 2 else []) != previous['actual']):
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
        serving_revision = None
        serving_metadata = None
        # Keep immediate rollback evidence in serving_metadata. Only the most
        # recent superseded, unresolved candidate needs an additional reference;
        # older Cloud Run revisions and Secret Manager versions remain untouched.
        retained = []
        obsolete_tags = []
        if superseded is not None:
            serving_revision, serving_metadata = record['serving_revision'], record['serving_metadata']
            retained = [superseded['candidate']]
            if record['schema'] == 2:
                obsolete_tags = record['obsolete_tags'] + [record['temporary_tag']]
                if len(obsolete_tags) > MAX_OBSOLETE_TAGS:
                    raise CIError('Too many interrupted tagged candidates; explicit recovery is required.')
        elif current is not None:
            state = parse_service(raw_service(current), profile, identifier)
            if not ready(state, state['created']) or any(not any(target['revision'] == state['created'] and target.get('percent', 0) > 0
                    for target in distribution) for distribution in (state['traffic'], state['actual'])):
                raise CIError('Current latest revision must be verified serving before ordinary deployment adoption.')
            base_revision = ci.request('GET', '/v2/' + full + '/revisions/' + state['created'], REVISION_FIELDS)
            if verify_activation(ci.transport, profile, state['created'], base_revision['createTime'], identifier).get('verified_active') is not True:
                fail()
            serving_revision, serving_metadata = state['created'], base_revision
        elif ci.env.get('MODTALE_SECRET_BUNDLE_NEW_SERVICE_APPROVED') != service:
            raise CIError('New bundle service requires explicit approval for its controlled ingress activation.')
        expected_image = args[args.index('--image') + 1] if '--image' in args else current['template']['containers'][0]['image'] if current is not None else None
        if not isinstance(expected_image, str) or re.fullmatch(r'[A-Za-z0-9._:/-]+@sha256:[a-f0-9]{64}', expected_image) is None:
            fail()
        temporary_tag = 'sb-' + digest[:12]
        if current is not None and any(row.get('tag') == temporary_tag for row in parse_service(raw_service(current), profile, identifier)['traffic']):
            raise CIError('Candidate tag already exists; it was not adopted or overwritten.')
        record = {'schema': 2, 'profile': profile, 'preview_id': identifier, 'digest': digest,
                  'revision': revision, 'pin': pin, 'head': head, 'base': current, 'phase': 'stage_intent',
                  'staged': None, 'candidate': None, 'desired': None, 'serving_revision': serving_revision,
                  'serving_metadata': serving_metadata, 'retained_revisions': retained,
                  'expected_image': expected_image, 'reservation_bytes': 0, 'operation': None,
                  'temporary_tag': temporary_tag, 'obsolete_tags': obsolete_tags}
        record['reservation_bytes'] = deployment_reservation_bytes(record)
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
            if record['schema'] == 2:
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
            if state['actual'] is not None and remove_owned_tags(state['actual'], record, [record['temporary_tag']] if record['schema'] == 2 else []) != old['actual']:
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
                desired = remove_owned_tags(old['traffic'], record, record['obsolete_tags'] if record['schema'] == 2 else [])
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
