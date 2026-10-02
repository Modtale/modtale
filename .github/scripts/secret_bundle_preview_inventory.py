"""Read-only metadata normalization with a mandatory injected transport.

No HTTP/auth implementation, subprocess, credential files, or CLI is provided.
The transport receives a request dictionary and returns {status, body}; it MUST
send each Cloud Run `fields` parameter to the server unchanged. It must not log
response bodies. On non-200 responses it must not read diagnostic response bodies;
return the status with an empty body instead. Errors never retain transport causes.

Supported: fixed Modtale/modtale GraphQL connections and Cloud Run REST v2,
single-container preview services in the fixed projects/us-central1. V1 config
normalization, cross-project numeric aliases, sidecars and unspecified bundle
versions fail closed. Ownership uses a separate regional v1 labelSelector query
projected to names/UIDs, matched to v2 UID and runtime identity. This narrow proof
was independently checked against a current branch preview; the complete adapter
and masks remain SYNTHETIC-TESTED ONLY. V2 label-map masks are unsupported. There
is no full-label, unmasked, or gcloud output-format fallback. Matching service
names alone never prove ownership. Existing PR workflows lack ownership labels.
API discovery/schema sources: Cloud Run REST v2 Service, Revision, Container and
Volume references; GitHub GraphQL Repository.refs and Repository.pullRequests.

Pagination and before/after service checks detect observed drift, not a global
snapshot. The future executor still needs the real global lock, fresh lifecycle
checks and an explicit complete caller-owned rollback manifest. Missing rollback
knowledge never means that old revisions are safe to retire.
Active bundle-mode provenance is externally verified deployment metadata, not an
env-value read. It binds each retained backend revision's immutable UID to the
profile, preview ID and numeric pin. The caller must actually establish it;
this library cannot authenticate a supplied assertion or verify its source.
"""
import re

from secret_bundle import TARGETS
import secret_bundle_preview_plan as plan

MAX_ITEMS = 10000
MAX_PAGES = 10000
BRANCH_QUERY = '''query PreviewBranches($cursor: String) {
  repository(owner: "Modtale", name: "modtale") {
    nameWithOwner
    refs(refPrefix: "refs/heads/", first: 100, after: $cursor) {
      nodes { name target { oid } }
      pageInfo { hasNextPage endCursor }
    }
  }
}'''
PR_QUERY = '''query PreviewPullRequests($cursor: String) {
  repository(owner: "Modtale", name: "modtale") {
    nameWithOwner
    pullRequests(first: 100, after: $cursor) {
      nodes { number state headRefOid headRepository { nameWithOwner } }
      pageInfo { hasNextPage endCursor }
    }
  }
}'''
CONTAINER_FIELDS = 'containers(name,env(name,valueSource(secretKeyRef(secret,version))),volumeMounts(name,mountPath,subPath))'
VOLUME_FIELDS = 'volumes(name,secret(secret,items(path,version)))'
CONFIG_FIELDS = CONTAINER_FIELDS + ',' + VOLUME_FIELDS
TRAFFIC_FIELDS = '(type,revision,percent,tag)'
OWNERSHIP_FIELDS = 'items(metadata(name,uid)),metadata(continue),unreachable'
OWNERSHIP_ORIGIN = 'https://us-central1-run.googleapis.com'
CLOUD_RUN_ORIGIN = 'https://run.googleapis.com'
RUNTIME_ACCOUNTS = {
    'branch-preview': 'modtale-branch-preview-runtime@gen-lang-client-0244308719.iam.gserviceaccount.com',
    'pr-preview': 'modtale-pr-preview-runtime@modtale-pr-preview.iam.gserviceaccount.com',
}
SERVICE_FIELDS = ('name,uid,etag,generation,observedGeneration,reconciling,terminalCondition(state),'
                  'latestReadyRevision,latestCreatedRevision,traffic' + TRAFFIC_FIELDS + ',trafficStatuses' + TRAFFIC_FIELDS
                  + ',template(revision,serviceAccount,' + CONFIG_FIELDS + ')')
SERVICE_LIST_FIELDS = 'services(name),nextPageToken,unreachable'
REVISION_FIELDS = 'name,uid,service,serviceAccount,' + CONFIG_FIELDS
REVISION_LIST_FIELDS = 'revisions(' + REVISION_FIELDS + '),nextPageToken'


class InventoryError(ValueError):
    pass


def _fail(message='Preview metadata is incomplete, unsupported, or inconsistent.'):
    raise InventoryError(message)


def _object(value, allowed, required=()):
    if not isinstance(value, dict) or not set(value).issubset(set(allowed)) or not set(required).issubset(value):
        _fail()
    return value


