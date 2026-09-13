#!/usr/bin/env bash
set -euo pipefail
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
  build-essential git python3 pkg-config binutils \
  libgtk-3-dev libxtst-dev libxxf86vm-dev libgl-dev libegl-dev \
  libpango1.0-dev libasound2-dev libudev-dev libwayland-dev
