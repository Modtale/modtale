"""Strict, offline bundle validation. Never prints credential values."""
import argparse
import json
import re
import sys

MAX_BYTES = 65536
SHARED = frozenset('ADMIN_DISCORD_WEBHOOK_URL BLUESKY_CLIENT_ID DISCORD_CLIENT_ID DISCORD_CLIENT_SECRET DISCORD_WEBHOOK_URL GITHUB_CLIENT_ID GITHUB_CLIENT_SECRET GITLAB_CLIENT_ID GITLAB_CLIENT_SECRET GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET GOOGLE_CLIENT_SECREY HYTALEMODDING_KEY MODTALE_PUBLIC_CACHE_PURGE_TOKEN MONGODB_URI MONGO_URI PRE_AUTH_SECRET R2_ACCESS_KEY R2_ENDPOINT R2_SECRET_KEY SMTP_HOST SMTP_PASSWORD SMTP_USERNAME TWITTER_CLIENT_ID TWITTER_CLIENT_SECRET WARDEN_API_KEY WARDEN_URL WEBHOOK_URL WIKI_API_KEY modtale-github-secret modtale-mongo-uri'.split())
BRANCH_BASE = frozenset('BRANCH_PREVIEW_MONGODB_URI R2_SOURCE_READ_ACCESS_KEY R2_SOURCE_READ_ENDPOINT R2_SOURCE_READ_SECRET_KEY R2_TEMPLATE_READ_ACCESS_KEY R2_TEMPLATE_READ_ENDPOINT R2_TEMPLATE_READ_SECRET_KEY'.split())
PR_BASE = frozenset('PREVIEW_MONGODB_URI PREVIEW_SOURCE_R2_ACCESS_KEY PREVIEW_SOURCE_R2_ENDPOINT PREVIEW_SOURCE_R2_SECRET_KEY'.split())
BOUNDARIES = {'shared': SHARED, 'branch-preview': BRANCH_BASE, 'pr-preview': PR_BASE}
TARGETS = {
    'shared': ('gen-lang-client-0244308719', 'MODTALE_CONFIG_SHARED'),
    'branch-preview': ('gen-lang-client-0244308719', 'MODTALE_CONFIG_BRANCH_PREVIEW'),
    'pr-preview': ('modtale-pr-preview', 'MODTALE_CONFIG_PR_PREVIEW'),
}

class BundleError(ValueError):
    pass

def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise BundleError('Duplicate JSON key')
        result[key] = value
    return result

def _allowed(boundary, key):
    if key in BOUNDARIES[boundary]:
        return True
    if boundary == 'branch-preview':
        return re.fullmatch(r'branch-preview-[a-z0-9](?:[a-z0-9-]{0,18}[a-z0-9])?-r2-(access-key|secret-key|endpoint|token-id)', key) is not None
    if boundary == 'pr-preview':
        return re.fullmatch(r'pr-preview-[1-9][0-9]*-r2-(access-key|secret-key|endpoint|token-id)', key) is not None
    return False

def parse(raw, expected_boundary):
    if expected_boundary not in BOUNDARIES:
        raise BundleError('Unknown boundary')
    if len(raw) > MAX_BYTES:
        raise BundleError('Bundle exceeds 64 KiB')
    try:
        data = json.loads(raw.decode('utf-8'), object_pairs_hook=_pairs)
    except (ValueError, UnicodeError, RecursionError):
        raise BundleError('Invalid bundle JSON') from None
    if not isinstance(data, dict) or set(data) != {'schemaVersion', 'boundary', 'secrets'}:
        raise BundleError('Invalid bundle envelope')
    if type(data['schemaVersion']) is not int or data['schemaVersion'] != 1 or data['boundary'] != expected_boundary:
        raise BundleError('Bundle version or boundary mismatch')
    secrets = data['secrets']
    if not isinstance(secrets, dict) or not secrets:
        raise BundleError('Bundle must contain secret entries')
    for key, value in secrets.items():
        if not _allowed(expected_boundary, key):
            raise BundleError('Secret outside approved boundary')
        if not isinstance(value, str) or not value or '\x00' in value:
            raise BundleError('Secret must be a nonempty NUL-free string')
        try:
            value.encode('utf-8', errors='strict')
        except UnicodeError:
            raise BundleError('Secret must be valid UTF-8 text') from None
    return data

def encode(data):
    raw = json.dumps(data, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    parse(raw, data.get('boundary'))
    return raw

def patch_snapshot(raw, boundary, updates, removals=()):
    """Pure function only. Caller must hold the shared remote mutation lock."""
    data = parse(raw, boundary)
    if set(updates).intersection(removals):
        raise BundleError('Conflicting update and removal')
    data['secrets'].update(updates)
    for key in removals:
        if not _allowed(boundary, key):
            raise BundleError('Removal outside approved boundary')
        data['secrets'].pop(key, None)
    return encode(data)

def numeric_version(value):
    if not isinstance(value, str) or not re.fullmatch(r'[1-9][0-9]*', value):
        raise BundleError('An explicit positive numeric version is required')
    return value

def main():
    parser = argparse.ArgumentParser(description='Validate a local bundle without displaying its values')
    parser.add_argument('--boundary', choices=BOUNDARIES, required=True)
    parser.add_argument('--file', required=True)
    args = parser.parse_args()
    try:
        with open(args.file, 'rb') as source:
            raw = source.read(MAX_BYTES + 1)
        data = parse(raw, args.boundary)
        print(json.dumps({'valid': True, 'boundary': args.boundary, 'entries': len(data['secrets']), 'bytes': len(raw)}))
    except (BundleError, OSError):
        print('Bundle validation failed; no values were displayed.', file=sys.stderr)
        return 1
    return 0

if __name__ == '__main__':
    sys.exit(main())