def _array(value):
    if not isinstance(value, list) or len(value) > MAX_ITEMS:
        _fail()
    return value


def _text(value, maximum=1024):
    try:
        return plan._text(value, maximum)
    except Exception:
        raise InventoryError('Invalid metadata text.') from None


def _version(value):
    try:
        return plan._version(value)
    except Exception:
        raise InventoryError('Bundle references must use explicit numeric versions.') from None


def _boundary(boundary):
    try:
        return plan._boundary(boundary)
    except Exception:
        raise InventoryError('Unsupported preview boundary.') from None


def _identity(boundary, service):
    _text(service, 63)
    if boundary == 'branch-preview':
        match = re.fullmatch(r'modtale-(backend|frontend)-(.+)', service)
        if not match:
            return None
        component, preview_id = match.groups()
        if preview_id in plan.RESERVED:
            return None
    else:
        match = re.fullmatch(r'modtale-pr-(.+)-(backend|frontend)', service)
        if not match:
            return None
        preview_id, component = match.groups()
    try:
        if plan.service_name(boundary, preview_id, component) != service:
            _fail()
    except Exception:
        raise InventoryError('Unrecognized preview service identity.') from None
    return preview_id, component


def _revision(name, service_full):
    _text(name)
    prefix = service_full + '/revisions/'
    short = name[len(prefix):] if name.startswith(prefix) else name
    service = service_full.rsplit('/', 1)[-1]
    if len(short) > 63 or not short.startswith(service + '-') or re.fullmatch(r'[a-z0-9][a-z0-9-]*[a-z0-9]', short) is None:
        _fail()
    return short


def _secret_name(value, project):
    value = _text(value)
    prefix = 'projects/' + project + '/secrets/'
    short = value[len(prefix):] if value.startswith(prefix) else value
    if re.fullmatch(r'[A-Za-z0-9_-]{1,255}', short) is None:
        _fail('Cross-project or unsupported secret reference.')
    return short


def _config(config, boundary, component):
    """Return a numeric mounted bundle version and whether legacy refs coexist."""
    _object(config, ('revision', 'serviceAccount', 'containers', 'volumes'), ('containers',))
    containers = _array(config['containers'])
    if len(containers) != 1:
        _fail('Only single-container preview services are supported.')
    container = _object(containers[0], ('name', 'env', 'volumeMounts'))
    if 'name' in container:
        _text(container['name'], 63)
    expected_secret = TARGETS[boundary][1]
    project = TARGETS[boundary][0]
    legacy = False
    for env in _array(container.get('env', [])):
        _object(env, ('name', 'valueSource'), ('name',))
        _text(env['name'], 255)
        if 'valueSource' not in env:
            continue
        source = _object(env['valueSource'], ('secretKeyRef',), ('secretKeyRef',))
        ref = _object(source['secretKeyRef'], ('secret', 'version'), ('secret', 'version'))
        secret = _secret_name(ref['secret'], project)
        _text(ref['version'], 255)
        if secret in {entry[1] for entry in TARGETS.values()}:
            _fail('Bundle environment-variable delivery is unsupported.')
        legacy = True
    mounts = {}
    for mount in _array(container.get('volumeMounts', [])):
        _object(mount, ('name', 'mountPath', 'subPath'), ('name', 'mountPath'))
        name = _text(mount['name'], 255)
        if name in mounts or mount.get('subPath', '') != '':
            _fail()
        path = _text(mount['mountPath'])
        if not path.startswith('/') or ':' in path or any(part in ('', '.', '..') for part in path.split('/')[1:]):
            _fail('Noncanonical mount path.')
        if any(path == other or path.startswith(other + '/') or other.startswith(path + '/') for other in mounts.values()):
            _fail('Overlapping mount paths require explicit review.')
        mounts[name] = path
    versions = []
    seen_volumes = set()
    for volume in _array(config.get('volumes', [])):
        _object(volume, ('name', 'secret'), ('name',))
        name = _text(volume['name'], 255)
        if name in seen_volumes:
            _fail()
        seen_volumes.add(name)
        if 'secret' not in volume:
            continue
        secret = _object(volume['secret'], ('secret', 'items'), ('secret',))
        source = _secret_name(secret['secret'], project)
        items = _array(secret.get('items', []))
        for item in items:
            _object(item, ('path', 'version'), ('path', 'version'))
            _text(item['path'])
            _text(item['version'], 255)
        if source != expected_secret:
            if source in {entry[1] for entry in TARGETS.values()}:
                _fail('Unexpected bundle boundary.')
            legacy = True
            continue
        if component != 'backend' or len(items) != 1 or mounts.get(name) != '/app/secrets/bundles':
            _fail('Unexpected bundle mount shape.')
        item = _object(items[0], ('path', 'version'), ('path', 'version'))
        if item['path'] != boundary + '.json':
            _fail('Unexpected bundle mount path.')
        versions.append(_version(item['version']))
    if len(versions) > 1:
        _fail('Multiple bundle references require explicit review.')
    if not set(mounts).issubset(seen_volumes):
        _fail('A mount has no corresponding volume.')
    return (versions[0] if versions else None), legacy


