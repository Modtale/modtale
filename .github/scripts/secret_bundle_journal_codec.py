"""Lossless bounded encoding of fixed-scope journal metadata. No IO or credentials."""
import base64
import json
import re

MAX_BYTES = 65536
MAX_ETAG_BYTES = 1024
MAX_DEPTH = 64
MARKERS = {'$service', '$revision', '$short_revision', '$operation', '$etag'}


class CodecError(ValueError):
    pass


def fail():
    raise CodecError('Invalid or oversized scoped journal metadata.')


def scope(value):
    if not isinstance(value, str) or re.fullmatch(r'projects/(?:gen-lang-client-0244308719|modtale-pr-preview)/locations/us-central1/services/[a-z][a-z0-9-]{0,62}', value) is None:
        fail()
    return value, value.rsplit('/', 1)[-1], value.rsplit('/services/', 1)[0] + '/operations/'


def revision_suffix(value, service):
    if not isinstance(value, str) or len(value) > 63 or not value.startswith(service + '-') or re.fullmatch(r'[a-z0-9][a-z0-9-]*[a-z0-9]', value) is None:
        fail()
    return value


def operation_suffix(value):
    if not isinstance(value, str) or re.fullmatch(r'[A-Za-z0-9_-]{1,128}', value) is None:
        fail()
    return value


def _encode(value, full, service, operations, depth=0, key=None):
    if depth > MAX_DEPTH:
        fail()
    if isinstance(value, dict):
        if any(not isinstance(name, str) for name in value) or set(value) & MARKERS:
            fail()
        return {name: _encode(item, full, service, operations, depth + 1, name) for name, item in value.items()}
    if isinstance(value, list):
        return [_encode(item, full, service, operations, depth + 1) for item in value]
    if isinstance(value, str):
        if key == 'etag':
            raw = value.encode('utf-8')
            if len(raw) > MAX_ETAG_BYTES:
                fail()
            return {'$etag': base64.b64encode(raw).decode('ascii')}
        if value == full:
            return {'$service': 1}
        if value.startswith(full + '/revisions/'):
            return {'$revision': revision_suffix(value[len(full + '/revisions/'):], service)[len(service) + 1:]}
        if value.startswith(service + '-') and len(value) <= 63 and re.fullmatch(r'[a-z0-9][a-z0-9-]*[a-z0-9]', value):
            return {'$short_revision': value[len(service) + 1:]}
        if value.startswith(operations):
            return {'$operation': operation_suffix(value[len(operations):])}
    return value


def _decode(value, full, service, operations, depth=0):
    if depth > MAX_DEPTH:
        fail()
    if isinstance(value, dict):
        reserved = set(value) & MARKERS
        if reserved:
            if len(value) != 1 or len(reserved) != 1:
                fail()
            kind = next(iter(reserved))
            item = value[kind]
            if kind == '$service':
                if type(item) is not int or item != 1:
                    fail()
                return full
            if kind in ('$revision', '$short_revision'):
                if not isinstance(item, str):
                    fail()
                restored = revision_suffix(service + '-' + item, service)
                return full + '/revisions/' + restored if kind == '$revision' else restored
            if kind == '$operation':
                return operations + operation_suffix(item)
            if not isinstance(item, str):
                fail()
            raw = base64.b64decode(item, validate=True)
            if len(raw) > MAX_ETAG_BYTES:
                fail()
            return raw.decode('utf-8')
        return {key: _decode(item, full, service, operations, depth + 1) for key, item in value.items()}
    if isinstance(value, list):
        return [_decode(item, full, service, operations, depth + 1) for item in value]
    return value


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            fail()
        result[key] = value
    return result


def scoped_record_bytes(record, service_full):
    try:
        full, service, operations = scope(service_full)
        expanded = json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode('utf-8')
        if len(expanded) > MAX_BYTES:
            fail()
        raw = json.dumps(_encode(record, full, service, operations), ensure_ascii=False,
                         sort_keys=True, separators=(',', ':'), allow_nan=False).encode('utf-8')
        if len(raw) > MAX_BYTES:
            fail()
        return raw
    except (ValueError, TypeError, UnicodeError, RecursionError):
        raise CodecError('Invalid or oversized scoped journal metadata.') from None


def parse_scoped_record(raw, service_full):
    try:
        if not isinstance(raw, bytes) or len(raw) > MAX_BYTES:
            fail()
        full, service, operations = scope(service_full)
        value = json.loads(raw.decode('utf-8'), object_pairs_hook=_pairs,
                           parse_constant=lambda value: fail())
        result = _decode(value, full, service, operations)
        # Fixed-scope restoration is also bounded; aliasing cannot amplify input
        # into an unbounded in-memory checkpoint.
        expanded = json.dumps(result, ensure_ascii=False, sort_keys=True,
                              separators=(',', ':'), allow_nan=False).encode('utf-8')
        if len(expanded) > MAX_BYTES:
            fail()
        return result
    except (ValueError, TypeError, UnicodeError, RecursionError):
        raise CodecError('Invalid or oversized scoped journal metadata.') from None
