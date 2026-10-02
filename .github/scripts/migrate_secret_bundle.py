"""User-dispatched, fixed-destination migration. Do not invoke automatically."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from secret_bundle import BundleError, TARGETS, encode, numeric_version

BASE = Path(__file__).resolve().parent
EXPECTED_MEMBERS = {
    'shared': ['serviceAccount:modtale-dev-runtime@gen-lang-client-0244308719.iam.gserviceaccount.com', 'serviceAccount:modtale-prod-runtime@gen-lang-client-0244308719.iam.gserviceaccount.com'],
    'production': ['serviceAccount:modtale-prod-runtime@gen-lang-client-0244308719.iam.gserviceaccount.com'],
    'branch-preview': ['serviceAccount:modtale-branch-preview-runtime@gen-lang-client-0244308719.iam.gserviceaccount.com'],
    'pr-preview': ['serviceAccount:modtale-pr-preview-runtime@modtale-pr-preview.iam.gserviceaccount.com'],
}

class MigrationError(RuntimeError):
    pass

def gcloud(*args):
    result = subprocess.run(['gcloud', *args, '--quiet'], capture_output=True, timeout=120)
    if result.returncode:
        raise MigrationError('Cloud operation failed; payload output suppressed')
    try:
        return json.loads(result.stdout) if result.stdout.strip() else {}
    except (ValueError, UnicodeError):
        raise MigrationError('Invalid cloud response; payload output suppressed') from None

def policy_ok(data, boundary):
    expected = [{'role': 'roles/secretmanager.secretAccessor', 'members': EXPECTED_MEMBERS[boundary]}]
    actual=[]
    for entry in data.get('bindings',[]):
        if entry.get('condition'):
            return False
        actual.append({'role':entry.get('role'), 'members':sorted(entry.get('members',[]))})
    return actual == expected

def migrate(boundary, api=gcloud):
    if boundary not in TARGETS:
        raise MigrationError('Unsupported boundary')
    manifest=json.loads((BASE/'secret-bundle-source-manifest.json').read_text())[boundary]
    project,destination=TARGETS[boundary]
    if (manifest['project'],manifest['destination']) != (project,destination):
        raise MigrationError('Manifest destination mismatch')
    sources=manifest['sourceVersions']
    if not sources:
        raise MigrationError('Empty source manifest')
    encode({'schemaVersion':1,'boundary':boundary,'secrets':{name:'metadata-validation' for name in sources}})
    # All metadata checks happen before the first payload retrieval.
    policy=api('secrets','get-iam-policy',destination,'--project',project,'--format=json')
    if not policy_ok(policy,boundary):
        raise MigrationError('Destination IAM does not match the approved boundary')
    versions=api('secrets','versions','list',destination,'--project',project,'--format=json')
    if any(v.get('state') != 'DESTROYED' for v in versions):
        raise MigrationError('Destination already populated; inspect metadata before any retry')
    for name,version in sources.items():
        numeric_version(version)
        policy=api('secrets','get-iam-policy',name,'--project',project,'--format=json')
        if not policy_ok(policy,boundary):
            raise MigrationError('Source IAM changed; regenerate and review the manifest')
        metadata=api('secrets','versions','describe',version,'--secret',name,'--project',project,'--format=json')
        if metadata.get('state')!='ENABLED':
            raise MigrationError('Pinned source version is not enabled')
        current=api('secrets','versions','list',name,'--project',project,'--format=json')
        enabled=[v['name'].rsplit('/',1)[-1] for v in current if v.get('state')=='ENABLED']
        if enabled != [version]:
            raise MigrationError('Source versions changed; regenerate and review the manifest')
    # Private ephemeral files only; no values are printed or returned.
    with tempfile.TemporaryDirectory(prefix='modtale-bundle-') as directory:
        os.chmod(directory,0o700)
        payloads={}
        for name,version in sources.items():
            file=Path(directory)/'source'
            file.touch(mode=0o600)
            api('secrets','versions','access',version,'--secret',name,'--project',project,'--out-file',str(file),'--format=none')
            raw=file.read_bytes()
            file.unlink()
            if len(raw)>65536:
                raise MigrationError('Source payload exceeds supported size')
            payloads[name]=raw.decode('utf-8')
        for name,version in sources.items():
            current=api('secrets','versions','list',name,'--project',project,'--format=json')
            enabled=[v['name'].rsplit('/',1)[-1] for v in current if v.get('state')=='ENABLED']
            if enabled != [version] or not policy_ok(api('secrets','get-iam-policy',name,'--project',project,'--format=json'),boundary):
                raise MigrationError('Source metadata changed during preparation')
        if not policy_ok(api('secrets','get-iam-policy',destination,'--project',project,'--format=json'),boundary):
            raise MigrationError('Destination IAM changed during preparation')
        if any(v.get('state') != 'DESTROYED' for v in api('secrets','versions','list',destination,'--project',project,'--format=json')):
            raise MigrationError('Destination changed during preparation')
        raw=encode({'schemaVersion':1,'boundary':boundary,'secrets':payloads})
        file=Path(directory)/'bundle'
        file.touch(mode=0o600)
        file.write_bytes(raw)
        published=api('secrets','versions','add',destination,'--project',project,'--data-file',str(file),'--format=json')
        version=numeric_version(published.get('name','').rsplit('/',1)[-1])
        return {'boundary':boundary,'destination':destination,'version':version,'entries':len(payloads)}

def main():
    if (os.environ.get('GITHUB_EVENT_NAME')!='workflow_dispatch' or os.environ.get('GITHUB_REPOSITORY')!='Modtale/modtale'
        or os.environ.get('GITHUB_REF')!='refs/heads/main' or os.environ.get('BUNDLE_MIGRATION_CONFIRM')!='PUBLISH_BUNDLE'
        or os.environ.get('GITHUB_ACTOR')!='Villagers654' or os.environ.get('GITHUB_TRIGGERING_ACTOR')!='Villagers654'):
        print('This migration requires the reviewed manual main-branch workflow.',file=sys.stderr)
        return 1
    boundary=os.environ.get('BUNDLE_BOUNDARY','')
    if boundary in ('branch-preview','pr-preview') and os.environ.get('BUNDLE_PREVIEW_WRITERS_READY')!='true':
        print('Preview migration is blocked until every legacy writer is upgraded and quiesced.',file=sys.stderr)
        return 1
    try:
        result=migrate(boundary)
        print(json.dumps(result))
        return 0
    except Exception:
        print('Migration stopped. No credential values were displayed. Inspect metadata before retrying.',file=sys.stderr)
        return 1

if __name__=='__main__':
    sys.exit(main())