class PreviewInventory:
    """Injected transport contract: request -> {'status': int, 'body': dict}."""

    def __init__(self, transport):
        if not callable(transport):
            _fail()
        self._transport = transport

    def _request(self, request, *, allow_not_found=False):
        try:
            response = self._transport(request)
        except Exception:
            raise InventoryError('Metadata request failed; details suppressed.') from None
        _object(response, ('status', 'body'), ('status', 'body'))
        status = response['status']
        if type(status) is not int:
            _fail()
        if status == 404 and allow_not_found:
            return None
        if status != 200:
            _fail('Metadata request did not succeed; absence was not inferred.')
        if not isinstance(response['body'], dict):
            _fail()
        return response['body']

    def github_lifecycle(self, boundary):
        _boundary(boundary)
        branch = boundary == 'branch-preview'
        connection = 'refs' if branch else 'pullRequests'
        cursor, seen, items = None, set(), []
        for _ in range(MAX_PAGES):
            body = self._request({'api': 'github', 'origin': 'https://api.github.com', 'method': 'POST', 'path': '/graphql',
                                  'query': BRANCH_QUERY if branch else PR_QUERY, 'variables': {'cursor': cursor}})
            _object(body, ('data', 'errors'), ('data',))
            if 'errors' in body and _array(body['errors']):
                _fail('GraphQL returned errors; partial lifecycle data was rejected.')
            data = _object(body['data'], ('repository',), ('repository',))
            repository = _object(data['repository'], ('nameWithOwner', connection), ('nameWithOwner', connection))
            if repository['nameWithOwner'] != plan.REPOSITORY:
                _fail('Repository scope mismatch.')
            result = _object(repository[connection], ('nodes', 'pageInfo'), ('nodes', 'pageInfo'))
            nodes = _array(result['nodes'])
            if len(items) + len(nodes) > MAX_ITEMS:
                _fail('Lifecycle inventory exceeded its hard limit.')
            for node in nodes:
                if branch:
                    _object(node, ('name', 'target'), ('name', 'target'))
                    target = _object(node['target'], ('oid',), ('oid',))
                    items.append({'name': node['name'], 'head_sha': target['oid']})
                else:
                    _object(node, ('number', 'state', 'headRefOid', 'headRepository'), ('number', 'state', 'headRefOid', 'headRepository'))
                    if type(node['number']) is not int or node['number'] <= 0 or node['state'] not in ('OPEN', 'CLOSED', 'MERGED'):
                        _fail()
                    head_repo = _object(node['headRepository'], ('nameWithOwner',), ('nameWithOwner',))
                    items.append({'number': str(node['number']), 'state': 'open' if node['state'] == 'OPEN' else 'closed',
                                  'head_sha': node['headRefOid'], 'head_repository': head_repo['nameWithOwner']})
            page = _object(result['pageInfo'], ('hasNextPage', 'endCursor'), ('hasNextPage', 'endCursor'))
            if type(page['hasNextPage']) is not bool:
                _fail()
            if not page['hasNextPage']:
                snapshot = {'repository': plan.REPOSITORY, 'complete': True, 'items': items}
                try:
                    plan._lifecycle(boundary, snapshot)
                except Exception:
                    raise InventoryError('Lifecycle identities are invalid or ambiguous.') from None
                return snapshot
            cursor = _text(page['endCursor'], 4096)
            if cursor in seen:
                _fail('Pagination cursor repeated.')
            seen.add(cursor)
        _fail('Lifecycle pagination exceeded its hard limit.')

    def _cloud_get(self, path, fields, *, token=None, list_request=False, allow_not_found=False):
        # No caller-controlled endpoint or field selector is exposed publicly.
        params = {'fields': fields}
        if list_request:
            params.update({'pageSize': 100, 'showDeleted': False})
            if token is not None:
                params['pageToken'] = token
        return self._request({'api': 'cloud-run-v2', 'origin': CLOUD_RUN_ORIGIN,
                              'method': 'GET', 'path': path, 'params': params}, allow_not_found=allow_not_found)

    def _pages(self, path, fields, collection, budget):
        token, seen, output = None, set(), []
        for _ in range(MAX_PAGES):
            body = self._cloud_get(path, fields, token=token, list_request=True)
            _object(body, (collection, 'nextPageToken', 'unreachable') if collection == 'services' else (collection, 'nextPageToken'))
            if collection == 'services' and _array(body.get('unreachable', [])):
                _fail('Cloud inventory contains unreachable resources.')
            items = _array(body.get(collection, []))
            budget[0] += len(items)
            if budget[0] > MAX_ITEMS:
                _fail('Cloud inventory exceeded its hard limit.')
            output.extend(items)
            next_token = body.get('nextPageToken', '')
            if not isinstance(next_token, str):
                _fail()
            if next_token == '':
                return output
            token = _text(next_token, 4096)
            if token in seen:
                _fail('Pagination cursor repeated.')
            seen.add(token)
        _fail('Cloud pagination exceeded its hard limit.')

    def _service_names(self, root, budget):
        names = set()
        for item in self._pages('/v2/' + root + '/services', SERVICE_LIST_FIELDS, 'services', budget):
            _object(item, ('name',), ('name',))
            name = _text(item['name'])
            if not name.startswith(root + '/services/') or '/' in name[len(root + '/services/'):]:
                _fail('Cloud project or region scope mismatch.')
            if re.fullmatch(r'[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?', name.rsplit('/', 1)[-1]) is None:
                _fail('Invalid Cloud Run service identity.')
            if name in names:
                _fail('Duplicate service in paginated inventory.')
            names.add(name)
        return names

    def _ownership(self, boundary, preview_id, component, service, budget):
        """Only the documented regional v1 selector route; no config is fetched."""
        selector = f'app=modtale,environment={boundary},component={component}'
        if boundary == 'branch-preview':
            selector += ',branch=' + preview_id
        path = '/apis/serving.knative.dev/v1/namespaces/' + TARGETS[boundary][0] + '/services'
        token, seen_tokens, seen_names, match = None, set(), set(), None
        for _ in range(MAX_PAGES):
            params = {'fields': OWNERSHIP_FIELDS, 'labelSelector': selector, 'limit': 100}
            if token is not None:
                params['continue'] = token
            body = self._request({'api': 'cloud-run-v1-ownership', 'origin': OWNERSHIP_ORIGIN,
                                  'method': 'GET', 'path': path, 'params': params})
            _object(body, ('items', 'metadata', 'unreachable'))
            if _array(body.get('unreachable', [])):
                _fail('Ownership inventory contains unreachable resources.')
            items = _array(body.get('items', []))
            budget[0] += len(items)
            if budget[0] > MAX_ITEMS:
                _fail('Cloud inventory exceeded its hard limit.')
            for item in items:
                _object(item, ('metadata',), ('metadata',))
                metadata = _object(item['metadata'], ('name', 'uid'), ('name', 'uid'))
                name = _text(metadata['name'], 63)
                uid = _text(metadata['uid'], 128)
                if name in seen_names:
                    _fail('Duplicate ownership inventory entry.')
                seen_names.add(name)
                if name == service:
                    match = uid
            metadata = _object(body.get('metadata', {}), ('continue',))
            next_token = metadata.get('continue', '')
            if not isinstance(next_token, str):
                _fail()
            if next_token == '':
                return match
            token = _text(next_token, 4096)
            if token in seen_tokens:
                _fail('Ownership pagination cursor repeated.')
            seen_tokens.add(token)
        _fail('Ownership pagination exceeded its hard limit.')

    def observe_service_absence(self, boundary, preview_id, component):
        """A dedicated GET accepts only an explicit HTTP 404 as absence."""
        _boundary(boundary)
        try:
            name = plan.service_name(boundary, preview_id, component)
        except Exception:
            raise InventoryError('Invalid preview service identity.') from None
        root = f'projects/{TARGETS[boundary][0]}/locations/{plan.REGION}/services/{name}'
        body = self._cloud_get('/v2/' + root, 'name', allow_not_found=True)
        if body is not None:
            _object(body, ('name',), ('name',))
            if body['name'] != root:
                _fail()
        return {'service': name, 'observation': 'not_found' if body is None else 'present'}

    def _reject_unmanaged_bundle_consumer(self, full, boundary, budget):
        """Unknown names cannot silently hide current or historical bundle refs."""
        expected = TARGETS[boundary][1]

        def optional_text(value, maximum=1024):
            if value != '':
                _text(value, maximum)
            elif not isinstance(value, str):
                _fail()

        def secret_name(value):
            value = _text(value)
            # Unrelated workloads may legitimately use a cross-project secret.
            # A matching bundle basename is conservatively refused regardless.
            if re.fullmatch(r'(?:projects/[A-Za-z0-9-]+/secrets/)?[A-Za-z0-9_-]{1,255}', value) is None:
                _fail('Unsupported unmanaged secret reference.')
            return value.rsplit('/', 1)[-1]

        def references(config):
            _object(config, ('revision', 'serviceAccount', 'containers', 'volumes'), ('containers',))
            for key in ('revision', 'serviceAccount'):
                if key in config:
                    optional_text(config[key])
            containers = _array(config['containers'])
            if not containers:
                _fail('Unmanaged configuration is incomplete.')
            found, volume_names, mount_names, container_names = False, set(), set(), set()
            for container in containers:
                _object(container, ('name', 'env', 'volumeMounts'))
                if 'name' in container:
                    name = _text(container['name'], 63)
                    if name in container_names:
                        _fail()
                    container_names.add(name)
                env_names = set()
                for env in _array(container.get('env', [])):
                    _object(env, ('name', 'valueSource'), ('name',))
                    name = _text(env['name'], 255)
                    if name in env_names:
                        _fail()
                    env_names.add(name)
                    if 'valueSource' in env:
                        source = _object(env['valueSource'], ('secretKeyRef',), ('secretKeyRef',))
                        ref = _object(source['secretKeyRef'], ('secret', 'version'), ('secret', 'version'))
                        found |= secret_name(ref['secret']) == expected
                        _text(ref['version'], 255)
                seen_mounts = set()
                for mount in _array(container.get('volumeMounts', [])):
                    _object(mount, ('name', 'mountPath', 'subPath'), ('name',))
                    name = _text(mount['name'], 255)
                    if name in seen_mounts:
                        _fail()
                    seen_mounts.add(name)
                    mount_names.add(name)
                    # Cloud SQL mounts may omit the otherwise required path.
                    for key in ('mountPath', 'subPath'):
                        if key in mount:
                            optional_text(mount[key])
            for volume in _array(config.get('volumes', [])):
                _object(volume, ('name', 'secret'), ('name',))
                name = _text(volume['name'], 255)
                if name in volume_names:
                    _fail()
                volume_names.add(name)
                if 'secret' in volume:
                    source = _object(volume['secret'], ('secret', 'items'), ('secret',))
                    found |= secret_name(source['secret']) == expected
                    paths = set()
                    for item in _array(source.get('items', [])):
                        _object(item, ('path', 'version'), ('path', 'version'))
                        path = _text(item['path'])
                        if path in paths:
                            _fail()
                        paths.add(path)
                        _text(item['version'], 255)
            if not mount_names.issubset(volume_names):
                _fail('Unmanaged mount configuration is incomplete.')
            return found

        body = self._cloud_get('/v2/' + full, SERVICE_FIELDS)
        allowed = ('name', 'uid', 'etag', 'generation', 'observedGeneration', 'reconciling', 'terminalCondition',
                   'latestReadyRevision', 'latestCreatedRevision', 'traffic', 'trafficStatuses', 'template')
        _object(body, allowed, ('name', 'uid', 'etag', 'generation', 'template'))
        if body['name'] != full:
            _fail('Unmanaged service scope mismatch.')
        _text(body['uid'], 128)
        _text(body['etag'])
        if re.fullmatch(r'[1-9][0-9]{0,18}', _text(body['generation'])) is None:
            _fail()
        if 'observedGeneration' in body and re.fullmatch(r'[0-9]{1,19}', _text(body['observedGeneration'])) is None:
            _fail()
        if type(body.get('reconciling', False)) is not bool:
            _fail()
        if 'terminalCondition' in body:
            condition = _object(body['terminalCondition'], ('state',), ('state',))
            _text(condition['state'], 128)
        referenced, ready = set(), None
        for key in ('latestCreatedRevision', 'latestReadyRevision'):
            if key in body:
                optional_text(body[key])
                if body[key]:
                    name = _revision(body[key], full)
                    referenced.add(name)
                    if key == 'latestReadyRevision':
                        ready = name
        referenced.update(self._traffic(body, full, ready))
        found = references(body['template'])
        template_revision = body['template'].get('revision')
        if template_revision:
            referenced.add(_revision(template_revision, full))
        revision_names, revision_uids = set(), set()
        for revision in self._pages('/v2/' + full + '/revisions', REVISION_LIST_FIELDS, 'revisions', budget):
            _object(revision, ('name', 'uid', 'service', 'serviceAccount', 'containers', 'volumes'),
                    ('name', 'uid', 'service', 'containers'))
            if revision['service'] not in (full, full.rsplit('/', 1)[-1]):
                _fail('Unmanaged revision service scope mismatch.')
            name = _revision(revision['name'], full)
            uid = _text(revision['uid'], 128)
            if name in revision_names or uid in revision_uids:
                _fail('Duplicate unmanaged revision identity.')
            revision_names.add(name)
            revision_uids.add(uid)
            config = {key: revision[key] for key in ('serviceAccount', 'containers', 'volumes') if key in revision}
            found |= references(config)
        if not referenced.issubset(revision_names):
            _fail('Unmanaged referenced revision inventory is incomplete.')
        if found:
            _fail('An unmanaged service references this bundle; classify it before continuing.')
        if self._cloud_get('/v2/' + full, SERVICE_FIELDS) != body:
            _fail('Unmanaged service metadata changed during inventory.')

    def cloud_run_inventory(self, boundary, *, requested_preview_ids=(), rollback_manifest=None, bundle_mode_provenance=()):
        """Collect metadata; `ready` requires complete external rollback knowledge.

        rollback_manifest is {'complete': True, 'services': {service: [revision]}}.
        The caller must establish completeness, including an explicitly empty map.

        Each bundle_mode_provenance record has exactly these fields:
          service: short backend service name
          service_uid: immutable service UID observed after the controlled update
          revision: short or fully qualified revision name
          revision_uid: immutable UID of that revision
          profile: branch-preview or pr-preview, equal to the requested boundary
          preview_id: the validated branch slug or PR number
          bundle_version: the explicit positive numeric mounted version
          verified_active: the boolean True

        A future trusted executor may create this metadata-only attestation from
        its OWN successful fixed deployment request: an approved adapter-bearing
        image, enabled=true, the exact profile/preview ID, and the fixed numeric
        bundle mount. After the operation identifies the created revision, read
        only its identity/reference metadata, bind both immutable UIDs, and run
        the required readiness checks. Retain the attestation with the existing
        controlled deployment/rollback records, including previous retained
        revisions. No new state service, credentials, or grants are introduced.
        Uncertain/failed requests, unknown images, recreated UIDs, or unverified
        historical deployments cannot generate evidence. A later configuration
        update produces a new revision and needs its own attestation.

        The adapter never reads arbitrary env.value to establish this proof. It
        does not infer active mode from mounts, labels, or a generic API success.
        These dictionaries are assertions from a trusted caller, not signatures;
        the future executor must validate their origin and freshness and must not
        accept untrusted PR artifacts or user-supplied verified_active booleans.
        """
        _boundary(boundary)
        if not isinstance(requested_preview_ids, (list, tuple)) or len(requested_preview_ids) > MAX_ITEMS:
            _fail()
        project = TARGETS[boundary][0]
        root = f'projects/{project}/locations/{plan.REGION}'
        requested = set()
        for preview_id in requested_preview_ids:
            try:
                plan._preview_id(boundary, preview_id)
            except Exception:
                raise InventoryError('Invalid requested preview identity.') from None
            if preview_id in requested:
                _fail()
            requested.add(preview_id)
        rollback_manifest = {'complete': False, 'services': {}} if rollback_manifest is None else rollback_manifest
        _object(rollback_manifest, ('complete', 'services'), ('complete', 'services'))
        if type(rollback_manifest['complete']) is not bool:
            _fail()
        rollback_revisions = rollback_manifest['services']
        if not isinstance(rollback_revisions, dict) or len(rollback_revisions) > MAX_ITEMS:
            _fail()
        provenance = self._provenance(boundary, root, bundle_mode_provenance)
        used_provenance = set()
        budget = [0]
        names = self._service_names(root, budget)
        services, references, migration, seen_rollback = [], [], [], set()
        legacy_retention = []
        if not rollback_manifest['complete']:
            migration.append({'service': None, 'revision': None, 'reason': 'rollback_inventory_not_verified'})
        for full in sorted(names):
            short = full.rsplit('/', 1)[-1]
            identity = _identity(boundary, short)
            if identity is None:
                self._reject_unmanaged_bundle_consumer(full, boundary, budget)
                continue
            preview_id, component = identity
            requested.add(preview_id)
            before = self._cloud_get('/v2/' + full, SERVICE_FIELDS)
            service, current, ready, blocked = self._normalize_service(before, full, boundary, preview_id, component)
            ownership_uid = self._ownership(boundary, preview_id, component, short, budget)
            if ownership_uid != before['uid']:
                blocked.append('managed_ownership_not_verified')
            services.append(service)
            for reason in blocked:
                migration.append({'service': short, 'revision': current, 'reason': reason})
            revisions = self._pages('/v2/' + full + '/revisions', REVISION_LIST_FIELDS, 'revisions', budget)
            revision_map = {}
            for revision in revisions:
                _object(revision, ('name', 'uid', 'service', 'serviceAccount', 'containers', 'volumes'), ('name', 'uid', 'service', 'containers'))
                if revision['service'] not in (full, short):
                    _fail('Revision service scope mismatch.')
                revision_name = _revision(revision['name'], full)
                if revision_name in revision_map:
                    _fail('Duplicate revision in paginated inventory.')
                pin, legacy = _config({key: revision[key] for key in ('containers', 'volumes') if key in revision}, boundary, component)
                uid = _text(revision['uid'], 128)
                identity_ok = revision.get('serviceAccount') == RUNTIME_ACCOUNTS[boundary]
                revision_map[revision_name] = (pin, legacy, uid, identity_ok)
            if current not in revision_map or (ready is not None and ready not in revision_map):
                _fail('A referenced revision is missing from the inventory.')
            if revision_map[current][0] != service['bundle_version']:
                _fail('Service template and current revision disagree.')
            traffic = self._traffic(before, full, ready)
            rollback = set()
            if short in rollback_revisions:
                seen_rollback.add(short)
                for revision_name in _array(rollback_revisions[short]):
                    rollback.add(_revision(revision_name, full))
            if ready is not None:
                rollback.add(ready)  # Always retain the last ready recovery revision.
            if not (set(traffic) | rollback).issubset(revision_map):
                _fail('Traffic or rollback revision inventory is incomplete.')
            if component == 'backend':
                for revision_name, (pin, legacy, uid, identity_ok) in sorted(revision_map.items()):
                    flags = traffic.get(revision_name, {'serving': False, 'tagged': False})
                    retained = flags['serving'] or flags['tagged'] or revision_name in rollback or revision_name == current
                    if retained and (pin is None or legacy):
                        # Historical legacy revisions remain valid recovery targets.
                        # No lifecycle operation may retire their original sources.
                        legacy_retention.append({'service': short, 'revision': revision_name})
                        if revision_name == current:
                            migration.append({'service': short, 'revision': revision_name, 'reason': 'legacy_revision_must_be_preserved'})
                    if retained and not identity_ok:
                        migration.append({'service': short, 'revision': revision_name, 'reason': 'revision_runtime_identity_not_verified'})
                    key = (short, revision_name)
                    evidence = provenance.get(key)
                    if evidence is not None:
                        used_provenance.add(key)
                        if (evidence['service_uid'] != before['uid'] or evidence['revision_uid'] != uid
                                or evidence['bundle_version'] != pin or evidence['preview_id'] != preview_id):
                            _fail('Active-mode provenance is stale or inconsistent.')
                    if retained and pin is not None and evidence is None:
                        migration.append({'service': short, 'revision': revision_name, 'reason': 'active_bundle_mode_not_verified'})
                    if pin is not None:
                        references.append({'preview_id': preview_id, 'service': short, 'revision': revision_name, 'version': pin,
                                           'serving': flags['serving'], 'tagged': flags['tagged'], 'rollback': revision_name in rollback})
            elif any(pin is not None or legacy for pin, legacy, _, _ in revision_map.values()):
                migration.append({'service': short, 'revision': current, 'reason': 'unexpected_frontend_secret_consumer'})
            elif any(not identity_ok for _, _, _, identity_ok in revision_map.values()):
                migration.append({'service': short, 'revision': current, 'reason': 'revision_runtime_identity_not_verified'})
            after = self._cloud_get('/v2/' + full, SERVICE_FIELDS)
            if after != before:
                _fail('Service metadata changed during inventory; retry from fresh observations.')
            if self._ownership(boundary, preview_id, component, short, budget) != ownership_uid:
                _fail('Ownership metadata changed during inventory.')
        if seen_rollback != set(rollback_revisions):
            _fail('Rollback manifest contains an unknown service.')
        if used_provenance != set(provenance):
            _fail('Active-mode provenance contains an unknown revision.')
        if self._service_names(root, [0]) != names:
            _fail('Service inventory changed during collection.')
        observed_names = {item['name'] for item in services}
        for preview_id in sorted(requested):
            for component in ('backend', 'frontend'):
                name = plan.service_name(boundary, preview_id, component)
                if name not in observed_names:
                    services.append({'preview_id': preview_id, 'component': component, 'name': name, 'observation': 'not_found',
                                     'bundle_version': None, 'revision': None, 'rollout_state': None})
        if len(services) > MAX_ITEMS or len(references) > MAX_ITEMS or len(migration) > MAX_ITEMS:
            _fail('Normalized inventory exceeded its hard limit.')
        # Status must be inspected before feeding these inventories into a planner.
        return {'status': 'initial_migration_required' if migration else 'ready',
                'services': {'project': project, 'region': plan.REGION, 'complete': not migration, 'items': services},
                'references': {'complete': not migration, 'items': references}, 'initial_migration_required': migration,
                'legacy_revisions_to_preserve': legacy_retention,
                'legacy_source_policy': 'retain_all_original_secrets_and_tokens',
                'reference_scope': 'fixed_project_cloud_run_services_only',
                'integration_status': 'synthetic_contract_requires_full_transport_verification'}

    def _provenance(self, boundary, root, evidence):
        if not isinstance(evidence, (list, tuple)) or len(evidence) > MAX_ITEMS:
            _fail()
        result = {}
        keys = ('service', 'service_uid', 'revision', 'revision_uid', 'profile', 'preview_id', 'bundle_version', 'verified_active')
        for item in evidence:
            _object(item, keys, keys)
            if item['profile'] != boundary or item['verified_active'] is not True:
                _fail('Active-mode provenance was not externally verified.')
            service = _text(item['service'], 63)
            identity = _identity(boundary, service)
            if identity != (item['preview_id'], 'backend'):
                _fail('Active-mode provenance scope mismatch.')
            _text(item['service_uid'], 128)
            _text(item['revision_uid'], 128)
            _version(item['bundle_version'])
            revision = _revision(item['revision'], root + '/services/' + service)
            key = (service, revision)
            if key in result:
                _fail('Duplicate active-mode provenance.')
            result[key] = dict(item)
        return result

    def _normalize_service(self, body, full, boundary, preview_id, component):
        allowed = ('name', 'uid', 'etag', 'generation', 'observedGeneration', 'reconciling', 'terminalCondition',
                   'latestReadyRevision', 'latestCreatedRevision', 'traffic', 'trafficStatuses', 'template')
        _object(body, allowed, ('name', 'uid', 'etag', 'generation', 'terminalCondition', 'latestCreatedRevision', 'template'))
        if body['name'] != full:
            _fail('Service scope mismatch.')
        for key in ('uid', 'etag', 'generation'):
            _text(body[key])
        if not re.fullmatch(r'[1-9][0-9]*', body['generation']):
            _fail()
        if 'observedGeneration' in body and not re.fullmatch(r'[0-9]+', _text(body['observedGeneration'])):
            _fail()
        reconciling = body.get('reconciling', False)
        if type(reconciling) is not bool:
            _fail()
        condition = _object(body['terminalCondition'], ('state',), ('state',))
        state = condition['state']
        if state not in ('CONDITION_SUCCEEDED', 'CONDITION_FAILED', 'CONDITION_PENDING', 'CONDITION_RECONCILING'):
            _fail('Unknown rollout condition.')
        current = _revision(body['latestCreatedRevision'], full)
        ready = _revision(body['latestReadyRevision'], full) if body.get('latestReadyRevision') else None
        pin, legacy = _config(body['template'], boundary, component)
        template_revision = body['template'].get('revision')
        if template_revision and _revision(template_revision, full) != current:
            _fail('Template and created revision disagree.')
        if reconciling or state in ('CONDITION_PENDING', 'CONDITION_RECONCILING'):
            rollout = 'pending'
        elif state == 'CONDITION_FAILED':
            rollout = 'failed'
        else:
            if ready != current or body.get('observedGeneration') != body['generation']:
                _fail('Ready service metadata is inconsistent.')
            rollout = 'ready'
        item = {'preview_id': preview_id, 'component': component, 'name': full.rsplit('/', 1)[-1],
                'observation': 'present', 'bundle_version': pin, 'revision': current, 'rollout_state': rollout}
        blocked = []
        if body['template'].get('serviceAccount') != RUNTIME_ACCOUNTS[boundary]:
            blocked.append('managed_ownership_not_verified')
        if legacy or (component == 'backend' and pin is None):
            blocked.append('legacy_or_missing_bundle_binding')
        return item, current, ready, blocked

    def _traffic(self, body, full, ready):
        output = {}
        actual = body.get('trafficStatuses', [])
        intended = body.get('traffic', [])
        for target in _array(actual) + _array(intended):
            _object(target, ('type', 'revision', 'percent', 'tag'))
            kind = target.get('type')
            if kind not in ('TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST', 'TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION'):
                _fail('Unknown traffic allocation.')
            if target.get('revision'):
                revision = _revision(target['revision'], full)
            elif kind == 'TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST' and ready is not None:
                revision = ready
            else:
                _fail('Traffic target has no resolvable revision.')
            percent = target.get('percent', 0)
            if type(percent) is not int or not 0 <= percent <= 100:
                _fail()
            tag = target.get('tag', '')
            if not isinstance(tag, str):
                _fail()
            if tag:
                _text(tag, 63)
            flags = output.setdefault(revision, {'serving': False, 'tagged': False})
            flags['serving'] |= percent > 0
            flags['tagged'] |= bool(tag)
        # The documented empty traffic setting is 100% to latest Ready.
        if not actual and not intended and ready is not None:
            output[ready] = {'serving': True, 'tagged': False}
        return output
