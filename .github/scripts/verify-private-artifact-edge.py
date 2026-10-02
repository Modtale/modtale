#!/usr/bin/env python3
"""Read-only R2/CDN cutover check. Requires CLOUDFLARE_API_TOKEN and known sample keys."""

import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request


PREFIXES = ("files/", "modpack-overrides/", "modpacks/")


def cloudflare(account, bucket, route, token):
    path = "/client/v4/accounts/{}/r2/buckets/{}/domains/{}".format(
        urllib.parse.quote(account, safe=""), urllib.parse.quote(bucket, safe=""), route)
    request = urllib.request.Request("https://api.cloudflare.com" + path,
                                     headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(request, timeout=15) as response:
        result = json.load(response)
    if result.get("success") is not True or not isinstance(result.get("result"), dict):
        raise ValueError("Cloudflare did not return a valid domain configuration")
    return result["result"]


def domains_are_private(account, public_bucket, private_bucket, cdn_host, token):
    for bucket in (public_bucket, private_bucket):
        managed = cloudflare(account, bucket, "managed", token)
        if managed.get("enabled") is not False:
            raise ValueError("An R2-managed public endpoint is enabled or unverified")
    public_domains = cloudflare(account, public_bucket, "custom", token).get("domains")
    private_domains = cloudflare(account, private_bucket, "custom", token).get("domains")
    if not isinstance(public_domains, list) or not isinstance(private_domains, list):
        raise ValueError("Cloudflare custom-domain inventory is incomplete")
    if any(domain.get("enabled") is not False for domain in private_domains):
        raise ValueError("Private artifact bucket has a public custom domain")
    active = [domain.get("domain") for domain in public_domains if domain.get("enabled") is True]
    if active != [cdn_host]:
        raise ValueError("Public bucket has unexpected enabled custom domains")


def probe(cdn_host, key, method, headers=None):
    url = "https://" + cdn_host + "/" + urllib.parse.quote(key, safe="/-_.")
    request = urllib.request.Request(url, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def verify_samples(cdn_host, artifact_keys, media_key):
    if {next((prefix for prefix in PREFIXES if key.startswith(prefix)), None)
            for key in artifact_keys} != set(PREFIXES):
        raise ValueError("Provide one sample artifact key for each protected prefix")
    if any(media_key.startswith(prefix) for prefix in PREFIXES):
        raise ValueError("Media sample must not use an artifact prefix")
    for key in artifact_keys:
        if probe(cdn_host, key, "HEAD") != 403:
            raise ValueError("CDN did not deny an artifact HEAD request")
        if probe(cdn_host, key, "GET", {"Range": "bytes=0-0"}) != 403:
            raise ValueError("CDN did not deny an artifact byte-range request")
    if probe(cdn_host, media_key, "HEAD") != 200:
        raise ValueError("Public media did not remain available")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--account-id", required=True)
    parser.add_argument("--public-bucket", required=True)
    parser.add_argument("--private-bucket", required=True)
    parser.add_argument("--cdn-host", required=True)
    parser.add_argument("--artifact-key", action="append", required=True)
    parser.add_argument("--media-key", required=True)
    args = parser.parse_args()
    token = os.environ.get("CLOUDFLARE_API_TOKEN")
    if not token:
        parser.error("CLOUDFLARE_API_TOKEN is required")
    if args.public_bucket == args.private_bucket:
        parser.error("Buckets must differ")
    if args.cdn_host != "cdn.modtale.net":
        parser.error("The production CDN host must be checked explicitly")
    domains_are_private(args.account_id, args.public_bucket, args.private_bucket, args.cdn_host, token)
    verify_samples(args.cdn_host, args.artifact_key, args.media_key)
    print("Private artifact domain settings and CDN sample denials verified")


if __name__ == "__main__":
    main()
