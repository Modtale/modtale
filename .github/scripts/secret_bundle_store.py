"""Fixed-boundary bundle store for explicitly opted-in, serialized CI jobs.

No command-line entrypoint: callers receive values in memory and must not log them.
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
from secret_bundle import BundleError, TARGETS, encode, parse, numeric_version, patch_snapshot

class StoreError(RuntimeError):
    pass

class GcloudBackend:
    def __init__(self,boundary):
        if boundary not in TARGETS:raise StoreError('Invalid boundary')
        self.project,self.secret=TARGETS[boundary]
    def _call(self,*args):
        p=subprocess.run(['gcloud','secrets',*args,'--project',self.project,'--quiet'],capture_output=True,timeout=120)
        if p.returncode:raise StoreError('Secret operation failed; details suppressed')
        return p.stdout
    def latest_enabled(self):
        data=json.loads(self._call('versions','list',self.secret,'--format=json'))
        enabled=[numeric_version(v['name'].rsplit('/',1)[-1]) for v in data if v.get('state')=='ENABLED']
        if not enabled:raise StoreError('No enabled bundle version')
        return max(enabled,key=int)
    def read(self,version):
        numeric_version(version)
        with tempfile.TemporaryDirectory(prefix='modtale-bundle-read-') as d:
            os.chmod(d,0o700);path=Path(d)/'payload';path.touch(mode=0o600)
            self._call('versions','access',version,'--secret',self.secret,'--out-file',str(path),'--format=none')
            with path.open('rb') as f:return f.read(65537)
    def publish(self,raw):
        with tempfile.TemporaryDirectory(prefix='modtale-bundle-write-') as d:
            os.chmod(d,0o700);path=Path(d)/'payload';path.touch(mode=0o600);path.write_bytes(raw)
            result=json.loads(self._call('versions','add',self.secret,'--data-file',str(path),'--format=json'))
            return numeric_version(result['name'].rsplit('/',1)[-1])

class BundleStore:
    def __init__(self,boundary,backend):
        if boundary not in TARGETS:raise StoreError('Invalid boundary')
        self.boundary=boundary;self.backend=backend
    def read(self,version):
        return parse(self.backend.read(numeric_version(version)),self.boundary)
    def update(self,updates,removals=(),*,lock_held=False):
        if not lock_held:raise StoreError('Serialized mutation context is required')
        old_version=numeric_version(self.backend.latest_enabled())
        old_raw=self.backend.read(old_version)
        old=parse(old_raw,self.boundary)
        new_raw=patch_snapshot(old_raw,self.boundary,updates,removals)
        new=parse(new_raw,self.boundary)
        if old==new:return {'version':old_version,'changed':False}
        # Detect out-of-band changes before publish. This is NOT an atomic CAS:
        # the caller's repository-wide mutex and maintenance policy are mandatory.
        if self.backend.latest_enabled()!=old_version:raise StoreError('Bundle changed outside the mutation lock')
        version=numeric_version(self.backend.publish(new_raw))
        return {'version':version,'previousVersion':old_version,'changed':True}

def preview_updates(boundary,preview_id,credentials):
    import re
    if boundary=='branch-preview':
        valid=re.fullmatch(r'[a-z0-9](?:[a-z0-9-]{0,18}[a-z0-9])?',preview_id)
    elif boundary=='pr-preview':valid=re.fullmatch(r'[1-9][0-9]*',preview_id)
    else:valid=False
    if not valid or (boundary=='branch-preview' and preview_id in ('main','develop','dev')):raise StoreError('Invalid preview identity')
    suffixes={'access-key','secret-key','endpoint','token-id'}
    if set(credentials)!=suffixes:raise StoreError('A complete credential set is required')
    return {f'{boundary}-{preview_id}-r2-{suffix}':value for suffix,value in credentials.items()}

def removable_versions(enabled_versions,referenced_versions,rollback_versions):
    """Metadata-only proposal. Never destroys, disables or revokes anything."""
    all_versions={numeric_version(v) for v in enabled_versions}
    retained={numeric_version(v) for v in referenced_versions}|{numeric_version(v) for v in rollback_versions}
    if not retained.issubset(all_versions):raise StoreError('Reference inventory is incomplete')
    return sorted(all_versions-retained,key=int)
