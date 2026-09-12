#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys


def branch_slug(name, sha=""):
    return re.sub('-+', '-', re.sub('[^a-zA-Z0-9-]', '-', name).lower())[:20].strip('-') or sha[:7]


if __name__ == '__main__':
    target = sys.argv[1]
    branches = json.loads(subprocess.check_output([
        'gh', 'api', f'repos/{os.environ["GITHUB_REPOSITORY"]}/branches', '--paginate', '--slurp'
    ], text=True))
    if target in {'main', 'develop', 'dev', ''} or any(branch_slug(branch['name'], branch.get('commit', {}).get('sha', '')) == target for page in branches for branch in page):
        raise SystemExit('Refusing cleanup: a live or protected branch owns this preview name.')
    print('Verified that no live branch owns this preview.')
