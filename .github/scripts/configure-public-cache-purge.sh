#!/usr/bin/env bash
# Source after constructing the backend deployment ARGS array. Enabling is an
# explicit per-environment decision after its dedicated secret is populated.
if [[ "${ENV_TYPE:-}" == "prod" || "${ENV_TYPE:-}" == "dev" ]] && [[ "${PUBLIC_CACHE_PURGE_ENABLED:-false}" == "true" ]]; then
  ARGS+=("--update-env-vars" "PUBLIC_CACHE_PURGE_ENABLED=true,CLOUDFLARE_ZONE_ID=ff20a05c676ca023b19d20e177054945")
  if [[ "${MODTALE_SECRET_BUNDLES_ENABLED:-false}" != "true" ]]; then
    ARGS+=("--update-secrets" "CLOUDFLARE_CACHE_PURGE_TOKEN=MODTALE_PUBLIC_CACHE_PURGE_TOKEN:latest")
  fi
else
  ARGS+=("--update-env-vars" "PUBLIC_CACHE_PURGE_ENABLED=false")
fi
