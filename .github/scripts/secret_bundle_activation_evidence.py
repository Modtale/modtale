"""Secret-free, exact-revision startup evidence through an injected read transport.

No auth client, credentials, or executable entrypoint. The transport must preserve
server-side response masks and suppress non-200 bodies. The calling operator needs
existing log-read authority; this module never grants it. Absent evidence means
unverified, not disabled. Pair positive evidence with current immutable revision
UID, numeric mount, readiness, and ownership observations before any rollout.
Application stdout can be forged by untrusted preview code. A log match is only
corroboration of a trusted deployment request/image, never independent authority.
"""
import re
from datetime import datetime

from secret_bundle_preview_plan import service_name

FIELDS = 'entries(insertId),nextPageToken'
EVENT = 'modtale_secret_bundle_activation'
MAX_PAGES = 1000


class EvidenceError(ValueError):
    pass


def _safe_identifier(value, maximum):
    if not isinstance(value,str) or not value or len(value)>maximum or any(ord(c)<32 or ord(c)==127 for c in value):
        return False
    try:
        value.encode('utf-8')
    except UnicodeError:
        return False
    return True


def activation_request(profile, revision, created_at, preview_id=None):
    # Must come from the current revision metadata paired with its immutable UID.
    if not isinstance(created_at, str) or not re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,9})?Z', created_at):
        raise EvidenceError('Trusted revision creation time is required.')
    try:
        datetime.fromisoformat(created_at.replace('Z', '+00:00'))
    except ValueError:
        raise EvidenceError('Invalid revision creation time.') from None
    if profile in ('prod', 'dev'):
        if preview_id is not None:
            raise EvidenceError('Unexpected preview identity.')
        project = 'gen-lang-client-0244308719'
        service = 'modtale-backend' if profile == 'prod' else 'modtale-backend-dev'
    elif profile in ('branch-preview', 'pr-preview'):
        service = service_name(profile, preview_id, 'backend')
        project = 'modtale-pr-preview' if profile == 'pr-preview' else 'gen-lang-client-0244308719'
    else:
        raise EvidenceError('Unsupported startup evidence profile.')
    if not isinstance(revision, str) or len(revision) > 63 or not re.fullmatch(re.escape(service)+r'-[a-z0-9-]+[a-z0-9]', revision):
        raise EvidenceError('Invalid revision identity.')
    filters = {
        'resource.type': 'cloud_run_revision', 'resource.labels.project_id': project,
        'resource.labels.location': 'us-central1', 'resource.labels.service_name': service,
        'resource.labels.revision_name': revision, 'jsonPayload.event': EVENT, 'jsonPayload.profile': profile,
    }
    expression = '\n'.join(f'{key}="{value}"' for key, value in filters.items()) + '\njsonPayload.activated=true' + f'\ntimestamp>="{created_at}"'
    return {'api': 'cloud-logging', 'origin': 'https://logging.googleapis.com', 'method': 'POST',
            'path': '/v2/entries:list', 'params': {'fields': FIELDS},
            'body': {'resourceNames': ['projects/'+project], 'filter': expression,
                     'pageSize': 100, 'orderBy': 'timestamp desc'}}


def verify_activation(transport, profile, revision, created_at, preview_id=None):
    request = activation_request(profile, revision, created_at, preview_id)
    seen = set()
    for _ in range(MAX_PAGES):
        try:
            response = transport(request)
        except Exception:
            raise EvidenceError('Startup evidence request failed; details suppressed.') from None
        if not isinstance(response, dict) or set(response) != {'status','body'} or type(response['status']) is not int:
            raise EvidenceError('Invalid evidence response.')
        if response['status'] != 200:
            return {'verified_active': False, 'reason': 'log_read_unavailable', 'http_status': response['status']}
        body = response['body']
        if not isinstance(body, dict) or set(body)-{'entries','nextPageToken'}:
            raise EvidenceError('Unexpected evidence response fields.')
        entries = body.get('entries', [])
        if not isinstance(entries, list) or len(entries)>100:
            raise EvidenceError('Invalid evidence entries.')
        for entry in entries:
            if not isinstance(entry,dict) or set(entry)!={'insertId'} or not _safe_identifier(entry['insertId'],512):
                raise EvidenceError('Invalid evidence identifier.')
        if entries:
            return {'verified_active': True, 'profile': profile, 'revision': revision, 'revision_created_at': created_at, 'event_id': entries[0]['insertId']}
        token = body.get('nextPageToken','')
        if token == '':
            return {'verified_active': False, 'reason': 'activation_not_observed'}
        if not _safe_identifier(token,4096) or token in seen:
            raise EvidenceError('Invalid evidence pagination.')
        seen.add(token)
        request = {**request, 'body': {**request['body'], 'pageToken':token}}
    raise EvidenceError('Evidence pagination exceeded its bound.')
