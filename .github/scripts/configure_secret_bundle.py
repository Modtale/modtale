"""Build metadata-only Cloud Run arguments; never handles secret payloads."""
import argparse
import json
import re
import sys
from secret_bundle import BundleError, TARGETS, numeric_version

RUNTIME_ALIASES = (
    'MONGODB_URI,R2_ACCESS_KEY,R2_SECRET_KEY,R2_ENDPOINT,SMTP_HOST,SMTP_USERNAME,SMTP_PASSWORD,'
    'PRE_AUTH_SECRET,GITHUB_CLIENT_ID,GITHUB_CLIENT_SECRET,GOOGLE_CLIENT_ID,GOOGLE_CLIENT_SECRET,'
    'GITLAB_CLIENT_ID,GITLAB_CLIENT_SECRET,DISCORD_CLIENT_ID,DISCORD_CLIENT_SECRET,TWITTER_CLIENT_ID,'
    'TWITTER_CLIENT_SECRET,BLUESKY_CLIENT_ID,WEBHOOK_URL,HYTALEMODDING_KEY,WIKI_API_KEY,'
    'DISCORD_WEBHOOK_URL,ADMIN_DISCORD_WEBHOOK_URL,HYTALE_CLIENT_SECRET,APP_SEEDING_SOURCE_R2_ACCESS_KEY,'
    'APP_SEEDING_SOURCE_R2_SECRET_KEY,APP_SEEDING_SOURCE_R2_ENDPOINT,CLOUDFLARE_CACHE_PURGE_TOKEN'
)

def plan(enabled,profile,pins,preview_id=''):
    if enabled=='false':return []
    if enabled!='true':raise BundleError('Enable flag must be true or false')
    boundaries={'prod':['shared','production'],'dev':['shared'],'branch-preview':['branch-preview'],'pr-preview':['pr-preview']}
    if profile not in boundaries:raise BundleError('Unsupported runtime profile')
    if profile=='branch-preview' and not re.fullmatch(r'[a-z0-9](?:[a-z0-9-]{0,18}[a-z0-9])?',preview_id):
        raise BundleError('Invalid branch preview identifier')
    if profile=='branch-preview' and preview_id in ('main','develop','dev'):
        raise BundleError('Reserved branch identifier')
    if profile=='pr-preview' and not re.fullmatch(r'[1-9][0-9]*',preview_id):
        raise BundleError('Invalid PR preview identifier')
    if profile in ('prod','dev') and preview_id:raise BundleError('Unexpected preview identifier')
    if set(pins) != set(boundaries[profile]):raise BundleError('Pin boundaries do not match the runtime profile')
    removed=RUNTIME_ALIASES + (',WARDEN_API_KEY' if profile in ('branch-preview','pr-preview') else '')
    args=['--remove-secrets',removed,'--remove-env-vars','WARDEN_URL']
    for boundary in boundaries[profile]:
        version=numeric_version(pins[boundary])
        args+=['--update-secrets',f'/app/secrets/bundles/{boundary}.json={TARGETS[boundary][1]}:{version}']
    env=f'MODTALE_SECRET_BUNDLES_ENABLED=true,MODTALE_SECRET_BUNDLE_PROFILE={profile}'
    if preview_id:env+=f',MODTALE_SECRET_BUNDLE_PREVIEW_ID={preview_id}'
    if profile in ('prod','dev'):
        # Deliberate fifth-version exception: backend and scanner keep the same
        # existing latest reference so rotations cannot split their credentials.
        args+=['--update-secrets','WARDEN_API_KEY=WARDEN_API_KEY:latest']
    args+=['--update-env-vars',env]
    return args

def restore_legacy_development(source_versions):
    """Explicit config-only recovery; retain source versions and the old serving revision."""
    names = set(('MONGODB_URI R2_ACCESS_KEY R2_SECRET_KEY R2_ENDPOINT SMTP_HOST SMTP_USERNAME SMTP_PASSWORD '
                 'PRE_AUTH_SECRET GITHUB_CLIENT_ID GITHUB_CLIENT_SECRET GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET '
                 'GITLAB_CLIENT_ID GITLAB_CLIENT_SECRET DISCORD_CLIENT_ID DISCORD_CLIENT_SECRET TWITTER_CLIENT_ID '
                 'TWITTER_CLIENT_SECRET BLUESKY_CLIENT_ID WARDEN_API_KEY WARDEN_URL WIKI_API_KEY '
                 'DISCORD_WEBHOOK_URL ADMIN_DISCORD_WEBHOOK_URL').split())
    if set(source_versions) != names:
        raise BundleError('Development recovery requires the exact legacy source manifest')
    args=['--remove-secrets',','.join('/app/secrets/bundles/'+b+'.json' for b in TARGETS),
          '--remove-env-vars','MODTALE_SECRET_BUNDLE_PROFILE,MODTALE_SECRET_BUNDLE_PREVIEW_ID',
          '--update-env-vars','MODTALE_SECRET_BUNDLES_ENABLED=false']
    for name in sorted(names):
        version=numeric_version(source_versions[name])
        args+=['--update-secrets',f'{name}={name}:{"latest" if name=="WARDEN_API_KEY" else version}']
    return args

def main():
    p=argparse.ArgumentParser();p.add_argument('--enabled',choices=['true','false'],default='false');p.add_argument('--profile',required=True)
    p.add_argument('--shared-version');p.add_argument('--production-version');p.add_argument('--branch-preview-version');p.add_argument('--pr-preview-version');p.add_argument('--preview-id',default='')
    a=p.parse_args();pins={k:v for k,v in [('shared',a.shared_version),('production',a.production_version),('branch-preview',a.branch_preview_version),('pr-preview',a.pr_preview_version)] if v is not None}
    try:print(json.dumps(plan(a.enabled,a.profile,pins,a.preview_id)));return 0
    except BundleError:print('Bundle deployment configuration is invalid.',file=sys.stderr);return 1
if __name__=='__main__':sys.exit(main())
