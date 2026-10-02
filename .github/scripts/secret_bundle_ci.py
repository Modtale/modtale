"""Opt-in CI execution. Payloads stay in this process or private runner files.

The workflow concurrency group is the real mutex. Every writer, deployer, cleanup
and recovery entry point uses it; this flag check alone is not a distributed lock.
No code here destroys versions, original secret resources, or Cloudflare tokens.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import time
from urllib.parse import urlsplit

from configure_secret_bundle import plan as runtime_plan
from secret_bundle import TARGETS, numeric_version
from secret_bundle_store import BundleStore, GcloudBackend, preview_updates
import secret_bundle_preview_plan as plan
from secret_bundle_preview_inventory import PreviewInventory, _identity, _config, SERVICE_FIELDS, REVISION_FIELDS, RUNTIME_ACCOUNTS
from secret_bundle_activation_evidence import verify_activation

SCRIPTS = Path(__file__).resolve().parent
LOCK = {'lock_held': True, 'lock_group': plan.LOCK_GROUP}


class CIError(RuntimeError):
    pass


class ActivationRefreshRequired(CIError):
    pass


def fail():
    raise CIError('Bundle CI preconditions failed; credentials and old versions were retained.')


def run(argv, *, env=None, timeout=1200):
    """Never expose child output/errors; callers explicitly return safe metadata."""
    result = subprocess.run(argv, env=env, capture_output=True, timeout=timeout)
    if result.returncode:
        raise CIError('Bundle CI subprocess failed; diagnostic output suppressed.')
    return result.stdout


def profile_from_env(env):
    if env.get('MODTALE_SECRET_BUNDLES_ENABLED') != 'true' or env.get('MODTALE_SECRET_BUNDLE_LOCK_GROUP') != plan.LOCK_GROUP:
        fail()
    profile = {'preview': 'branch-preview'}.get(env.get('ENV_TYPE'), env.get('ENV_TYPE'))
    if profile not in ('prod', 'dev', 'branch-preview', 'pr-preview'):
        # Cleanup/source provisioning jobs have no ENV_TYPE in the legacy flow.
        profile = 'branch-preview' if env.get('BRANCH_SLUG') or env.get('R2_BUCKET_NAME') == 'modtale-preview-template' else None
    expected_environment = {'prod': 'production', 'dev': 'develop', 'branch-preview': 'branch-preview', 'pr-preview': 'pr-preview'}.get(profile)
    if env.get('MODTALE_SECRET_BUNDLE_CI_ENVIRONMENT', expected_environment) != expected_environment:
        fail()
    boundary = 'shared' if profile in ('prod', 'dev') else profile
    if boundary not in TARGETS or env.get('PROJECT_ID') != TARGETS[boundary][0] or env.get('REGION', 'us-central1') != plan.REGION:
        fail()
    preview_id = env.get('BRANCH_SLUG') if profile == 'branch-preview' else env.get('PR_NUMBER') if profile == 'pr-preview' else None
    if preview_id is not None:
        plan._preview_id(boundary, preview_id)
    return profile, boundary, preview_id


def read_bundle_value(boundary, version, key):
    values = BundleStore(boundary, GcloudBackend(boundary)).read(version)['secrets']
    if key not in values:
        fail()
    return values[key]


def endpoint(value):
    parsed = urlsplit(value)
    if parsed.scheme != 'https' or not parsed.hostname or not parsed.hostname.endswith('.r2.cloudflarestorage.com') or parsed.username or parsed.password or parsed.port:
        fail()
    return parsed.scheme + '://' + parsed.hostname


def aws_env(values, env):
    return {**env, 'AWS_ACCESS_KEY_ID': values['access-key'], 'AWS_SECRET_ACCESS_KEY': values['secret-key'],
            'AWS_DEFAULT_REGION': 'auto', 'AWS_EC2_METADATA_DISABLED': 'true'}


def source_credentials(boundary, values, env):
    defaults = ('R2_TEMPLATE_READ_', 'BRANCH_PREVIEW_MONGODB_URI') if boundary == 'branch-preview' else ('PREVIEW_SOURCE_R2_', 'PREVIEW_MONGODB_URI')
    keys = {'access-key': env.get('SEEDING_SOURCE_R2_ACCESS_KEY_SECRET_NAME', defaults[0] + 'ACCESS_KEY'),
            'secret-key': env.get('SEEDING_SOURCE_R2_SECRET_KEY_SECRET_NAME', defaults[0] + 'SECRET_KEY'),
            'endpoint': env.get('SEEDING_SOURCE_R2_ENDPOINT_SECRET_NAME', defaults[0] + 'ENDPOINT')}
    allowed = {'branch-preview': {'R2_TEMPLATE_READ_ACCESS_KEY', 'R2_TEMPLATE_READ_SECRET_KEY', 'R2_TEMPLATE_READ_ENDPOINT',
                                 'R2_SOURCE_READ_ACCESS_KEY', 'R2_SOURCE_READ_SECRET_KEY', 'R2_SOURCE_READ_ENDPOINT'},
               'pr-preview': {'PREVIEW_SOURCE_R2_ACCESS_KEY', 'PREVIEW_SOURCE_R2_SECRET_KEY', 'PREVIEW_SOURCE_R2_ENDPOINT'}}
    if not set(keys.values()).issubset(allowed[boundary]) or any(key not in values for key in keys.values()):
        fail()
    result = {key: values[name] for key, name in keys.items()}
    result['endpoint'] = endpoint(result['endpoint'])
    return result


class BundleCI:
    def __init__(self, transport, env=None, runner=run, store_factory=None, sleeper=time.sleep):
        self.transport = transport
        self.env = dict(os.environ if env is None else env)
        self.profile, self.boundary, self.preview_id = profile_from_env(self.env)
        self.inventory = PreviewInventory(transport)
        self.runner, self.sleep = runner, sleeper
        self.store = (store_factory or (lambda boundary: BundleStore(boundary, GcloudBackend(boundary))))(self.boundary)

    def request(self, method, path, fields, *, body=None, params=None, allow_404=False):
        req = {'api': 'cloud-run-v2', 'origin': 'https://run.googleapis.com', 'method': method, 'path': path,
               'params': {'fields': fields, **(params or {})}}
        if body is not None:
            req['body'] = body
        response = self.transport(req)
        if response['status'] == 404 and allow_404:
            return None
        if response['status'] != 200:
            fail()
        return response['body']

    def lifecycle(self, *, cleanup=False):
        if self.boundary == 'shared':
            snapshot = self.inventory.github_lifecycle('branch-preview')
            expected = 'main' if self.profile == 'prod' else 'develop'
            if not any(item['name'] == expected and item['head_sha'] == self.env['GITHUB_SHA'] for item in snapshot['items']):
                fail()
            return snapshot
        snapshot = self.inventory.github_lifecycle(self.boundary)
        head = self.env.get('PR_HEAD_SHA') if self.boundary == 'pr-preview' else self.env.get('GITHUB_SHA')
        raw = self.env.get('GIT_BRANCH_NAME') if self.boundary == 'branch-preview' else None
        # A scheduled branch cleanup has no deleted head. Use a syntactically
        # valid sentinel only to recompute a nonempty raw branch slug; the full
        # lifecycle inventory must still prove that no live branch owns it.
        if cleanup and self.boundary == 'branch-preview':
            head = head or '0' * 40
        plan._check_target(self.boundary, self.preview_id, raw, head, snapshot, cleanup)
        return snapshot

    def write_metadata_env(self, **values):
        path = self.env.get('GITHUB_ENV')
        if not path:
            fail()
        with open(path, 'a', encoding='utf-8') as output:
            for key, value in values.items():
                if not re.fullmatch(r'[A-Z0-9_]+', key) or not re.fullmatch(r'[A-Za-z0-9_-]+', str(value)):
                    fail()
                output.write(f'{key}={value}\n')

    def existing_credentials(self, values):
        keys = plan.credential_keys(self.boundary, self.preview_id)
        present = {key: values[key] for key in keys if key in values}
        if present and len(present) != 4:
            fail()
        return {key.rsplit('-r2-', 1)[1]: value for key, value in present.items()}

    def check_runtime(self, credentials):
        if not credentials:
            return False
        config = aws_env(credentials, self.env)
        uri = endpoint(credentials['endpoint'])
        with tempfile.TemporaryDirectory(prefix='modtale-bundle-check-') as directory:
            path = Path(directory) / 'probe'
            path.write_bytes(b'ok')
            key = '.modtale-preview/bundle-credential-check-' + self.env.get('GITHUB_RUN_ID', 'manual')
            command = ['aws', '--endpoint-url', uri, 's3api']
            self.lifecycle()
            try:
                self.runner(command + ['put-object', '--bucket', self.env['R2_BUCKET_NAME'], '--key', key, '--body', str(path)], env=config)
                self.runner(command + ['head-object', '--bucket', self.env['R2_BUCKET_NAME'], '--key', key], env=config)
            except CIError:
                return False
            finally:
                self.lifecycle()
                try:
                    self.runner(command + ['delete-object', '--bucket', self.env['R2_BUCKET_NAME'], '--key', key], env=config)
                except CIError:
                    pass
        return True

    def require_provision_approval(self, *, replacement=False):
        self.validate_preview_targets()
        if self.env.get('RUNTIME_SERVICE_ACCOUNT') != RUNTIME_ACCOUNTS[self.boundary]:
            raise CIError('Preview credential provisioning requires the fixed isolated runtime identity.')
        service = plan.service_name(self.boundary, self.preview_id, 'backend')
        approved = self.env.get('MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED') == service
        if not approved and not replacement and self.env.get('MODTALE_SECRET_BUNDLE_NEW_SERVICE_APPROVED') == service:
            approved = self.inventory.observe_service_absence(self.boundary, self.preview_id, 'backend')['observation'] == 'not_found'
        if not approved:
            raise CIError('Preview credential provisioning needs explicit approval for this exact service.')
        self.lifecycle()

    def require_legacy_provision_approval(self, *, replacement=False):
        self.require_provision_approval(replacement=replacement)
        service = plan.service_name(self.boundary, self.preview_id, 'backend')
        if replacement or self.env.get('MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED') == service:
            return
        # A missing token-id payload never proves that retained credential
        # resources are absent. Metadata list failures are fatal, not absence.
        project = TARGETS[self.boundary][0]
        names = plan.credential_keys(self.boundary, self.preview_id)
        query = 'name~"/(' + '|'.join(re.escape(name) for name in names) + ')$"'
        resources = json.loads(self.runner(['gcloud', 'secrets', 'list', '--project', project,
                                           '--filter', query, '--format=json(name)', '--quiet']))
        if not isinstance(resources, list) or len(resources) > 4:
            fail()
        number = {'gen-lang-client-0244308719':'145553429208','modtale-pr-preview':'759035195996'}[project]
        allowed = {f'projects/{alias}/secrets/{name}' for alias in (project, number) for name in names}
        if any(not isinstance(item, dict) or set(item) != {'name'} or item['name'] not in allowed for item in resources):
            fail()
        if resources:
            raise CIError('Retained original credential resources require exact replacement approval.')
        self.lifecycle()

    def ensure_legacy_accessor(self, name):
        self.validate_preview_targets()
        runtime = RUNTIME_ACCOUNTS[self.boundary]
        if self.env.get('RUNTIME_SERVICE_ACCOUNT') != runtime or name not in plan.credential_keys(self.boundary, self.preview_id):
            fail()
        project = TARGETS[self.boundary][0]
        policy = json.loads(self.runner(['gcloud', 'secrets', 'get-iam-policy', name, '--project', project, '--format=json', '--quiet']))
        member = 'serviceAccount:' + runtime
        if any(binding.get('role') == 'roles/secretmanager.secretAccessor'
               and member in binding.get('members', []) and not binding.get('condition')
               for binding in policy.get('bindings', [])):
            return
        self.require_provision_approval()
        self.lifecycle()
        self.runner(['gcloud', 'secrets', 'add-iam-policy-binding', name, '--project', project,
                     '--member', member, '--role', 'roles/secretmanager.secretAccessor', '--quiet'])

    def provision(self, *, replacement=False):
        self.require_provision_approval(replacement=replacement)
        # The existing provisioner retains all old tokens. Its mask/output data
        # is captured, never printed or appended to the job-wide environment.
        self.lifecycle()
        with tempfile.TemporaryDirectory(prefix='modtale-bundle-token-') as directory:
            os.chmod(directory, 0o700)
            file = Path(directory) / 'runtime.env'
            file.touch(mode=0o600)
            env = {**self.env, 'R2_RUNTIME_ENV_FILE': str(file), 'GITHUB_ENV': '', 'GITHUB_ACTIONS': 'false',
                   'R2_BUNDLE_LIFECYCLE_SCRIPT': str(SCRIPTS / 'secret_bundle_ci.py')}
            self.runner(['node', str(SCRIPTS / 'cloudflare-r2-preview.mjs'), 'provision'], env=env)
            found = {}
            for line in file.read_text().splitlines():
                key, raw = line.split('=', 1)
                value = shlex.split(raw)
                if len(value) != 1 or key in found:
                    fail()
                found[key] = value[0]
            names = {'access-key': 'R2_RUNTIME_ACCESS_KEY', 'secret-key': 'R2_RUNTIME_SECRET_KEY',
                     'endpoint': 'R2_RUNTIME_ENDPOINT', 'token-id': 'R2_RUNTIME_TOKEN_ID'}
            if set(found) != set(names.values()):
                fail()
            return {key: found[name] for key, name in names.items()}

    def prepare(self):
        if self.boundary != 'shared':
            self.validate_preview_targets()
        self.lifecycle()
        if self.boundary == 'shared':
            pin = numeric_version(self.env.get('MODTALE_SECRET_BUNDLE_SHARED_VERSION', ''))
            # Validate only the fixed version's metadata. The runtime validates
            # bundle contents; shared CI never needs this credential payload.
            project, secret = TARGETS['shared']
            name = f'projects/{project}/secrets/{secret}/versions/{pin}'
            canonical = name.replace('/gen-lang-client-0244308719/', '/145553429208/')
            response = self.transport({'api': 'secret-manager-metadata',
                'origin': 'https://secretmanager.googleapis.com', 'method': 'GET',
                'path': '/v1/' + name, 'params': {'fields': 'name,state'}})
            if (not isinstance(response, dict) or set(response) != {'status', 'body'}
                    or type(response['status']) is not int or response['status'] != 200
                    or not isinstance(response['body'], dict) or set(response['body']) != {'name', 'state'}
                    or response['body']['name'] not in (name, canonical) or response['body']['state'] != 'ENABLED'):
                fail()
            self.write_metadata_env(MODTALE_SECRET_BUNDLE_VERSION=pin)
            return
        before = self.lifecycle()
        base = numeric_version(self.store.backend.latest_enabled())
        values = self.store.read(base)['secrets']
        source = source_credentials(self.boundary, values, self.env)
        bucket = self.env['SEEDING_SOURCE_R2_BUCKET_NAME']
        if bucket in ('modtale-binaries', self.env['R2_BUCKET_NAME']):
            fail()
        self.runner(['aws', '--endpoint-url', source['endpoint'], 's3api', 'head-bucket', '--bucket', bucket], env=aws_env(source, self.env))
        existing = self.existing_credentials(values)
        valid = self.check_runtime(existing)
        credentials = existing if valid else self.provision(replacement=bool(existing))
        recheck = self.lifecycle()
        intent = plan.plan_credential_upsert(self.boundary, self.preview_id,
                    raw_branch=self.env.get('GIT_BRANCH_NAME') if self.boundary == 'branch-preview' else None,
                    expected_head=self.env.get('PR_HEAD_SHA') if self.boundary == 'pr-preview' else self.env['GITHUB_SHA'],
                    before=before, recheck=recheck, base_version=base, credentials_changed=not valid, **LOCK)
        self.lifecycle()
        plan.require_current_base(intent, self.store.backend.latest_enabled(), **LOCK)
        result = self.store.update(preview_updates(self.boundary, self.preview_id, credentials), lock_held=True)
        # Publication is one complete four-key snapshot. A retried run fans out
        # even when it reuses credentials, so interrupted consumers are resumed.
        fanout = self.fanout(result['version'], allow_own_pending=True)
        self.write_metadata_env(MODTALE_SECRET_BUNDLE_VERSION=result['version'],
                                R2_RUNTIME_CREDENTIALS_REPLACED=str(result['changed']).lower(),
                                MODTALE_SECRET_BUNDLE_DEPLOYMENT_PENDING=str(fanout['status'] == 'deferred_own_deployment').lower())

    def proof(self, profile, identifier, service, raw):
        full = raw['name']
        pin, legacy = _config(raw['template'], profile, 'backend')
        if legacy or not pin or raw['template'].get('serviceAccount') != RUNTIME_ACCOUNTS[profile]:
            fail()
        if self.inventory._ownership(profile, identifier, 'backend', service, [0]) != raw['uid']:
            fail()
        revision = raw['latestCreatedRevision'].rsplit('/', 1)[-1]
        fields = 'name,uid,service,createTime,serviceAccount,' + REVISION_FIELDS.split('serviceAccount,', 1)[1]
        candidate = self.request('GET', '/v2/' + full + '/revisions/' + revision, fields)
        if candidate['serviceAccount'] != RUNTIME_ACCOUNTS[profile]:
            fail()
        config = {key: candidate[key] for key in ('serviceAccount', 'containers', 'volumes') if key in candidate}
        if _config(config, profile, 'backend') != (pin, False):
            fail()
        active = verify_activation(self.transport, profile, revision, candidate['createTime'], identifier)
        if active.get('verified_active') is not True:
            if active.get('reason') == 'activation_not_observed':
                raise ActivationRefreshRequired('An old activation event is unavailable; controlled refresh is required.')
            fail()
        return {'verified_active': True, 'ownership_verified': True, 'profile': profile, 'preview_id': identifier,
                'service_uid': raw['uid'], 'revision_uid': candidate['uid'], 'bundle_version': pin}

    def refresh_preview_activation(self, identifier, owner, target):
        """Re-establish a fresh marker through the ordinary zero-traffic gates.

        The old baseline is never relabeled active. Fixed scope/account controls
        and the exact existing immutable image are reapplied to a NEW candidate.
        No credential provisioning, publication, or image build occurs here.
        """
        from secret_bundle_ci_deploy import deploy, DEPLOY_SERVICE_FIELDS, RUNTIME_IDENTITIES
        service = plan.service_name(self.boundary, identifier, 'backend')
        full = f'projects/{TARGETS[self.boundary][0]}/locations/{plan.REGION}/services/{service}'
        current = self.request('GET', '/v2/' + full, DEPLOY_SERVICE_FIELDS)
        image = current['template']['containers'][0]['image']
        env = {key: self.env[key] for key in ('GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT') if key in self.env}
        env.update(MODTALE_SECRET_BUNDLE_FANOUT_REFRESH='true', MODTALE_SECRET_BUNDLES_ENABLED='true', MODTALE_SECRET_BUNDLE_LOCK_GROUP=plan.LOCK_GROUP,
                   MODTALE_SECRET_BUNDLE_VERSION=target, PROJECT_ID=TARGETS[self.boundary][0], REGION=plan.REGION,
                   ENV_TYPE=self.boundary, BACKEND_SERVICE=service, RUNTIME_SERVICE_ACCOUNT=RUNTIME_IDENTITIES[self.boundary])
        if self.boundary == 'branch-preview':
            env.update(BRANCH_SLUG=identifier, GIT_BRANCH_NAME=owner['name'], GITHUB_SHA=owner['head_sha'])
        else:
            env.update(PR_NUMBER=identifier, PR_HEAD_SHA=owner['head_sha'])
        refreshed = BundleCI(self.transport, env, runner=self.runner, sleeper=self.sleep)
        deploy(refreshed, ['--image', image])

    def finalize_preview(self):
        if self.boundary == 'shared':
            fail()
        self.lifecycle()
        target = numeric_version(self.env.get('MODTALE_SECRET_BUNDLE_VERSION', ''))
        if self.store.backend.latest_enabled() != target:
            fail()
        self.fanout(target)

    def fanout(self, target, *, allow_own_pending=False):
        from secret_bundle_rollout import RolloutExecutor, SecretManagerJournal
        root = f'projects/{TARGETS[self.boundary][0]}/locations/{plan.REGION}'
        names = self.inventory._service_names(root, [0])
        owners = plan._lifecycle(self.boundary, self.inventory.github_lifecycle(self.boundary))
        from secret_bundle_ci_deploy import DeploymentJournal, defer_own_pending_deployment
        pending = []
        for full in sorted(names):
            identity = _identity(self.boundary, full.rsplit('/', 1)[-1])
            if identity is None or identity[1] != 'backend':
                continue
            identifier = identity[0]
            ordinary, _ = DeploymentJournal(self, self.boundary, identifier).load()
            if ordinary is not None and ordinary['phase'] != 'complete':
                if ordinary.get('intent_kind') == 'fanout_refresh':
                    owner = owners.get(identifier)
                    if owner is None or owner['state'] != 'open':
                        raise CIError('A closed preview has pending ordinary refresh evidence; cleanup or explicit recovery is required.')
                    self.refresh_preview_activation(identifier, owner, target)
                    continue
                if not allow_own_pending or identifier != self.preview_id:
                    raise CIError('Another or unresolved backend deployment must finish before strict fanout.')
                if defer_own_pending_deployment(self, self.boundary, identifier):
                    pending.append(identifier)
        if pending:
            # Do not reserve a second active journal while the own ordinary
            # checkpoint holds recovery capacity. Nothing is called complete;
            # deployment is forced and a later strict pass covers every service.
            for full in sorted(names):
                identity = _identity(self.boundary, full.rsplit('/', 1)[-1])
                if identity is None or identity[1] != 'backend':
                    continue
                mounted, _ = SecretManagerJournal(self.transport, self.boundary, identity[0]).load()
                if mounted is not None and mounted['phase'] not in ('complete', 'rolled_back'):
                    raise CIError('Overlapping pending mount and ordinary deployments need recovery.')
            return {'status': 'deferred_own_deployment', 'preview_id': pending[0], 'version': target}
        # A pending journal reserves recovery capacity on the shared container.
        # Resume it before preparing any new service journal, regardless of name
        # order, so a canceled fanout cannot starve its own recovery space.
        for full in sorted(names):
            service = full.rsplit('/', 1)[-1]
            identity = _identity(self.boundary, service)
            if identity is None or identity[1] != 'backend':
                continue
            identifier = identity[0]
            journal = SecretManagerJournal(self.transport, self.boundary, identifier)
            saved, _ = journal.load()
            if saved is not None and saved['phase'] not in ('complete', 'rolled_back'):
                executor = RolloutExecutor(self.transport, journal, self.inventory.github_lifecycle)
                owner = owners.get(identifier)
                if owner is None or owner['state'] != 'open' or owner['head_sha'] != saved['head_sha']:
                    executor.rollback(**LOCK)
                self.finish_rollout(executor)
        for full in sorted(names):
            service = full.rsplit('/', 1)[-1]
            identity = _identity(self.boundary, service)
            if identity is None:
                self.inventory._reject_unmanaged_bundle_consumer(full, self.boundary, [0])
                continue
            identifier, component = identity
            raw = self.request('GET', '/v2/' + full, SERVICE_FIELDS)
            if component == 'frontend':
                if _config(raw['template'], self.boundary, component) != (None, False):
                    fail()
                continue
            if identifier not in owners or owners[identifier]['state'] != 'open':
                # Orphans keep their exact pins and versions until explicit cleanup.
                continue
            from secret_bundle_ci_deploy import assert_no_pending_deployment, ordinary_terminal_transfer, completed_journal_evictions, run_attempt
            assert_no_pending_deployment(self, self.boundary, identifier)
            journal = SecretManagerJournal(self.transport, self.boundary, identifier)
            executor = RolloutExecutor(self.transport, journal, self.inventory.github_lifecycle)
            saved, _ = journal.load()
            if saved is not None:
                if saved['phase'] not in ('complete', 'rolled_back'):
                    self.finish_rollout(executor)
                raw = self.request('GET', '/v2/' + full, SERVICE_FIELDS)
            try:
                proof = self.proof(self.boundary, identifier, service, raw)
            except ActivationRefreshRequired:
                self.refresh_preview_activation(identifier, owners[identifier], target)
                raw = self.request('GET', '/v2/' + full, SERVICE_FIELDS)
                proof = self.proof(self.boundary, identifier, service, raw)
            if proof['bundle_version'] == target:
                continue
            transfer = ordinary_terminal_transfer(self, self.boundary, identifier)
            evictions = completed_journal_evictions(self, self.boundary, identifier)
            journal = SecretManagerJournal(self.transport, self.boundary, identifier, consume_terminal=transfer, evict_completed=evictions)
            executor = RolloutExecutor(self.transport, journal, self.inventory.github_lifecycle)
            owner = owners[identifier]
            transaction = hashlib.sha256((self.env['GITHUB_RUN_ID'] + ':' + run_attempt(self.env) + ':' + service + ':' + target).encode()).hexdigest()[:12]
            outcome = executor.begin(self.boundary, target, transaction, proof, preview_id=identifier,
                raw_branch=owner.get('name'), head_sha=owner['head_sha'], replace_terminal=saved is not None, **LOCK)
            if outcome['status'] != 'unchanged':
                self.finish_rollout(executor)
        if self.inventory._service_names(root, [0]) != names:
            fail()
        return {'status': 'complete', 'version': target}

    def finish_rollout(self, executor):
        deadline = time.monotonic() + 900
        while time.monotonic() < deadline:
            result = executor.advance(**LOCK)
            if result['status'] == 'complete':
                return result
            if result['status'] == 'rolled_back':
                raise CIError('Bundle repin failed and the original pin was restored. Review before retrying publication fanout.')
            if result['status'] in ('revision_failed', 'operation_failed'):
                executor.rollback(**LOCK)
            self.sleep(5)
        raise CIError('Bundle rollout is pending; rerun under the same lock to resume its durable checkpoint.')

    def absence(self):
        for component in ('backend', 'frontend'):
            if self.inventory.observe_service_absence(self.boundary, self.preview_id, component)['observation'] != 'not_found':
                fail()

    def validate_preview_targets(self, *, cleanup=False):
        if self.boundary == 'branch-preview':
            bucket = 'modtale-branch-' + self.preview_id
            database = 'modtale-' + self.preview_id
            tag = self.preview_id
        elif self.boundary == 'pr-preview':
            bucket = database = 'modtale-pr-' + self.preview_id
            head = self.env.get('PR_HEAD_SHA', '')
            plan._sha(head)
            tag = 'pr-' + self.preview_id + '-' + head[:7]
        else:
            fail()
        if self.env.get('R2_BUCKET_NAME') != bucket:
            fail()
        if self.env.get('DB_NAME', database) != database:
            fail()
        if cleanup and (self.env.get('DB_NAME') != database or self.env.get('IMAGE_TAG') != tag):
            fail()
        if self.env.get('SEEDING_SOURCE_R2_BUCKET_NAME') in ('modtale-binaries', bucket):
            fail()

    def release_absent_preview_padding(self):
        """Keep exact recovery records but release obsolete physical reservations."""
        from secret_bundle_ci_deploy import DeploymentJournal
        from secret_bundle_rollout import SecretManagerJournal
        ordinary = DeploymentJournal(self)
        mounted = SecretManagerJournal(self.transport, self.profile, self.preview_id)
        metadata = ordinary.request('GET')
        annotations = dict(metadata.get('annotations', {}))
        changed = False
        for journal, decode in ((ordinary, ordinary.decode), (mounted, mounted._decode)):
            encoded = annotations.get(journal.key)
            if encoded is None:
                continue
            decode(encoded)  # Malformed or foreign-scope evidence is never discarded.
            compact = encoded.split('.', 1)[0]
            if compact != encoded:
                annotations[journal.key] = compact
                changed = True
        if not changed:
            return
        self.lifecycle(cleanup=True)
        self.absence()
        self.lifecycle(cleanup=True)
        ordinary.request('PATCH', body={'name': ordinary.name, 'etag': metadata['etag'], 'annotations': annotations})

    def cleanup(self, *, legacy=False):
        if self.boundary == 'shared':
            fail()
        self.validate_preview_targets(cleanup=True)
        self.lifecycle(cleanup=True)
        project = TARGETS[self.boundary][0]
        for component in ('backend', 'frontend'):
            self.lifecycle(cleanup=True)
            name = plan.service_name(self.boundary, self.preview_id, component)
            full = f'/v2/projects/{project}/locations/{plan.REGION}/services/{name}'
            current = self.request('GET', full, 'name,uid,etag', allow_404=True)
            if current is not None:
                if self.inventory._ownership(self.boundary, self.preview_id, component, name, [0]) != current['uid']:
                    fail()
                self.lifecycle(cleanup=True)
                operation = self.request('DELETE', full, 'name,done,error(code)', params={'etag': current['etag']})
                deadline = time.monotonic() + 600
                while self.inventory.observe_service_absence(self.boundary, self.preview_id, component)['observation'] != 'not_found':
                    if time.monotonic() >= deadline:
                        fail()
                    self.sleep(5)
        self.absence()
        if not legacy:
            self.release_absent_preview_padding()
        presence = json.loads(self.runner(['node', str(SCRIPTS / 'cloudflare-r2-preview.mjs'), 'inspect-bucket'], env=self.env))
        if set(presence) != {'exists'} or type(presence['exists']) is not bool:
            fail()
        mongo_key = 'BRANCH_PREVIEW_MONGODB_URI' if self.boundary == 'branch-preview' else 'PREVIEW_MONGODB_URI'
        if legacy:
            def original_value(name):
                return self.runner(['gcloud', 'secrets', 'versions', 'access', 'latest', '--project', project,
                                    '--secret', name]).decode().strip()
            values = {mongo_key: original_value(mongo_key)}
            existing = {suffix: original_value(f'{self.boundary}-{self.preview_id}-r2-{suffix}')
                        for suffix in ('access-key', 'secret-key', 'endpoint')} if presence['exists'] else {}
            if not values[mongo_key] or (existing and not all(existing.values())):
                fail()
        else:
            base = numeric_version(self.store.backend.latest_enabled())
            values = self.store.read(base)['secrets']
            existing = self.existing_credentials(values)
        self.lifecycle(cleanup=True)
        self.absence()
        self.runner(['node', str(SCRIPTS / 'drop-bundle-preview-db.cjs')], env={**self.env, 'MONGODB_URI': values[mongo_key],
                    'BUNDLE_PREVIEW_ID': self.preview_id, 'BUNDLE_PREVIEW_BOUNDARY': self.boundary})
        if existing and presence['exists']:
            self.lifecycle(cleanup=True)
            self.absence()
            self.runner(['aws', '--endpoint-url', endpoint(existing['endpoint']), 's3', 'rm',
                         's3://' + self.env['R2_BUCKET_NAME'], '--recursive'], env=aws_env(existing, self.env))
        self.lifecycle(cleanup=True)
        self.absence()
        self.runner(['node', str(SCRIPTS / 'cloudflare-r2-preview.mjs'), 'cleanup'],
                    env={**self.env, 'CLOUDFLARE_API_TOKEN_PROVISIONER': '', 'R2_RUNTIME_TOKEN_ID': '',
                         'R2_BUNDLE_LIFECYCLE_SCRIPT': str(SCRIPTS / 'secret_bundle_ci.py')})
        for component in ('backend', 'frontend'):
            self.lifecycle(cleanup=True)
            self.absence()
            try:
                self.runner(['gcloud', 'container', 'images', 'untag', f'gcr.io/{project}/modtale-{component}:' + self.env['IMAGE_TAG'], '--quiet'])
            except CIError:
                pass  # Image tag absence does not grant secret-removal authority.
        if legacy:
            return
        before, recheck = self.lifecycle(cleanup=True), self.lifecycle(cleanup=True)
        services = {'project': project, 'region': plan.REGION, 'complete': True, 'items': []}
        for component in ('backend', 'frontend'):
            self.absence()
            services['items'].append({'preview_id': self.preview_id, 'component': component,
                'name': plan.service_name(self.boundary, self.preview_id, component), 'observation': 'not_found',
                'bundle_version': None, 'revision': None, 'rollout_state': None})
        intent = plan.plan_cleanup(self.boundary, self.preview_id, raw_branch=self.env.get('GIT_BRANCH_NAME') if self.boundary == 'branch-preview' else None,
            expected_head=self.env.get('PR_HEAD_SHA') if self.boundary == 'pr-preview' else self.env.get('GITHUB_SHA', '0' * 40),
            before=before, recheck=recheck, services=services, base_version=base,
            existing_key_names=plan.credential_keys(self.boundary, self.preview_id) if existing else [], **LOCK)
        self.lifecycle(cleanup=True)
        self.absence()
        plan.require_current_base(intent, self.store.backend.latest_enabled(), **LOCK)
        result = self.store.update({}, plan.credential_keys(self.boundary, self.preview_id), lock_held=True)
        self.fanout(result['version'])

    def require_template_provision_approval(self):
        if (self.boundary != 'branch-preview' or self.env.get('R2_BUCKET_NAME') != 'modtale-preview-template'
                or self.env.get('R2_TOKEN_NAME') != 'modtale-preview-template-reader'
                or self.env.get('RUNTIME_SERVICE_ACCOUNT') != RUNTIME_ACCOUNTS['branch-preview']
                or self.env.get('MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED') != 'modtale-preview-template-reader'):
            raise CIError('Template-reader provisioning needs explicit approval for that exact reader.')

    def validate_source(self):
        pin = numeric_version(self.store.backend.latest_enabled())
        source_credentials(self.boundary, self.store.read(pin)['secrets'], self.env)




def resolve_settings(env):
    """Read only the selected environment's uniquely named step inputs.

    Generic repository-level flags/pins are intentionally ignored. The workflow
    supplies these vars-context inputs on the runner after selecting its GitHub
    environment, then subsequent steps consume only validated GITHUB_ENV output.
    """
    environment = env.get('BUNDLE_SETTINGS_ENVIRONMENT')
    prefixes = {'production': 'PRODUCTION', 'develop': 'DEVELOP',
                'branch-preview': 'BRANCH_PREVIEW', 'pr-preview': 'PR_PREVIEW'}
    if environment not in prefixes:
        raise CIError('A fixed GitHub deployment environment is required.')
    prefix = 'BUNDLE_SETTINGS_' + prefixes[environment] + '_'
    enabled = env.get(prefix + 'ENABLED', '') or 'false'
    if enabled not in ('true', 'false'):
        raise CIError('The selected environment bundle flag must be true or false.')
    version = env.get(prefix + 'VERSION', '') if environment in ('production', 'develop') else ''
    if enabled == 'true' and environment in ('production', 'develop'):
        numeric_version(version)
    elif version:
        numeric_version(version)
    result = {'MODTALE_SECRET_BUNDLE_CI_ENVIRONMENT': environment,
              'MODTALE_SECRET_BUNDLES_ENABLED': enabled,
              'MODTALE_SECRET_BUNDLE_SHARED_VERSION': version,
              'MODTALE_SECRET_BUNDLE_NEW_SERVICE_APPROVED': '',
              'MODTALE_SECRET_BUNDLE_CREDENTIAL_PROVISION_APPROVED': ''}
    for key in ('NEW_SERVICE_APPROVED', 'CREDENTIAL_PROVISION_APPROVED'):
        value = env.get(prefix + key, '')
        if value:
            if environment in ('production', 'develop'):
                expected = 'modtale-backend' + ('-dev' if environment == 'develop' else '')
                if value != expected:
                    fail()
            elif environment == 'branch-preview':
                if key == 'CREDENTIAL_PROVISION_APPROVED' and value == 'modtale-preview-template-reader':
                    result['MODTALE_SECRET_BUNDLE_' + key] = value
                    continue
                if not value.startswith('modtale-backend-'):
                    fail()
                plan._preview_id('branch-preview', value[len('modtale-backend-'):])
            elif re.fullmatch(r'modtale-pr-[1-9][0-9]*-backend', value) is None:
                fail()
        result['MODTALE_SECRET_BUNDLE_' + key] = value
    return result


def write_settings(env):
    resolved = resolve_settings(env)
    with open(env['GITHUB_ENV'], 'a', encoding='utf-8') as output:
        for key, value in resolved.items():
            output.write(f'{key}={value}\n')


def validate_mode(transport, env):
    enabled = env.get('MODTALE_SECRET_BUNDLES_ENABLED', 'false')
    if enabled not in ('true', 'false'):
        raise CIError('Bundle mode must be explicitly true or false.')
    checked = BundleCI(transport, {**env, 'MODTALE_SECRET_BUNDLES_ENABLED': 'true'})
    profile, boundary, identifier = checked.profile, checked.boundary, checked.preview_id
    service = ('modtale-backend' if profile == 'prod' else 'modtale-backend-dev') if boundary == 'shared' else plan.service_name(boundary, identifier, 'backend')
    if env.get('BACKEND_SERVICE') != service:
        fail()
    full = f'/v2/projects/{TARGETS[boundary][0]}/locations/{plan.REGION}/services/{service}'
    raw = checked.request('GET', full, SERVICE_FIELDS, allow_404=True)
    if raw is None:
        return
    references = []
    for volume in raw['template'].get('volumes', []):
        secret = volume.get('secret', {})
        name = secret.get('secret', '').rsplit('/', 1)[-1]
        if name in {value[1] for value in TARGETS.values()}:
            references.append((name, secret.get('items', [])))
    if enabled == 'false' and references:
        raise CIError('Backend already uses a bundle. Restore its opt-in flag or use the explicit owner rollback flow; legacy aliases were not attached.')
    if enabled == 'true':
        if len(references) != 1 or references[0][0] != TARGETS[boundary][1] or len(references[0][1]) != 1:
            raise CIError('Initial owner-controlled bundle activation is required before normal deployment.')
        numeric_version(references[0][1][0].get('version', ''))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('operation', choices=('prepare', 'finalize-preview', 'cleanup', 'deploy', 'validate-source', 'check-lifecycle', 'check-cleanup', 'validate-mode', 'cleanup-legacy', 'resolve-settings', 'guard-legacy-provision', 'guard-template-provision', 'ensure-legacy-accessor'))
    parser.add_argument('args', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    try:
        if args.operation == 'resolve-settings':
            write_settings(dict(os.environ))
            return 0
        from secret_bundle_transport import create_transport
        transport = create_transport()
        if args.operation == 'validate-mode':
            validate_mode(transport, dict(os.environ))
            return 0
        context = dict(os.environ)
        if args.operation == 'cleanup-legacy' and context.get('MODTALE_SECRET_BUNDLES_ENABLED', 'false') != 'false':
            fail()
        if args.operation in ('cleanup-legacy', 'check-cleanup', 'guard-legacy-provision', 'guard-template-provision', 'ensure-legacy-accessor', 'check-lifecycle'):
            if context.get('MODTALE_SECRET_BUNDLES_ENABLED', 'false') not in ('true', 'false'):
                fail()
            context['MODTALE_SECRET_BUNDLES_ENABLED'] = 'true'
        ci = BundleCI(transport, context)
        if args.operation == 'ensure-legacy-accessor':
            if len(args.args) != 1:
                fail()
            ci.ensure_legacy_accessor(args.args[0])
        elif args.operation == 'guard-legacy-provision':
            if args.args not in (['new'], ['replacement']):
                fail()
            ci.require_legacy_provision_approval(replacement=args.args == ['replacement'])
        elif args.operation == 'guard-template-provision':
            ci.require_template_provision_approval()
        elif args.operation == 'cleanup-legacy':
            ci.cleanup(legacy=True)
        elif args.operation in ('check-lifecycle', 'check-cleanup', 'validate-mode'):
            if ci.boundary != 'shared':
                ci.validate_preview_targets(cleanup=args.operation == 'check-cleanup')
            ci.lifecycle(cleanup=args.operation == 'check-cleanup')
            if args.operation == 'check-cleanup':
                ci.validate_preview_targets(cleanup=True)
                ci.absence()
        elif args.operation == 'deploy':
            from secret_bundle_ci_deploy import deploy
            print(deploy(ci, args.args[1:] if args.args[:1] == ['--'] else args.args))
        else:
            getattr(ci, args.operation.replace('-', '_'))()
        return 0
    except CIError as error:
        print(str(error), file=sys.stderr)
        return 1
    except Exception:
        print('Bundle CI stopped safely; private diagnostics suppressed. Original secrets, versions and tokens are retained.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
