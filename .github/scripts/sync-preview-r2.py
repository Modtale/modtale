#!/usr/bin/env python3
"""Copy missing sanitized fixtures on the CI runner, keeping branch objects intact."""
import concurrent.futures
import json
import os
import subprocess
import tempfile
from urllib.parse import urlsplit


def read_secret(name):
    return subprocess.check_output([
        'gcloud', 'secrets', 'versions', 'access', 'latest',
        '--project', os.environ['PROJECT_ID'], '--secret', name,
    ], text=True).strip()


def credentials(prefix):
    env = dict(os.environ)
    env['AWS_ACCESS_KEY_ID'] = read_secret(os.environ[prefix + 'ACCESS_KEY_SECRET_NAME'])
    env['AWS_SECRET_ACCESS_KEY'] = read_secret(os.environ[prefix + 'SECRET_KEY_SECRET_NAME'])
    env['AWS_DEFAULT_REGION'] = 'auto'
    env['AWS_EC2_METADATA_DISABLED'] = 'true'
    endpoint = urlsplit(read_secret(os.environ[prefix + 'ENDPOINT_SECRET_NAME']))
    if endpoint.scheme != 'https' or not endpoint.hostname or not endpoint.hostname.endswith('.r2.cloudflarestorage.com'):
        raise ValueError('Expected an HTTPS R2 endpoint')
    return env, endpoint.scheme + '://' + endpoint.netloc


def aws(config, *args):
    env, endpoint = config
    return subprocess.check_output(['aws', '--endpoint-url', endpoint, *args], env=env, stderr=subprocess.PIPE)


def keys(config, bucket):
    result = json.loads(aws(config, 's3api', 'list-objects-v2', '--bucket', bucket, '--output', 'json'))
    return {item['Key'] for item in result.get('Contents', [])}


def missing_keys(source, target):
    return sorted(key for key in source - target if not key.startswith('.modtale-preview/'))


def main():
    source_bucket = os.environ['SEEDING_SOURCE_R2_BUCKET_NAME']
    target_bucket = os.environ['R2_BUCKET_NAME']
    if source_bucket == target_bucket or source_bucket == 'modtale-binaries' or not target_bucket.startswith('modtale-branch-'):
        raise ValueError('Fixture sync requires a sanitized source and an isolated branch target')
    source = credentials('SEEDING_SOURCE_R2_')
    target = credentials('R2_')
    missing = missing_keys(keys(source, source_bucket), keys(target, target_bucket))

    def copy(key):
        with tempfile.TemporaryDirectory(prefix='modtale-r2-') as directory:
            path = os.path.join(directory, 'object')
            aws(source, 's3', 'cp', 's3://' + source_bucket + '/' + key, path, '--only-show-errors')
            # Copy metadata explicitly; the temporary filename has no useful extension.
            metadata = json.loads(aws(source, 's3api', 'head-object', '--bucket', source_bucket, '--key', key))
            args = ['s3api', 'put-object', '--bucket', target_bucket, '--key', key, '--body', path,
                    '--content-type', metadata.get('ContentType', 'application/octet-stream'),
                    '--cache-control', metadata.get('CacheControl', 'public, max-age=31536000, immutable')]
            if metadata.get('ContentDisposition'):
                args += ['--content-disposition', metadata['ContentDisposition']]
            aws(target, *args)

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(copy, missing))
    print(f'Preview R2 fixtures ready: copied {len(missing)} missing objects; existing objects preserved.')


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as error:
        # Never include credential-bearing subprocess environments in failures.
        raise SystemExit(f'Preview fixture sync failed (exit {error.returncode}); deployment stopped.') from None
