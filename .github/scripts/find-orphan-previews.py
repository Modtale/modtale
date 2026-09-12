#!/usr/bin/env python3
import json
import os
import re
import subprocess
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path

spec = spec_from_file_location('orphan_guard', Path(__file__).with_name('assert-preview-orphan.py'))
guard = module_from_spec(spec)
spec.loader.exec_module(guard)
project = os.environ['PROJECT_ID']
branch_pages = json.loads(subprocess.check_output([
    'gh', 'api', f'repos/{os.environ["GITHUB_REPOSITORY"]}/branches', '--paginate', '--slurp'
], text=True))
live = {guard.branch_slug(branch['name']) for page in branch_pages for branch in page}
live.update({'main', 'develop', 'dev', ''})
services = json.loads(subprocess.check_output([
    'gcloud', 'run', 'services', 'list', '--project', project, '--region', os.environ['REGION'], '--format=json'
], text=True))
secrets = json.loads(subprocess.check_output([
    'gcloud', 'secrets', 'list', '--project', project, '--format=json'
], text=True))
candidates = set()
for service in services:
    metadata = service['metadata']
    slug = metadata.get('labels', {}).get('branch')
    if slug and metadata['name'] in {'modtale-backend-' + slug, 'modtale-frontend-' + slug}:
        candidates.add(slug)
for secret in secrets:
    match = re.fullmatch(r'branch-preview-([a-z0-9-]{1,20})-r2-token-id', secret['name'].rsplit('/', 1)[-1])
    if match:
        candidates.add(match[1])
print(json.dumps(sorted(candidates - live)))
