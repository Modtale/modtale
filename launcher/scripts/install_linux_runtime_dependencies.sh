#!/usr/bin/env bash
set -euo pipefail
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
  libgtk-3-0t64 libopengl0 libegl1 libpango-1.0-0 libasound2t64 \
  libudev1 libwayland-client0 libwayland-egl1
