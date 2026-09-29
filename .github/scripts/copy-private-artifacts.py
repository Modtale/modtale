#!/usr/bin/env python3
"""Copy version artifacts out of a public R2 bucket, verifying both byte streams.

This never deletes source objects or changes CDN configuration. AWS CLI credentials
and endpoint come from the operator's environment; no credentials are logged.
"""

import argparse
import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path


PREFIXES = ("files/", "modpack-overrides/", "modpacks/")
MAX_OBJECT_BYTES = 1024 * 1024 * 1024


def aws(endpoint, *args):
    command = ["aws", "--endpoint-url", endpoint, "s3api", *args, "--output", "json", "--no-cli-pager"]
    result = subprocess.run(command, check=True, text=True, capture_output=True)
    return json.loads(result.stdout) if result.stdout.strip() else {}


def inventory(endpoint, bucket):
    seen = set()
    for prefix in PREFIXES:
        continuation = None
        while True:
            args = ["list-objects-v2", "--bucket", bucket, "--prefix", prefix,
                    "--max-keys", "1000", "--no-paginate"]
            if continuation:
                args.extend(("--continuation-token", continuation))
            page = aws(endpoint, *args)
            for item in page.get("Contents", []):
                key = item["Key"]
                if not key.startswith(prefix) or key in seen:
                    raise ValueError("Unexpected or duplicate artifact key in source inventory")
                seen.add(key)
                size = item["Size"]
                if not isinstance(size, int) or size < 0 or size > MAX_OBJECT_BYTES:
                    raise ValueError("Artifact exceeds migration byte limit")
                yield key, size
            if not page.get("IsTruncated"):
                break
            next_token = page.get("NextContinuationToken")
            if not next_token or next_token == continuation:
                raise ValueError("Artifact inventory pagination did not advance")
            continuation = next_token


def download(endpoint, bucket, key, path, expected_size):
    metadata = aws(endpoint, "get-object", "--bucket", bucket, "--key", key, str(path))
    actual_size = path.stat().st_size
    if actual_size != expected_size or metadata.get("ContentLength") != expected_size:
        raise ValueError("Artifact size changed during migration")
    with path.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    return metadata, digest


def destination_exists(endpoint, bucket, key):
    try:
        aws(endpoint, "head-object", "--bucket", bucket, "--key", key)
        return True
    except subprocess.CalledProcessError as error:
        # AWS CLI emits its diagnostic on stderr. Only an absent object may be copied.
        if "404" in error.stderr or "Not Found" in error.stderr or "NoSuchKey" in error.stderr:
            return False
        raise


def copy_and_verify(endpoint, source, destination, key, size, workdir):
    source_file = workdir / "source"
    destination_file = workdir / "destination"
    source_metadata, source_digest = download(endpoint, source, key, source_file, size)
    already_present = destination_exists(endpoint, destination, key)
    if not already_present:
        args = ["put-object", "--bucket", destination, "--key", key, "--body", str(source_file)]
        args.extend(("--cache-control", "private, no-store"))
        for field, flag in (("ContentType", "--content-type"), ("ContentDisposition", "--content-disposition")):
            if source_metadata.get(field):
                args.extend((flag, source_metadata[field]))
        aws(endpoint, *args)
    _, destination_digest = download(endpoint, destination, key, destination_file, size)
    if destination_digest != source_digest:
        raise ValueError("Private artifact bytes do not match source")
    source_file.unlink()
    destination_file.unlink()
    return already_present, source_digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--source-bucket", required=True)
    parser.add_argument("--destination-bucket", required=True)
    parser.add_argument("--copy", action="store_true", help="Copy and verify every object; default only inventories")
    parser.add_argument("--report", type=Path, required=True, help="Write an atomic summary without artifact keys")
    args = parser.parse_args()
    if args.source_bucket == args.destination_bucket:
        parser.error("Source and destination buckets must differ")
    if not args.endpoint.startswith("https://"):
        parser.error("An HTTPS R2 endpoint is required")
    count = 0
    total_bytes = 0
    copied = 0
    already_equal = 0
    manifest_digest = hashlib.sha256()
    with tempfile.TemporaryDirectory(prefix="modtale-artifact-copy-") as directory:
        for key, size in inventory(args.endpoint, args.source_bucket):
            count += 1
            total_bytes += size
            if args.copy:
                existed, digest = copy_and_verify(args.endpoint, args.source_bucket,
                                                  args.destination_bucket, key, size, Path(directory))
                already_equal += int(existed)
                copied += int(not existed)
                manifest_digest.update(key.encode("utf-8") + b"\0" + digest.encode("ascii") + b"\n")
    report = {"sourceBucket": args.source_bucket, "destinationBucket": args.destination_bucket,
              "objectCount": count, "totalBytes": total_bytes, "copied": copied,
              "alreadyEqual": already_equal, "copyVerified": args.copy,
              "keyAndByteManifestSha256": manifest_digest.hexdigest() if args.copy else None}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", dir=args.report.parent, delete=False) as file:
        json.dump(report, file, sort_keys=True, indent=2)
        file.write("\n")
        temporary = file.name
    os.replace(temporary, args.report)
    print(json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    main()
